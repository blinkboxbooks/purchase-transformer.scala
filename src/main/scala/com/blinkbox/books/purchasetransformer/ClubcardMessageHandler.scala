package com.blinkbox.books.purchasetransformer

import akka.actor.ActorRef
import com.blinkbox.books.hermes.common.ErrorHandler
import com.blinkbox.books.hermes.common.MessageSender
import com.blinkbox.books.hermes.common.ReliableMessageHandler
import com.blinkboxbooks.hermes.rabbitmq.Message
import java.io.IOException
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future

/**
 * Actor that receives incoming purchase-complete messages
 * and passes on Clubcard messages.
 */

class ClubcardMessageHandler(output: MessageSender, errorHandler: ErrorHandler, retryInterval: FiniteDuration)
  extends ReliableMessageHandler(output, errorHandler, retryInterval) {

  // TODO: This could just use the XSLT transform used in the old code
  // to transform the input, without going via objects.
  override def handleMessage(message: Message, originalSender: ActorRef): Future[Unit] = ??? // TODO

  // TODO: Check!
  override def isTemporaryFailure(e: Throwable) = e.isInstanceOf[IOException] || e.isInstanceOf[TimeoutException]

}
