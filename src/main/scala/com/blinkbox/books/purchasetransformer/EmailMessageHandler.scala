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
import com.blinkbox.books.hermes.common.ReliableMessageHandler

/**
 * Actor that receives incoming purchase-complete messages,
 * gets additional information about books purchased,
 * and passes on Email messages.
 */
class EmailMessageHandler(bookDao: BookDao, output: MessageSender, errorHandler: ErrorHandler,
  routingId: String, templateName: String, retryInterval: FiniteDuration)
  extends ReliableMessageHandler(output, errorHandler, retryInterval) {

  override def handleMessage(message: Message, originalSender: ActorRef): Future[Unit] =
    for (
      purchase <- Future(purchaseFromXml(message.body));
      isbns = purchase.basketItems.map(_.isbn);
      bookFuture = bookDao.getBooks(isbns);
      books <- bookFuture;
      emailContent = buildEmailContent(purchase, books);
      sendResult <- output.send(outgoingMessage(message, emailContent))
    ) yield sendResult

  protected def isTemporaryFailure(e: Throwable) = e.isInstanceOf[IOException] || e.isInstanceOf[TimeoutException] // TODO: check

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

