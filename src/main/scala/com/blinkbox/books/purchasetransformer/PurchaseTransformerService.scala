package com.blinkbox.books.purchasetransformer

import akka.actor.{ ActorSystem, ActorRef, Props }
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.blinkbox.books.config.{ Configuration, RichConfig }
import com.blinkboxbooks.hermes.rabbitmq._
import com.blinkboxbooks.hermes.rabbitmq.RabbitMqConsumer.QueueConfiguration
import com.blinkboxbooks.hermes.rabbitmq.RabbitMqConfirmedPublisher.PublisherConfiguration
import com.blinkbox.books.messaging._
import com.rabbitmq.client.Connection
import com.typesafe.config.Config
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

  private def publisher(config: Config) =
    system.actorOf(Props(new RabbitMqConfirmedPublisher(publisherConnection.createChannel(), PublisherConfiguration(config))))

  // Initialise the actor system.
  implicit val system = ActorSystem("reporting-service")
  implicit val ec = system.dispatcher

  // Could use the Java Book client library instead?
  val httpActor = IO(Http)
  val ClientRequestTimeout = Timeout(serviceConf.getDuration("clientRequestInterval", TimeUnit.SECONDS).seconds)
  val bookDao = new HttpBookDao(httpActor, serviceConf.getHttpUrl("bookService.url").toString)(ClientRequestTimeout, ec)

  // TODO: Add helpers for getting scala.concurrent.Durations to common-config?
  val ClientRequestRetryInterval = serviceConf.getDuration("clientRequestInterval", TimeUnit.SECONDS).seconds

  val emailMessageSender = new PublishingMessageSender(publisher(serviceConf.getConfig("emailListener.output")))
  val emailMsgErrorHandler = new PublishingErrorHandler(publisher(serviceConf.getConfig("emailListener.error")))

  val routingId = serviceConf.getString("emailListener.routingInstance")
  val templateName = serviceConf.getString("emailListener.templateName")
  val retryInterval = serviceConf.getDuration("clientRequestTimeout", TimeUnit.SECONDS).seconds

  // Create actors for email messages.
  val emailMessageHandler = system.actorOf(Props(
    new EmailMessageHandler(bookDao, emailMessageSender, emailMsgErrorHandler, routingId, templateName, retryInterval)))

  system.actorOf(Props(new RabbitMqConsumer(consumerConnection.createChannel,
    QueueConfiguration(serviceConf.getConfig("emailListener.input")), "email-msg-consumer", emailMessageHandler)))
    .tell(RabbitMqConsumer.Init, null)

  // Create actors that handle Cubcard point messages.
  val clubcardMessageHandler = system.actorOf(Props(new ClubcardMessageHandler(
    new PublishingMessageSender(publisher(serviceConf.getConfig("clubcardListener.output"))),
    new PublishingErrorHandler(publisher(serviceConf.getConfig("clubcardListener.error"))), retryInterval)))

  system.actorOf(Props(new RabbitMqConsumer(consumerConnection.createChannel,
    QueueConfiguration(serviceConf.getConfig("clubcardListener.input")), "clubcard-msg-consumer", clubcardMessageHandler)))
    .tell(RabbitMqConsumer.Init, null)

  logger.info("Started")

  // For now: wrap actor in the MessageSender interface.
  // TODO: Consider just passing the actors into the message handlers instead.
  // Or, move these wrapper classes into the common RabbitMq library.
  class PublishingMessageSender(publisherActor: ActorRef) extends EventPublisher {
    implicit val timeout = Timeout(5.seconds) // TODO - get rid of this!
    override def publish(event: Event): Future[Unit] = (publisherActor ? event).map(result => ())
  }

  // Ditto for the ErrorHandlers.
  class PublishingErrorHandler(publisherActor: ActorRef) extends ErrorHandler {
    implicit val timeout = Timeout(5.seconds) // TODO - get rid of this!
    override def handleError(event: Event, e: Throwable): Future[Unit] = {
      logger.error(s"Unrecoverable error in processing event: $event", e)
      (publisherActor ? event).map(result => ())
    }
  }

}
