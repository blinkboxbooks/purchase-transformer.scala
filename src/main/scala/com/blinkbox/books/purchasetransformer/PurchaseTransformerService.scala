package com.blinkbox.books.purchasetransformer

import akka.actor.ActorSystem
import akka.actor.Props
import akka.io.IO
import akka.util.Timeout
import com.blinkbox.books.config.Configuration
import com.blinkbox.books.messaging._
import com.blinkboxbooks.hermes.rabbitmq.AmqpConsumerActor
import com.typesafe.scalalogging.slf4j.Logging
import com.rabbitmq.client.ConnectionFactory
import net.jodah.lyra.Connections
import net.jodah.lyra.config._
import net.jodah.lyra.util.{ Duration => LyraDuration }
import spray.can.Http
import scala.concurrent.duration._
import scala.concurrent.Future
import com.blinkboxbooks.hermes.rabbitmq.RabbitMqConsumer
import com.blinkboxbooks.hermes.rabbitmq.RabbitMqConsumer.QueueConfiguration
import com.typesafe.config.ConfigFactory

object PurchaseTransformerService extends App with Configuration with Logging {

  val Originator = "purchase-transformer"

  logger.info("Starting")

  val serviceConf = config.getConfig("service.purchaseTransformer")

  // TODO: Get RabbitMQ configs from Configuration API.
  val factory = new ConnectionFactory()
  factory.setHost("127.0.0.1")
  factory.setPort(5672)
  factory.setUsername("guest")
  factory.setPassword("guest")

  // Note: this doesn't fail on startup if the broker is down, it will try to reconnection.
  // This is a debated issue, but I think this is far better!!!
  val lyraConfig = new Config()
    .withConnectRetryPolicy(RetryPolicies.retryAlways)
    .withRecoveryPolicy(new RecoveryPolicy()
      .withBackoff(LyraDuration.seconds(initialRetryInterval), LyraDuration.seconds(maxRetryInterval)))
    .withRetryPolicy(new RetryPolicy()
      .withBackoff(LyraDuration.seconds(initialRetryInterval), LyraDuration.seconds(maxRetryInterval)))

  val connection = Connections.create(factory, lyraConfig)

  //  val messagesForClubcardsQueueName = "Clubcard.Listener.Queue"
  //  val messagesForClubcardsExchangeName = "Clubcard.Listener.Exchange" // TODO: Add to config.
  //  val clubcardRoutingKeys = List("routing.keys") // TODO: Add to config.
  val invalidMessagesForClubcardsQueue = "Clubcard.Listener.DLQ"
  val outgoingExchangeForClubcardMessages = "Clubcard.Collector.Exchange"

  val messagesForEmailQueueName = "Mail.Listener.Queue"
  val invalidMessagesForEmailQueue = "Mail.Listener.DLQ"
  val outgoingExchangeForEmailMessages = "Mail.Sender.Exchange"

  implicit val system = ActorSystem("reporting-service")
  implicit val executionContext = system.dispatcher

  val httpActor = IO(Http)

  // TODO: Proper timeouts etc.
  implicit val timeout = Timeout(10.seconds)
  val initialRetryInterval = 5L
  val maxRetryInterval = 60L
  val amqpTimeout = Timeout(10.seconds)

  // Could use the Java client library instead?
  val bookDao = new HttpBookDao(httpActor, "http:localhost/catalogue/books") // TODO: Config

  // TODO: Could factory out standard connection policies into the shared library.
  // This would entail placing the configuration properties for these in a common place in the
  // config hiearchy. We could do that with all the other settings for the RabbitMQ broker too,
  // and have common code that creates connections to it without having to fetch out the details in each service.

  val routingId = "TODO" // TODO: Get from properties.
  val templateName = "TODO" // TODO: Get from properties.
  val retryInterval = 10.seconds // TODO: Get from properties.

  // TODO: Replace these with RabbitMQ implementations when available.
  class DummyMessageSender(exchangeName: String) extends EventPublisher {
    override def publish(event: Event): Future[Unit] = ???
  }
  class DummyErrorHandler(queueName: String) extends ErrorHandler {
    override def handleError(message: Event, error: Throwable): Future[Unit] = ???
  }

  val emailMessageSender: EventPublisher = new DummyMessageSender(messagesForEmailQueueName)
  val clubcardMessageSender: EventPublisher = new DummyMessageSender(outgoingExchangeForClubcardMessages)

  val emailMsgErrorHandler: ErrorHandler = new DummyErrorHandler(invalidMessagesForEmailQueue)
  val clubcardMsgErrorHandler: ErrorHandler = new DummyErrorHandler(invalidMessagesForClubcardsQueue)

  // Create actors for email messages.
  val emailMessageHandler = system.actorOf(Props(
    new EmailMessageHandler(bookDao, emailMessageSender, emailMsgErrorHandler, routingId, templateName, retryInterval)))
  system.actorOf(Props(new RabbitMqConsumer(connection.createChannel,
    QueueConfiguration(serviceConf.getConfig("emailQueue")), "email-msg-consumer", emailMessageHandler)))

  // Create actors that handle clubcard point messages.
  val clubcardMessageHandler = system.actorOf(Props(
    new ClubcardMessageHandler(clubcardMessageSender, clubcardMsgErrorHandler, retryInterval)))
  system.actorOf(Props(new RabbitMqConsumer(connection.createChannel,
    QueueConfiguration(serviceConf.getConfig("clubcardQueue")), "clubcard-msg-consumer", clubcardMessageHandler)))

  logger.info("Started")
}
