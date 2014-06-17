package com.blinkbox.books.purchasetransformer

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Status.Failure
import akka.actor.Status.Success
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import com.blinkbox.books.hermes.common.Common._
import com.blinkbox.books.hermes.common.ErrorHandler
import com.blinkbox.books.hermes.common.MessageSender
import com.blinkboxbooks.hermes.rabbitmq.Message
import java.io.IOException
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Matchers.{ eq => matcherEq }
import org.mockito.Mockito._
import org.scalatest.junit.JUnitRunner
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuiteLike
import org.scalatest.mock.MockitoSugar
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.xml.Elem
import scala.xml.XML
import scala.xml.Utility.trim
import org.xml.sax.SAXParseException
import scala.xml.Node
import scala.reflect.runtime.universe._

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
    bookDao = mock[BookDao]
    messageSender = mock[MessageSender]
    errorHandler = mock[ErrorHandler]
    doReturn(Future.successful(())).when(errorHandler).handleError(any[Message], any[Throwable])
    
    handler = emailHandler
  }

  //
  // Happy path.
  //

  test("Send message with all optional fields populated") {
    val books = isbns(1, 2)
    doReturn(Future.successful(bookList(books))).when(bookDao).getBooks(books)

    within(500.millis) {
      handler ! message(testMessage(2, 2, true).toString)
      expectMsgType[Success]
    }

    verify(bookDao).getBooks(books)
    checkSentMessage(expectedEmailMessage(2, 2, true))

    verifyNoMoreInteractions(errorHandler, messageSender)
  }

  test("Send message without optional fields") {
    val books = isbns(1)
    doReturn(Future.successful(bookList(books))).when(bookDao).getBooks(books)

    within(500.millis) {
      handler ! message(testMessage(1, 1, false).toString)
      expectMsgType[Success]
    }

    checkSentMessage(expectedEmailMessage(1, 1, false))
    verifyNoMoreInteractions(errorHandler, messageSender)
  }

  test("Book with no authors") {
    val ids = isbns(1)
    val books = BookList(0, ids.size, ids.size,
      ids.map(isbn => Book(isbn, s"guid-$isbn", s"title-$isbn", "TODO: dateStr", List())).toList)
    doReturn(Future.successful(books)).when(bookDao).getBooks(ids)

    within(500.millis) {
      handler ! message(testMessage(1, 1, true).toString)
      expectMsgType[Success]
    }

    checkSentMessage(expectedEmailMessage(1, 1, clubcardPoints = true, knownAuthor = false))
    verifyNoMoreInteractions(errorHandler, messageSender)
  }

  //
  // Failure scenarios.
  //

  test("non-well-formed XML input") {
    val msg = message("Not valid XML")

    within(500.millis) {
      handler ! msg
      expectMsgType[Success]
    }

    checkRecordedError[SAXParseException](msg)
    verifyNoMoreInteractions(errorHandler, messageSender)
  }

  test("Well-formed XML that fails in conversion") {
    val msg = message("<p:purchase><invalid>Not the expected content</invalid></p:purchase>")

    within(500.millis) {
      handler ! msg
      expectMsgType[Success]
    }

    checkRecordedError[IllegalArgumentException](msg)
    verifyNoMoreInteractions(errorHandler, messageSender)
  }

  test("Message with no books") {
    doReturn(Future.successful(bookList(List()))).when(bookDao).getBooks(any[Seq[String]])

    val msg = message(testMessage(0, 1, false).toString)

    within(500.millis) {
      handler ! msg
      expectMsgType[Success]
    }

    checkRecordedError[IllegalArgumentException](msg)
    verifyNoMoreInteractions(errorHandler, messageSender)
  }

  test("Process fails with temporary error, then recovers") {
    val books = isbns(1)
    val temporaryError = new IOException("Test temporary failure")

    // Fail twice then succeed.
    when(bookDao.getBooks(books))
      .thenReturn(Future.failed(temporaryError))
      .thenReturn(Future.failed(temporaryError))
      .thenReturn(Future.successful(bookList(books)))

    within(retryInterval * 3 + 500.millis) {
      handler ! message(testMessage(1, 2, true).toString)
      expectMsgType[Success]
    }

    checkSentMessage(expectedEmailMessage(1, 2, true))
    verifyNoMoreInteractions(errorHandler, messageSender)
  }

  test("Error handler failure to deal with message") {
    val books = isbns(1)
    val ex = new IOException("Test failure from error handler")

    doReturn(Future.successful(bookList(books))).when(bookDao).getBooks(books)

    // Make error handler fail twice then succeed.
    when(errorHandler.handleError(any[Message], any[Throwable]))
      .thenReturn(Future.failed(ex))
      .thenReturn(Future.failed(ex))
      .thenReturn(Future.successful(()))

    within(retryInterval * 3 + 500.millis) {
      handler ! message(testMessage(1, 2, true).toString)
      expectMsgType[Success]
    }

    checkSentMessage(expectedEmailMessage(1, 2, true))
    verifyNoMoreInteractions(errorHandler, messageSender)
  }

  private def emailHandler = system.actorOf(Props(
    new EmailMessageHandler(bookDao, messageSender, errorHandler, routingId, templateName, retryInterval)))

  private def checkSentMessage(expectedContent: Node) {
    val argument = ArgumentCaptor.forClass(classOf[Message])
    verify(messageSender).send(argument.capture)
    val content = new String(argument.getValue.body, "UTF-8")
    assert(xml(content) == expectedContent)
  }

  private def checkRecordedError[T <: Throwable](msg: Message)(implicit manifest: Manifest[T]): Unit = {
    val expectedExceptionClass = manifest.erasure.asInstanceOf[Class[T]]
    val error = ArgumentCaptor.forClass(classOf[Exception])
    verify(errorHandler).handleError(matcherEq(msg), error.capture)
    assert(expectedExceptionClass.isAssignableFrom(error.getValue.getClass))
  }

}

