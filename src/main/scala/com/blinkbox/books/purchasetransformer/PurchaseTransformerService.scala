package com.blinkbox.books.purchasetransformer

import akka.actor.ActorSystem
import akka.actor.Props
import akka.io.IO
import akka.util.Timeout
import com.blinkbox.books.config.Configuration
import com.blinkboxbooks.hermes.rabbitmq.AmqpConsumerActor
import com.blinkbox.books.hermes.common.ErrorHandler
import com.blinkbox.books.hermes.common.RabbitMqErrorHandler
import com.blinkbox.books.hermes.common.Common._
import com.typesafe.scalalogging.slf4j.Logging
import com.rabbitmq.client.ConnectionFactory
import net.jodah.lyra.Connections
import net.jodah.lyra.config._
import net.jodah.lyra.util.{ Duration => LyraDuration }
import spray.can.Http
import scala.concurrent.duration._
import scala.concurrent.Future
import com.blinkbox.books.hermes.common.MessageSender
import com.blinkbox.books.hermes.common.RabbitMqMessageSender

object PurchaseTransformerService extends App with Configuration with Logging {

  // TODO: Get configs from Configuration API.
  val factory = new ConnectionFactory()
  factory.setHost("127.0.0.1")
  factory.setPort(5672)
  factory.setUsername("guest")
  factory.setPassword("guest")

  val messagesForClubcardsQueueName = "Clubcard.Listener.Queue"
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
  val prefetchCount = 100

  val bookDao = new HttpBookDao(httpActor, "http:localhost/catalogue/books")
  // TODO: Going to need a price client too? http://qa.mobcastdev.com/service/catalogue/prices
  // Could use the Java client library?

  val emailMessageSender: MessageSender = new RabbitMqMessageSender() // TODO: Put in place something that sends a message on an outgoing channel.
  val clubcardMessageSender: MessageSender = new RabbitMqMessageSender() // TODO: Put in place something that sends a message on an outgoing channel.

  val emailMsgErrorHandler: ErrorHandler = new RabbitMqErrorHandler(invalidMessagesForEmailQueue)
  val clubcardMsgErrorHandler: ErrorHandler = new RabbitMqErrorHandler(invalidMessagesForClubcardsQueue)

  // TODO: Could factory out standard connection policies into the shared library.
  // This would entail placing the configuration properties for these in a common place in the
  // config hiearchy. We could do that with all the other settings for the RabbitMQ broker too,
  // and have common code that creates connections to it without having to fetch out the details in each service.

  // Note: this doesn't fail on startup if the broker is down, it will try to reconnection.
  // This is a debated issue, but I think this is far better!!!
  val lyraConfig = new Config()
    .withConnectRetryPolicy(RetryPolicies.retryAlways)
    .withRecoveryPolicy(new RecoveryPolicy()
      .withBackoff(LyraDuration.seconds(initialRetryInterval), LyraDuration.seconds(maxRetryInterval)))
    .withRetryPolicy(new RetryPolicy()
      .withBackoff(LyraDuration.seconds(initialRetryInterval), LyraDuration.seconds(maxRetryInterval)))

  val connection = Connections.create(factory, lyraConfig)

  val routingId = "TODO" // TODO: Get from properties.
  val templateName = "TODO" // TODO: Get from properties.
  val retryInterval = 10.seconds // TODO: Get from properties.

  // Create actors for email messages.
  val emailMessageHandler = system.actorOf(Props(
    new EmailMessageHandler(bookDao, emailMessageSender, emailMsgErrorHandler, routingId, templateName, retryInterval)))
  val emailMessageReceiver = system.actorOf(Props(AmqpConsumerActor(connection.createChannel, emailMessageHandler,
    messagesForEmailQueueName, None, amqpTimeout, None, "email-msg-consumer", prefetchCount)))

  // Create actor that produces email messages.
  // Create actor that gets purchase transform messages from the dedicated queue, passing it to the above actor.
  val clubcardMessageHandler = system.actorOf(Props(new ClubcardMessageHandler(bookDao, clubcardMessageSender, clubcardMsgErrorHandler)))
  val clubcardMessageReceiver = system.actorOf(Props(AmqpConsumerActor(connection.createChannel, clubcardMessageHandler,
    messagesForEmailQueueName, None, amqpTimeout, None, "clubcard-msg-consumer", prefetchCount)))

  logger.info("Started")
}

