package com.blinkbox.books.purchasetransformer

import akka.actor.ActorRef
import akka.actor.Status.{ Success, Failure }
import akka.pattern.ask
import akka.util.Timeout
import com.blinkbox.books.rabbitmq._
import com.blinkbox.books.messaging._
import java.io.IOException
import java.util.concurrent.TimeoutException
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.Future
import spray.can.Http.ConnectionException

/**
 * Actor that receives incoming purchase-complete messages,
 * gets additional information about books purchased,
 * and passes on Email messages.
 */
class EmailMessageHandler(bookDao: BookDao, output: ActorRef, errorHandler: ErrorHandler,
  routingId: String, templateName: String, retryInterval: FiniteDuration)
  extends ReliableEventHandler(errorHandler, retryInterval) {

  private val XmlDeclaration = """<?xml version="1.0" encoding="UTF-8"?>""" + "\n"
  private implicit val timeout = Timeout(retryInterval)
  
  override def handleEvent(event: Event, originalSender: ActorRef): Future[Unit] =
    for (
      purchase <- Future(Purchase.fromXml(event.body.content));
      isbns = purchase.basketItems.map(_.isbn);
      bookFuture = bookDao.getBooks(isbns);
      books <- bookFuture;
      emailContent = buildEmailContent(purchase, books);
      eventContext = Purchase.context(purchase);
      sendResult <- output ? Event.xml(emailContent, eventContext)
    ) yield ()

  // Consider the error temporary if the exception or its root cause is an IO exception or timeout.
  @tailrec
  final override def isTemporaryFailure(e: Throwable) =
    e.isInstanceOf[IOException] || e.isInstanceOf[TimeoutException] || e.isInstanceOf[ConnectionException] ||
      Option(e.getCause).isDefined && isTemporaryFailure(e.getCause)

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

    XmlDeclaration + xml.toString
  }

  private def generateReceipt(purchase: Purchase): String =
    s"$templateName-${purchase.userId}-${purchase.basketId}"

  private def singleAuthorName(book: Book): String =
    book.links.headOption.map(_.title).getOrElse("")

}

