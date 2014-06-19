package com.blinkbox.books.purchasetransformer

import akka.actor.ActorRef
import akka.actor.Status.{ Success, Failure }
import com.blinkboxbooks.hermes.rabbitmq._
import com.blinkbox.books.messaging._
import java.io.IOException
import java.util.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.concurrent.Future

/**
 * Actor that receives incoming purchase-complete messages,
 * gets additional information about books purchased,
 * and passes on Email messages.
 */
class EmailMessageHandler(bookDao: BookDao, output: EventPublisher, errorHandler: ErrorHandler,
  routingId: String, templateName: String, retryInterval: FiniteDuration)
  extends ReliableEventHandler(output, errorHandler, retryInterval) {

  override def handleEvent(event: Event, originalSender: ActorRef): Future[Unit] =
    for (
      purchase <- Future(Purchase.fromXml(event.body.content));
      isbns = purchase.basketItems.map(_.isbn);
      bookFuture = bookDao.getBooks(isbns);
      books <- bookFuture;
      emailContent = buildEmailContent(purchase, books);
      eventContext = Purchase.context(purchase);
      sendResult <- output.publish(Event.xml(emailContent, eventContext))
    ) yield sendResult

  // TODO: check
  override protected def isTemporaryFailure(e: Throwable) =
    e.isInstanceOf[IOException] || e.isInstanceOf[TimeoutException]

  /**
   * Generate output message.
   */
  private def buildEmailContent(purchase: Purchase, books: BookList): String = {
    // The message format only supports a single book at the moment.
    val book = books.items(0)
    val basketItem = purchase.basketItems(0)

    val xml =
      <sendEmail r:messageId={ generateReceipt(purchase) } r:instance={ routingId } r:originator="bookStore" xmlns="http://schemas.blinkbox.com/books/emails/sending/v1" xmlns:r="http://schemas.blinkbox.com/books/routing/v1">
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

  private def generateReceipt(purchase: Purchase): String =
    s"$templateName-${purchase.userId}-${purchase.basketId}"

  private def singleAuthorName(book: Book): String =
    book.links.headOption.map(_.title).getOrElse("")

}

