package com.blinkbox.books.hermes.common

import scala.concurrent.Future
import com.blinkboxbooks.hermes.rabbitmq.Message

/**
 * Common interface for objects that dispose of messages that can't be processed,
 * typically because they are invalid.
 *
 *  Implementations will normally persist these messages in a safe location, e.g. a database or a DLQ.
 */
trait ErrorHandler {

  /**
   * Record the fact that the given message failed, due to the given reason.
   *
   * May return an exception if storage fails.
   */
  // TODO: Consider if the message type here should be an (amqp) Message.
  // In which case this trait, and maybe the DLQ implementation, should go into the
  // Hermes.rabbitmq-ha library.
  // OR: Should the Message class actually go into a messaging-common library, and have
  // fields that represent metadata we should include, e.g. UID, timestamp, originator, content-type,
  // user ID, topic etc. (some of them optional). And the AMQP/RabbitMQ implementation would populate this from
  // AMQP headers...
  def handleError(message: Message, error: Throwable): Future[Unit]

}

/**
 * Simple error handler implementation that tries to write errors to a RabbitMQ queue.
 */
class RabbitMqErrorHandler(queueName: String) extends ErrorHandler {

  // TODO: Should change this to take a common Message type instead, so we can store/forward all the 
  // headers etc of the message.
  override def handleError(message: Message, error: Throwable): Future[Unit] = ???

}
