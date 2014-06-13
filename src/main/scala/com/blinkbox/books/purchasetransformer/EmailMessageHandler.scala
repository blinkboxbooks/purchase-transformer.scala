package com.blinkbox.books.purchasetransformer

import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.Status.Failure
import akka.actor.Status.Success
import java.util.concurrent.TimeoutException
import java.io.IOException
import PurchaseTransformerService._
import scala.concurrent.duration._
import scala.concurrent.Future

import com.blinkboxbooks.hermes.rabbitmq._
import com.blinkbox.books.hermes.common.ErrorHandler
import com.blinkbox.books.hermes.common.Common._

/**
 * Actor that receives incoming purchase-complete messages,
 * gets additional information about books purchased,
 * and passes on Email messages.
 */
class EmailMessageHandler(bookDao: BookDao, send: MessageSender, errorHandler: ErrorHandler)
  extends Actor with ActorLogging {

  protected val retryInterval = 10.seconds

  def receive = {
    case msg @ Message(_, _, _, payload) =>
      val result = for (
        purchase <- Future(purchaseFromXml(payload));
        isbns = purchase.basketItems.map(_.isbn);
        bookFuture = bookDao.getBooks(isbns);
        books <- bookFuture;
        emailMessage = buildEmailMessage(purchase, books);
        sendResult = send(mailMessageToXml(emailMessage))
      ) yield sendResult

      result.onComplete {
        case scala.util.Success(_) => sender ! Success("Sent message")
        case scala.util.Failure(e) if isTemporaryFailure(e) => reschedule(msg)
        case scala.util.Failure(e) => handleUnrecoverableFailure(msg, e)
      }
  }

  // These are candidates for common methods in a message handler base class.
  private def reschedule(msg: Any) = context.system.scheduler.scheduleOnce(retryInterval, self, msg)
  private def handleUnrecoverableFailure(msg: Any, e: Throwable) = println("Not good: " + e.getMessage) // TODO!!!

  // These are potential abstract methods that concrete message handlers should override.

  protected def isTemporaryFailure(e: Throwable) = e.isInstanceOf[IOException] || e.isInstanceOf[TimeoutException] // TODO: Check.

  // These are specific to the message types processed in each handler - any way we can make this generic?
  private def purchaseFromXml(xml: Array[Byte]): PurchaseComplete = ???
  private def buildEmailMessage(purchase: PurchaseComplete, books: BookList): MailMessage = ???
  private def mailMessageToXml(msg: MailMessage): String = ???

}

/**
 * The outgoing mail message.
 */
// TODO!
case class MailMessage()  
