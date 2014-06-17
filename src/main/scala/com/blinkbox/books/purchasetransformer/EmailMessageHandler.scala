package com.blinkbox.books.purchasetransformer

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeoutException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import scala.xml.NodeSeq
import scala.xml.XML

import com.blinkbox.books.hermes.common.Common._
import com.blinkbox.books.hermes.common.ErrorHandler
import com.blinkbox.books.hermes.common.MessageSender
import com.blinkbox.books.hermes.common.XmlUtils.NodeSeqWrapper
import com.blinkboxbooks.hermes.rabbitmq._

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Status.Failure
import akka.actor.Status.Success

/**
 * Actor that receives incoming purchase-complete messages,
 * gets additional information about books purchased,
 * and passes on Email messages.
 */
class EmailMessageHandler(bookDao: BookDao, output: MessageSender, errorHandler: ErrorHandler,
  routingId: String, templateName: String, retryInterval: FiniteDuration)
  extends Actor with ActorLogging {

  implicit val ec = context.dispatcher

  def receive = {
    case msg @ Message(_, _, _, payload) =>
      val originalSender = sender
      val result = for (
        purchase <- Future(purchaseFromXml(payload));
        isbns = purchase.basketItems.map(_.isbn);
        bookFuture = bookDao.getBooks(isbns);
        books <- bookFuture;
        emailContent = buildEmailContent(purchase, books);
        sendResult = output.send(message(msg, emailContent))
      ) yield sendResult

      result.onComplete {
        case scala.util.Success(_) => originalSender ! Success("Sent message")
        case scala.util.Failure(e) if isTemporaryFailure(e) => reschedule(msg, originalSender)
        case scala.util.Failure(e) => handleUnrecoverableFailure(msg, e, originalSender)
      }
  }

  // These are candidates for common methods in a message handler base class.

  /**
   * Reschedule message to be retried after an inveral. When re-sent, make sure
   * that the message still has the same sender as the original.
   */
  private def reschedule(msg: Any, originalSender: ActorRef) =
    context.system.scheduler.scheduleOnce(retryInterval, self, msg)(ec, originalSender)

  /**
   * An unrecoverable failure should be ACKed, i.e. we have successfully competed processing of it,
   * even if the result wasn't as desired. Hence this will send a Success message to the original sender.
   * The handling of the unrecoverable error is delegated to the error handler.
   *
   * If the error handler itself fails, this should cause a retry of the message again.
   */
  private def handleUnrecoverableFailure(msg: Message, e: Throwable, originalSender: ActorRef) = {
    log.error(s"Unable to process message: ${e.getMessage}\nInput message was: ${new String(msg.body)}", e)
    errorHandler.handleError(msg, e).onComplete {
      case scala.util.Success(_) => {
        log.info("Stored invalid message for later processing")
        originalSender ! akka.actor.Status.Success(e)
      }
      case scala.util.Failure(e) => {
        log.warning("Error handler failed to deal with error, rescheduling", e)
        reschedule(msg, originalSender)
      }
    }
  }

  // These are potential abstract methods that concrete message handlers should override.

  protected def isTemporaryFailure(e: Throwable) = e.isInstanceOf[IOException] || e.isInstanceOf[TimeoutException] // TODO

  // These are specific to the message types processed in each handler - any way we can make them generic?
  // TODO: change to different Message class, and set outgoing headers correctly.
  private def message(inputMessage: Message, content: String) =
    Message("", inputMessage.envelope, inputMessage.properties, content.getBytes("UTF-8"))

  /**
   * Parse input message.
   */
  private def purchaseFromXml(xml: Array[Byte]): Purchase = {
    val purchase = XML.load(new ByteArrayInputStream(xml))
    val basketId = purchase.value("basketId")
    val basketItems = for (basketItem <- purchase \ "basketItems" \ "basketItem")
      yield BasketItem(basketId, basketItem.value("isbn"),
      price(basketItem \ "salePrice"), price(basketItem \ "listPrice"))

    Purchase(purchase.value("userId"), purchase.value("firstName"),
      purchase.value("lastName"), purchase.value("email"),
      purchase.optionalValue("clubcardNumber"), purchase.optionalValue("clubcardPointsAward").map(_.toInt),
      price(purchase \ "totalPrice"), basketItems)
  }

  private def price(priceNode: NodeSeq) =
    Price(BigDecimal(priceNode.value("amount")), priceNode.value("currency"))

  /**
   * Generate output message.
   */
  private def buildEmailContent(purchase: Purchase, books: BookList): String = {
    // The message format only supports a single book at the moment.
    val book = books.items(0)
    val basketItem = purchase.basketItems(0)

    val xml =
      <sendEmail r:messageId={ generateReceipt(purchase, basketItem) } r:instance={ routingId } r:originator="bookStore" xmlns="http://schemas.blinkbox.com/books/emails/sending/v1" xmlns:r="http://schemas.blinkbox.com/books/routing/v1">
        <template>{ templateName }</template>
        <to>
          <recipient>
            <name>{ purchase.firstName }</name>
            <email>{ purchase.email }</email>
          </recipient>
        </to>
        <templateVariables>
          <templateVariable>
            <key>salutation</key>
            <value>{ purchase.firstName }</value>
          </templateVariable>
          <templateVariable>
            <key>bookTitle</key>
            <value>{ book.title }</value>
          </templateVariable>
          <templateVariable>
            <key>author</key>
            <value>{ singleAuthorName(book) }</value>
          </templateVariable>
          <templateVariable>
            <key>price</key>
            <value>{ basketItem.salePrice.amount }</value>
          </templateVariable>
        </templateVariables>
      </sendEmail>

    xml.toString
  }

  private def generateReceipt(purchase: Purchase, basketItem: BasketItem): String =
    s"$templateName-${purchase.userId}-${basketItem.id}"

  private def singleAuthorName(book: Book): String =
    book.links.headOption.map(_.title).getOrElse("")

}