object EmailMessageHandlerTests {

  // Constants for test.
  val routingId = "test-routing-id"
  val templateName = "testTemplate"
  val retryInterval = 100.millis
  val BASE_ISBN = 122344566780L
  val authors = List("Author 1", "Author 2", "Author 3")

  // Some helper methods for test data.
  def isbn(id: Int) = 122344566780L + id
  def isbns(ids: Int*) = ids.map(isbn(_).toString).toList
  def author(isbn: String) = authors((isbn.toLong - BASE_ISBN - 1).toInt)
  def authorLink(isbn: String) = Link("", "", None, author(isbn))
  def bookList(ids: Seq[String]) = BookList(0, ids.size, ids.size,
    ids.map(isbn => Book(isbn, s"guid-$isbn", s"title-$isbn", "TODO: dateStr", List(authorLink(isbn)))).toList)

  def message(content: String) = Message("consumer-tag", null, null, content.getBytes("UTF-8"))

  def xml(str: String) = trim(XML.loadString(str))

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

  def expectedEmailMessage(numBooks: Int, numBillingProviders: Int, clubcardPoints: Boolean = true, knownAuthor: Boolean = true) = {
    val isbns = (1 to numBooks).map(isbn(_).toString)

    // NOTE: The email messages only support a single book at the moment - so that's what we have to provide.
    val book = bookList(isbns).items(0)
    val xml =
      <sendEmail r:messageId="testTemplate-101-1001" r:instance={ routingId } r:originator="bookStore" xmlns="http://schemas.blinkbox.com/books/emails/sending/v1" xmlns:r="http://schemas.blinkbox.com/books/routing/v1">
        <template>{ templateName }</template>
        <to>
          <recipient>
            <name>FirstName</name>
            <email>email@blinkbox.com</email>
          </recipient>
        </to>
        <templateVariables>
          <templateVariable>
            <key>salutation</key>
            <value>FirstName</value>
          </templateVariable>
          <templateVariable>
            <key>bookTitle</key>
            <value>{ book.title }</value>
          </templateVariable>
          <templateVariable>
            <key>author</key>
            <value>{ if (knownAuthor) author(book.id) else "" }</value>
          </templateVariable>
          <templateVariable>
            <key>price</key>
            <value>{ (12.0 / isbns.size).toString }</value>
          </templateVariable>
        </templateVariables>
      </sendEmail>

    trim(xml)
  }

}