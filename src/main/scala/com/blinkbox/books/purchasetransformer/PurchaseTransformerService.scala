package com.blinkbox.books.purchasetransformer

import akka.actor.{ ActorSystem, ActorRef, Props }
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.blinkbox.books.config.{ Configuration, RichConfig }
import com.blinkbox.books.logging.Loggers
import com.blinkbox.books.rabbitmq._
import com.blinkbox.books.rabbitmq.RabbitMqConsumer.QueueConfiguration
import com.blinkbox.books.rabbitmq.RabbitMqConfirmedPublisher.PublisherConfiguration
import com.blinkbox.books.messaging._
import com.rabbitmq.client.Connection
import com.typesafe.config.Config
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import spray.can.Http
import scala.concurrent.duration._
import scala.concurrent.Future

/**
 * Entry point for the purchase-transformer service.
 */
object PurchaseTransformerService extends App with Configuration /*with Loggers*/ {

  val log = LoggerFactory.getLogger(getClass)

  val Originator = "purchase-transformer"

  val serviceConf = config.getConfig("service.purchaseTransformer")

  log.info(s"Starting purchase-transformer service with config: $serviceConf")

  // Use separate connections for consumers and publishers.
  def newConnection() = RabbitMq.reliableConnection(RabbitMqConfig(config))
  val publisherConnection = newConnection()
  val consumerConnection = newConnection()

  private def publisher(config: Config, actorName: String) =
    system.actorOf(Props(new RabbitMqConfirmedPublisher(
      publisherConnection.createChannel(), PublisherConfiguration(config))), name = actorName)

  // Initialise the actor system.
  implicit val system = ActorSystem("purchase-transformer-service")
  implicit val ec = system.dispatcher

  // Could use the Java Book client library instead?
  val httpActor = IO(Http)
  val ClientRequestTimeout = Timeout(serviceConf.getDuration("clientRequestInterval", TimeUnit.SECONDS).seconds)
  val bookDao = new HttpBookDao(httpActor, serviceConf.getHttpUrl("bookService.url").toString)(ClientRequestTimeout, ec)

  // TODO: Add helpers for getting scala.concurrent.Durations to common-config?
  val ClientRequestRetryInterval = serviceConf.getDuration("clientRequestInterval", TimeUnit.SECONDS).seconds

  val routingId = serviceConf.getString("emailListener.routingInstance")
  val templateName = serviceConf.getString("emailListener.templateName")
  val retryInterval = serviceConf.getDuration("clientRequestTimeout", TimeUnit.SECONDS).seconds

  log.debug("Initialising actors")

  implicit val actorTimeout = Timeout(serviceConf.getDuration("actorTimeout", TimeUnit.SECONDS).seconds)

  // Create actors that handle email messages.
  val emailMessageSender = publisher(serviceConf.getConfig("emailListener.output"), "email-publisher")
  val emailMsgErrorHandler = new ActorErrorHandler(publisher(serviceConf.getConfig("emailListener.error"), "email-error-publisher"))
  val emailMessageHandler = system.actorOf(Props(
    new EmailMessageHandler(bookDao, emailMessageSender, emailMsgErrorHandler, routingId, templateName, retryInterval)),
    name = "email-message-handler")

  system.actorOf(Props(new RabbitMqConsumer(consumerConnection.createChannel,
    QueueConfiguration(serviceConf.getConfig("emailListener.input")), "email-msg-consumer", emailMessageHandler)), name = "email-listener")
    .tell(RabbitMqConsumer.Init, null)

  // Create actors that handle Clubcard point messages.
  val clubcardMessageSender = publisher(serviceConf.getConfig("clubcardListener.output"), "clubcard-publisher")
  val clubcardMsgErrorHandler = new ActorErrorHandler(publisher(serviceConf.getConfig("clubcardListener.error"), "clubcard-error-publisher"))
  val clubcardMessageHandler = system.actorOf(Props(new ClubcardMessageHandler(
    clubcardMessageSender, clubcardMsgErrorHandler, retryInterval)), name = "clubcard-message-handler")

  system.actorOf(Props(new RabbitMqConsumer(consumerConnection.createChannel,
    QueueConfiguration(serviceConf.getConfig("clubcardListener.input")), "clubcard-msg-consumer", clubcardMessageHandler)), name = "clubcard-listener")
    .tell(RabbitMqConsumer.Init, null)

  log.info("Started")
}
