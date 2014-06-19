package com.blinkbox.books.purchasetransformer

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.actor.Status.{ Success, Failure }
import akka.testkit.{ ImplicitSender, TestKit }
import com.blinkbox.books.messaging._
import java.io.IOException
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Matchers.{ eq => matcherEq }
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuiteLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.xml.sax.SAXException
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.xml.{ Elem, XML, Node }
import scala.xml.Utility.trim
import TestMessages._

/**
 * Tests that check the behaviour of the overall app, only mocking out RabbitMQ and external web services.
 */
@RunWith(classOf[JUnitRunner])
class EmailMessageHandlerTest extends TestKit(ActorSystem("test-system")) with ImplicitSender
  with FunSuiteLike with BeforeAndAfter with MockitoSugar {

  import EmailMessageHandlerTests._

  private var errorHandler: ErrorHandler = _
  private var eventPublisher: EventPublisher = _
  private var bookDao: BookDao = _

  private var handler: ActorRef = _

  val eventContext = EventContext("test")

  before {
    bookDao = mock[BookDao]
    eventPublisher = mock[EventPublisher]
    doReturn(Future.successful(())).when(eventPublisher).publish(any[Event])
    errorHandler = mock[ErrorHandler]
    doReturn(Future.successful(())).when(errorHandler).handleError(any[Event], any[Throwable])

    handler = system.actorOf(Props(
      new EmailMessageHandler(bookDao, eventPublisher, errorHandler, routingId, templateName, retryInterval)))
  }

  //
  // Happy path.
  //

  test("Send message with all optional fields populated") {
    val books = isbns(1, 2)
    doReturn(Future.successful(bookList(books))).when(bookDao).getBooks(books)

    within(500.millis) {
      handler ! Event(testMessage(2, 2, true).toString, eventContext)
      expectMsgType[Success]

      verify(bookDao).getBooks(books)
      checkPublishedEvent(eventPublisher, expectedEmailMessage(2, 2, true))

      verifyNoMoreInteractions(errorHandler, eventPublisher)
    }
  }

  test("Send message without optional fields") {
    val books = isbns(1)
    doReturn(Future.successful(bookList(books))).when(bookDao).getBooks(books)

    within(500.millis) {
      handler ! Event(testMessage(1, 1, false).toString, eventContext)
      expectMsgType[Success]

      checkPublishedEvent(eventPublisher, expectedEmailMessage(1, 1, false))
      verifyNoMoreInteractions(errorHandler, eventPublisher)
    }
  }

  test("Book with no authors") {
    val ids = isbns(1)
    val books = BookList(0, ids.size, ids.size,
      ids.map(isbn => Book(isbn, s"guid-$isbn", s"title-$isbn", TransactionDate, List())).toList)
    doReturn(Future.successful(books)).when(bookDao).getBooks(ids)

    within(500.millis) {
      handler ! Event(testMessage(1, 1, true).toString, eventContext)
      expectMsgType[Success]

      checkPublishedEvent(eventPublisher, expectedEmailMessage(1, 1, clubcardPoints = true, knownAuthor = false))
      verifyNoMoreInteractions(errorHandler, eventPublisher)
    }
  }

  //
  // Failure scenarios.
  //

  test("Non-well-formed XML input") {
    val msg = Event("Not valid XML", eventContext)

    within(500.millis) {
      handler ! msg
      expectMsgType[Success]

      verify(errorHandler).handleError(matcherEq(msg), any[SAXException])
      verifyNoMoreInteractions(errorHandler, eventPublisher)
    }
  }

  test("Well-formed XML that fails in conversion") {
    val msg = Event("<p:purchase><invalid>Not the expected content</invalid></p:purchase>", eventContext)

    within(500.millis) {
      handler ! msg
      expectMsgType[Success]

      checkRecordedError[IllegalArgumentException](msg)
      verifyNoMoreInteractions(errorHandler, eventPublisher)
    }
  }

  test("Event with no books") {
    doReturn(Future.successful(bookList(List()))).when(bookDao).getBooks(any[Seq[String]])

    val msg = Event(testMessage(0, 1, false).toString, eventContext)

    within(500.millis) {
      handler ! msg
      expectMsgType[Success]

      checkRecordedError[IllegalArgumentException](msg)
      verifyNoMoreInteractions(errorHandler, eventPublisher)
    }
  }

  // TODO: Ditch this, seeing as we only check the class of exception?
  private def checkRecordedError[T <: Throwable](msg: Event)(implicit manifest: Manifest[T]): Unit = {
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
  val authors = List("Author 1", "Author 2", "Author 3")

  // Some helper methods for test data.
  def author(isbn: String) = authors((isbn.toLong - BaseIsbn - 1).toInt)
  def authorLink(isbn: String) = Link("", "", None, author(isbn))
  def bookList(ids: Seq[String]) = BookList(0, ids.size, ids.size,
    ids.map(isbn => Book(isbn, s"guid-$isbn", s"title-$isbn", TransactionDate, List(authorLink(isbn)))).toList)

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
