package com.blinkbox.books.purchasetransformer

import akka.actor.{ ActorSystem, Props }
import akka.io.IO
import akka.util.Timeout
import com.blinkbox.books.config.Configuration
import com.blinkboxbooks.hermes.rabbitmq._
import com.blinkboxbooks.hermes.rabbitmq.RabbitMqConsumer.QueueConfiguration
import com.blinkbox.books.messaging._
import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.duration._
import scala.concurrent.Future
import spray.can.Http
import com.typesafe.config.ConfigObject

/**
 * Entry point for the purchase-transformer service.
 */
object PurchaseTransformerService extends App with Configuration with Logging {

  val Originator = "purchase-transformer"

  logger.info("Starting")

  val connection = RabbitMq.reliableConnection()

  // Initialise the actor system.
  implicit val system = ActorSystem("reporting-service")
  implicit val executionContext = system.dispatcher

  val httpActor = IO(Http)

  // Could use the Java client library instead?
  implicit val timeout = Timeout(10.seconds) // TODO: Config
  val bookDao = new HttpBookDao(httpActor, "http:localhost/catalogue/books") // TODO: Config

  // TODO: Replace these with RabbitMQ implementations when available.
  class DummyMessageSender(exchangeName: String) extends EventPublisher {
    override def publish(event: Event): Future[Unit] = Future {
      println(s"* PRETEND publishing event: $event to $exchangeName")
    }
  }
  class DummyErrorHandler(queueName: String) extends ErrorHandler {
    override def handleError(message: Event, error: Throwable): Future[Unit] = Future {
      println(s"* PRETEND handling error: ${error.getMessage} to $queueName")
    }
  }

  val serviceConf = config.getConfig("service.purchaseTransformer")
  logger.info(s"Starting purchase-transformer service with config: $serviceConf")

  val invalidMessagesForClubcardsQueue = "Clubcard.Listener.DLQ"
  val outgoingExchangeForClubcardMessages = "Clubcard.Collector.Exchange"
  val clubcardMessageSender = new DummyMessageSender(outgoingExchangeForClubcardMessages)
  val clubcardMsgErrorHandler = new DummyErrorHandler(invalidMessagesForClubcardsQueue)

  val invalidMessagesForEmailQueue = "Mail.Listener.DLQ"
  val outgoingExchangeForEmailMessages = "Mail.Sender.Exchange"
  val emailMessageSender = new DummyMessageSender(outgoingExchangeForEmailMessages)
  val emailMsgErrorHandler = new DummyErrorHandler(invalidMessagesForEmailQueue)

  // Create actors for email messages.
  val routingId = "TODO" // TODO: Get from properties.
  val templateName = "TODO" // TODO: Get from properties.
  val retryInterval = 10.seconds // TODO: Get from properties.
  val emailMessageHandler = system.actorOf(Props(
    new EmailMessageHandler(bookDao, emailMessageSender, emailMsgErrorHandler, routingId, templateName, retryInterval)))
  system.actorOf(Props(new RabbitMqConsumer(connection.createChannel,
    QueueConfiguration(serviceConf.getConfig("emailListener.input")), "email-msg-consumer", emailMessageHandler)))
    .tell(RabbitMqConsumer.Init, null)

  // Create actors that handle clubcard point messages.
  val clubcardMessageHandler = system.actorOf(Props(
    new ClubcardMessageHandler(clubcardMessageSender, clubcardMsgErrorHandler, retryInterval)))
  system.actorOf(Props(new RabbitMqConsumer(connection.createChannel,
    QueueConfiguration(serviceConf.getConfig("clubcardListener.input")), "clubcard-msg-consumer", clubcardMessageHandler)))
    .tell(RabbitMqConsumer.Init, null)

  logger.info("Started")
}
