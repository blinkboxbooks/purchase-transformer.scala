package com.blinkbox.books.hermes.common

import scala.concurrent.Future

/**
 * Temporary bucket o'stuff, things here will be moved into common libraries or other places.
 */

object Common {

  /**
   * TODO: Move somewhere else.
   * Type of function that sends messages to some previously bound queue.
   */
  type MessageSender = (Any) => Future[Unit]

}

/**
 *  Common exception class for message processing code.
 */
class MessageException(msg: String, cause: Throwable) extends Exception(msg, cause) {
  def this(msg: String) = this(msg, null)
}

