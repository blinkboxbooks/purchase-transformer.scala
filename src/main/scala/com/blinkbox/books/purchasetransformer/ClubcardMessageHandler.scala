package com.blinkbox.books.purchasetransformer

import akka.actor.ActorLogging
import akka.actor.Actor

import com.blinkboxbooks.hermes.rabbitmq._
import com.blinkbox.books.hermes.common.Common._
import com.blinkbox.books.hermes.common.ErrorHandler

/**
 * Actor that receives incoming purchase-complete messages
 * and passes on Clubcard messages.
 */

class ClubcardMessageHandler(bookDao: BookDao, emailMessageSender: MessageSender, errorHandler: ErrorHandler)
  extends Actor with ActorLogging {

  def receive = {
    case Message(_, _, _, payload) =>
    // TODO: This will extract the message in the same way as the other one,
    // but process it in a different way and send a different message out.
    // Or, given that it's so simple, could just use the XSLT transform used in the old code
    // to transform the input, without going via objects.
    case msg => log.warning("Unexpected message: " + msg)
  }

}

