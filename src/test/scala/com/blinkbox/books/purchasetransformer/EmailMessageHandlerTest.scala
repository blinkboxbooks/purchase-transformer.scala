package com.blinkbox.books.purchasetransformer

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.mockito.Matchers.{ eq => matcherEq }
import org.mockito.ArgumentCaptor
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuiteLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import scala.concurrent.Future
import com.blinkbox.books.hermes.common.Common._
import com.blinkbox.books.hermes.common.ErrorHandler
import com.blinkbox.books.hermes.common.MessageSender
import com.blinkboxbooks.hermes.rabbitmq.Message
import org.xml.sax.SAXException

/**
 * Tests that check the behaviour of the overall app, only mocking out RabbitMQ and external web services.
 */
@RunWith(classOf[JUnitRunner])
class EmailMessageHandlerTest extends TestKit(ActorSystem("test-system")) with ImplicitSender
  with FunSuiteLike with BeforeAndAfter with MockitoSugar {

  import EmailMessageHandlerTests._

  private var errorHandler: ErrorHandler = _
  private var messageSender: MessageSender = _
  private var bookDao: BookDao = _

  private var handler: ActorRef = _

  before {
    errorHandler = mock[ErrorHandler]
    messageSender = mock[MessageSender]
    bookDao = mock[BookDao]

    handler = emailHandler
  }

  //
  // Happy path.
  //

  test("Send message with all optional fields populated") {
    val books = isbns(1, 2)
    doReturn(Future.successful(bookList(books))).when(bookDao).getBooks(books)

    handler ! message(testMessage(2, 2, true).toString)
    expectNoMsg

    verify(bookDao).getBooks(books)
    val argument = ArgumentCaptor.forClass(classOf[Message])
    verify(messageSender).send(argument.capture)
    val content = new String(argument.getValue.body, "UTF-8")
    assert(content == expectedEmailMessage(2, 2, true))

    verifyNoMoreInteractions(errorHandler, messageSender)
  }

  test("Send message without optional fields") {
    val books = isbns(1)
    doReturn(Future.successful(bookList(books))).when(bookDao).getBooks(books)

    handler ! message(testMessage(1, 1, false).toString)
    expectNoMsg

    val argument = ArgumentCaptor.forClass(classOf[Message])
    verify(messageSender).send(argument.capture)
    val content = new String(argument.getValue.body, "UTF-8")
    assert(content == expectedEmailMessage(1, 1, false))

    verifyNoMoreInteractions(errorHandler, messageSender)
  }

  //
  // Failure scenarios.
  //

  test("non-well-formed XML input") {
    val msg = message("Not valid XML")
    handler ! msg
    expectNoMsg

    val error = ArgumentCaptor.forClass(classOf[Exception])
    verify(errorHandler).handleError(matcherEq(msg), error.capture)
    assert(error.getValue.isInstanceOf[SAXException])
    verifyNoMoreInteractions(errorHandler, messageSender)
  }

  test("Well-formed XML that fails in conversion") {
    val msg = message("<p:purchase><invalid>Not the expeced content</invalid></p:purchase>")
    handler ! msg
    expectNoMsg

    val error = ArgumentCaptor.forClass(classOf[Exception])
    verify(errorHandler).handleError(matcherEq(msg), error.capture)
    assert(error.getValue.isInstanceOf[SAXException])
    verifyNoMoreInteractions(errorHandler, messageSender)
  }

  test("Message with no books") {
    fail("TODO")
  }

  test("Forwarding message fails with unrecoverable error") {
    // Should ack + write to error handler.
    fail("TODO")
  }

  test("Forwarding message fails with temporary error") {
    // Should schedule message to be retried.
    // Or go through cycle of retries followed by final success/failure?
    fail("TODO")
  }

  test("Acking message fails") {
    fail("TODO")
  }

  private def emailHandler = system.actorOf(Props(
    new EmailMessageHandler(bookDao, messageSender, errorHandler, routingId, templateName)))

}

object EmailMessageHandlerTests {

  val routingId = "test-routing-id"
  val templateName = "test-template"

  val BASE_ISBN = 122344566780L

  def isbn(id: Int) = 122344566780L + id
  def isbns(ids: Int*) = ids.map(isbn(_).toString).toList
  def bookList(ids: Seq[String]) = BookList(0, 0, 0, List()) // TODO
  def author(isbn: String) = "TODO" // TODO!

  def message(content: String) = Message("consumer-tag", null, null, content.getBytes("UTF-8"))

  /** Create a purchase complete message based on a template. */
  def testMessage(numBooks: Int, numBillingProviders: Int,
    includeClubcardFields: Boolean = true) =
    <p:purchase xmlns:p="http://schemas.blinkbox.com/books/purchasing/v1">
      <userId>101</userId>
      <firstName>FirstName</firstName>
      <lastName>LastName</lastName>
      <email>email@blinkbox.com</email>
      <basketId>1001</basketId>
      <deviceId>9999</deviceId>
      {
        if (includeClubcardFields) {
          <clubcardNumber>1234567890123451</clubcardNumber>
          <clubcardPointsAward>100</clubcardPointsAward>
        }
      }
      <transactionDate>2013-10-15T13:32:51Z</transactionDate>
      <totalPrice>
        <amount>12.0</amount>
        <currency>GBP</currency>
      </totalPrice>
      <billingProviders>
        {
          for (providerNum <- 1 to numBillingProviders)
            yield <billingProvider>
                    <name>{ "billing-provider-" + providerNum }</name>
                    <region>UK</region>
                    <payment>
                      <amount>{ 12.0 / numBillingProviders }</amount>
                      <currency>GBP</currency>
                    </payment>
                  </billingProvider>
        }
      </billingProviders>
      <basketItems>
        {
          for (bookNum <- 1 to numBooks)
            yield <basketItem>
                    <isbn>{ isbn(bookNum) }</isbn>
                    <publisherId>{ 100 + bookNum }</publisherId>
                    <publisherUsername>ftp_username</publisherUsername>
                    <salePrice>
                      <amount>{ 12.0 / numBooks }</amount>
                      <currency>GBP</currency>
                    </salePrice>
                    <listPrice>
                      <amount>{ 15.0 / numBooks }</amount>
                      <currency>GBP</currency>
                    </listPrice>
                  </basketItem>
        }
      </basketItems>
    </p:purchase>

  def expectedEmailMessage(numBooks: Int, numBillingProviders: Int, clubcardPoints: Boolean = true) = {
    val isbns = (1 to numBooks).map(_.toString)

    // NOTE: The email messages only support a single book at the moment - so that's what we have to provide.
    val book = bookList(isbns).items(0)

    // TODO: attributes
    <sendEmail r:messageId="receipt-76-424056" r:instance="{routingId}" r:originator="bookStore" xmlns="http://schemas.blinkbox.com/books/emails/sending/v1" xmlns:r="http://schemas.blinkbox.com/books/routing/v1">
      <template>{ templateName }</template>
      <to>
        <recipient>
          <name>First</name>
          <email>email@blinkbox.com</email>
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
          <value>{ author(book.id) } </value>
        </templateVariable>
        <templateVariable>
          <key>price</key>
          <value>12.0</value>
        </templateVariable>
      </templateVariables>
    </sendEmail>
  }

}