package com.blinkbox.books.purchasetransformer

import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.Status.{ Success, Failure }
import java.util.concurrent.TimeoutException
import java.io.IOException
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.xml.NodeSeq
import scala.xml.XML
import com.blinkboxbooks.hermes.rabbitmq._
import com.blinkbox.books.hermes.common.ErrorHandler
import com.blinkbox.books.hermes.common.Common._
import akka.actor.ActorRef
import com.blinkbox.books.hermes.common.MessageSender
import java.io.ByteArrayInputStream

import com.blinkbox.books.hermes.common.XmlUtils.NodeSeqWrapper

/**
 * Actor that receives incoming purchase-complete messages,
 * gets additional information about books purchased,
 * and passes on Email messages.
 */
class EmailMessageHandler(bookDao: BookDao, output: MessageSender, errorHandler: ErrorHandler,
  routingId: String, templateName: String)
  extends Actor with ActorLogging {

  protected val retryInterval = 10.seconds

  implicit val ec = context.dispatcher

  def receive = {
    case msg @ Message(_, _, _, payload) =>
      val result = for (
        purchase <- Future(purchaseFromXml(payload));
        isbns = purchase.basketItems.map(_.isbn);
        bookFuture = bookDao.getBooks(isbns);
        books <- bookFuture;
        emailContent = buildEmailContent(purchase, books);
        sendResult = output.send(message(msg, emailContent))
      ) yield sendResult

      result.onComplete {
        case scala.util.Success(_) => sender ! Success("Sent message")
        case scala.util.Failure(e) if isTemporaryFailure(e) => reschedule(msg)
        case scala.util.Failure(e) => handleUnrecoverableFailure(msg, e)
      }
  }

  // These are candidates for common methods in a message handler base class.
  private def reschedule(msg: Any) = context.system.scheduler.scheduleOnce(retryInterval, self, msg)
  private def handleUnrecoverableFailure(msg: Message, e: Throwable) = errorHandler.handleError(msg, e)

  // These are potential abstract methods that concrete message handlers should override.

  protected def isTemporaryFailure(e: Throwable) = e.isInstanceOf[IOException] || e.isInstanceOf[TimeoutException] // TODO

  // These are specific to the message types processed in each handler - any way we can make them generic?
  private def message(inputMessage: Message, content: String): Message = ???

  /**
   * Parse input message.
   */
  private def purchaseFromXml(xml: Array[Byte]): Purchase = {
    val purchase = XML.load(new ByteArrayInputStream(xml))
    val basketItems = for (basketItem <- purchase \ "basketItems" \ "basketItem")
      yield BasketItem(basketItem.value("isbn"), price(basketItem \ "salePrice"), price(basketItem \ "listPrice"))

    Purchase(purchase.value("firstName"), purchase.value("lastName"), purchase.value("email"),
      purchase.value("clubcardNumber"), purchase.value("clubcardPointsAward").toInt,
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
      <sendEmail r:messageId="{ generateReceipt(basketItem) }" r:instance="email.routing.instance" r:originator="bookStore" xmlns="http://schemas.blinkbox.com/books/emails/sending/v1" xmlns:r="http://schemas.blinkbox.com/books/routing/v1">
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
            <value>First</value>
          </templateVariable>
          <templateVariable>
            <key>bookTitle</key>
            <value>{ book.title }</value>
          </templateVariable>
          <templateVariable>
            <key>author</key>
            <value>{ "TODO!!" } </value>
          </templateVariable>
          <templateVariable>
            <key>price</key>
            <value>{ basketItem.salePrice.amount }</value>
          </templateVariable>
        </templateVariables>
      </sendEmail>

    xml.toString
  }

}
