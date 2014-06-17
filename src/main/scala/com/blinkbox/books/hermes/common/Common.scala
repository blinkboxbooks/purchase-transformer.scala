package com.blinkbox.books.hermes.common

import scala.concurrent.Future
import com.blinkboxbooks.hermes.rabbitmq.Message

/**
 * Temporary bucket o'stuff, things here will be moved into common libraries or other places.
 */

object Common {

}

/**
 * Common interface for mechanism used to pass on message.
 */
trait MessageSender {
  def send(message: Message): Future[Unit]
}

// TODO: Placeholder for actual implementation.
class RabbitMqMessageSender extends MessageSender {
  def send(message: Message): Future[Unit] = ???
}

/**
 *  Common exception class for message processing code.
 */
class MessageException(msg: String, cause: Throwable) extends Exception(msg, cause) {
  def this(msg: String) = this(msg, null)
}

