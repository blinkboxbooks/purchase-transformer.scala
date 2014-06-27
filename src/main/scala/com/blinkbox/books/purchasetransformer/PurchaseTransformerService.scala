package com.blinkbox.books.purchasetransformer

import akka.actor.{ ActorSystem, ActorRef, Props }
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.blinkbox.books.config.Configuration
import com.blinkbox.books.config.RichConfig
import com.blinkboxbooks.hermes.rabbitmq._
import com.blinkboxbooks.hermes.rabbitmq.RabbitMqConsumer.QueueConfiguration
import com.blinkbox.books.messaging._
import com.rabbitmq.client.Connection
import com.typesafe.scalalogging.slf4j.Logging
import java.util.concurrent.TimeUnit
import spray.can.Http
import scala.concurrent.duration._
import scala.concurrent.Future

/**
 * Entry point for the purchase-transformer service.
 */
object PurchaseTransformerService extends App with Configuration with Logging {

  val Originator = "purchase-transformer"

  val serviceConf = config.getConfig("service.purchaseTransformer")
  logger.info(s"Starting purchase-transformer service with config: $serviceConf")

  // Use separate connections for consumers and publishers.
  def newConnection() = RabbitMq.reliableConnection(RabbitMqConfig(config))
  val publisherConnection = newConnection()
  val consumerConnection = newConnection()

  // Initialise the actor system.
  implicit val system = ActorSystem("reporting-service")
  implicit val ec = system.dispatcher

  val ClientRequestTimeout = Timeout(serviceConf.getDuration("clientRequestInterval", TimeUnit.SECONDS).seconds)
  
  // Could use the Java Book client library instead?
  val httpActor = IO(Http)
  val bookDao = new HttpBookDao(httpActor, serviceConf.getHttpUrl("bookService.url").toString)(ClientRequestTimeout, ec)

  // TODO: Add helpers for getting scala.concurrent.Durations to common-config?
  val OutgoingMessageTimeout = serviceConf.getDuration("messagePublishingTimeout", TimeUnit.SECONDS).seconds
  val ClientRequestRetryInterval = serviceConf.getDuration("clientRequestInterval", TimeUnit.SECONDS).seconds

  // TODO: Should the routing key be made optional still, seeing as some services don't use it yet?
  // I guess it doesn't matter if we publish messages on a topic; old-school consumers can just
  // subscribe a topic of "*"?

  def publisher(exchange: String, routingKey: String) =
    system.actorOf(Props(new RabbitMqConfirmedPublisher(publisherConnection.createChannel(), exchange, routingKey, OutgoingMessageTimeout)))

  val emailListenerConfig = serviceConf.getConfig("emailListener.input")
  val clubcardListenerConfig = serviceConf.getConfig("clubcardListener.input")

  val emailMessageSender = new PublishingMessageSender(publisher(emailListenerConfig.getString("exchangeName"), "TODO"))
  val emailMsgErrorHandler = new PublishingErrorHandler(publisher("", serviceConf.getString("emailListener.error")))

  val routingId = emailListenerConfig.getString("emailRoutingInstance")
  val templateName = emailListenerConfig.getString("templateName")
  val retryInterval = serviceConf.getDuration("messagePublishingTimeout", TimeUnit.SECONDS).seconds

  // Create actors for email messages.
  val emailMessageHandler = system.actorOf(Props(
    new EmailMessageHandler(bookDao, emailMessageSender, emailMsgErrorHandler, routingId, templateName, retryInterval)))

  system.actorOf(Props(new RabbitMqConsumer(consumerConnection.createChannel,
    QueueConfiguration(emailListenerConfig), "email-msg-consumer", emailMessageHandler)))
    .tell(RabbitMqConsumer.Init, null)

  // Create actors that handle clubcard point messages.
  val clubcardMessageHandler = system.actorOf(Props(new ClubcardMessageHandler(
    new PublishingMessageSender(publisher(clubcardListenerConfig.getString("exchangeName"), "TODO")),
    new PublishingErrorHandler(publisher("", serviceConf.getString("clubcardListener.error"))), retryInterval)))

  system.actorOf(Props(new RabbitMqConsumer(consumerConnection.createChannel,
    QueueConfiguration(clubcardListenerConfig), "clubcard-msg-consumer", clubcardMessageHandler)))
    .tell(RabbitMqConsumer.Init, null)

  logger.info("Started")

  // For now: wrap actor in the MessageSender interface.
  // TODO: Consider just passing the actors into the message handlers instead.
  // Or, move these wrapper classes into the common RabbitMq library.
  class PublishingMessageSender(publisherActor: ActorRef) extends EventPublisher {
    implicit val timeout = Timeout(OutgoingMessageTimeout)
    override def publish(event: Event): Future[Unit] = (publisherActor ? event).map(result => ())
  }

  // Ditto for the ErrorHandlers.
  class PublishingErrorHandler(publisherActor: ActorRef) extends ErrorHandler {
    implicit val timeout = Timeout(OutgoingMessageTimeout)
    override def handleError(event: Event, e: Throwable): Future[Unit] = {
      logger.error(s"Unrecoverable error in processing event: $event", e)
      (publisherActor ? event).map(result => ())
    }
  }

}
