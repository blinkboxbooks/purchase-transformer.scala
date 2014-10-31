package com.blinkbox.books.purchasetransformer

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.actor.Status.{ Success, Failure }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import com.blinkbox.books.messaging._
import java.io.IOException
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Matchers.{ eq => matcherEq }
import org.mockito.Mockito._
import org.scalatest.FlatSpec
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
class EmailMessageHandlerTest extends FlatSpec with MockitoSugar {

  import EmailMessageHandlerTests._

  val eventHeader = EventHeader("test")

  private class TestFixture extends TestKit(ActorSystem("test-system")) with ImplicitSender {

    val bookDao = mock[BookDao]
    val output = TestProbe()
    val errorHandler = mock[ErrorHandler]
    doReturn(Future.successful(())).when(errorHandler).handleError(any[Event], any[Throwable])

    val handler = system.actorOf(Props(
      new EmailMessageHandler(bookDao, output.ref, errorHandler, routingId, templateName, retryInterval)))

    def checkRecordedError[T <: Throwable](msg: Event)(implicit manifest: Manifest[T]): Unit = {
      val expectedExceptionClass = manifest.runtimeClass.asInstanceOf[Class[T]]
      val error = ArgumentCaptor.forClass(classOf[Exception])
      verify(errorHandler).handleError(matcherEq(msg), error.capture)
      assert(expectedExceptionClass.isAssignableFrom(error.getValue.getClass))
    }
  }

  //
  // Happy path.
  //

  "An email message handler" should "Send message with all optional fields populated" in new TestFixture {
    val books = isbns(1, 2)
    doReturn(Future.successful(bookList(books))).when(bookDao).getBooks(books)

    within(5000.millis) {
      handler ! Event.xml(testMessage(2, 2, true).toString, eventHeader)

      // Check the message that came out the other end.
      checkPublishedEvent(output, expectedEmailMessage(2, 2, true))

      // Make the test probe that is the output send a Success notification back.
      output.send(output.lastSender, Success(()))

      // Check that we passed on the right parameters to the book client.
      verify(bookDao).getBooks(books)

      // Check the result of the overall flow.
      expectMsgType[Success]

      // Should have seen no errors here.
      verifyNoMoreInteractions(errorHandler)
    }
  }

  it should "Send message without optional fields" in new TestFixture {
    val books = isbns(1)
    doReturn(Future.successful(bookList(books))).when(bookDao).getBooks(books)

    within(5000.millis) {
      handler ! Event.xml(testMessage(1, 1, false).toString, eventHeader)
      checkPublishedEvent(output, expectedEmailMessage(1, 1, false))

      output.send(output.lastSender, Success(()))
      expectMsgType[Success]

      verifyNoMoreInteractions(errorHandler)
    }
  }

  it should "handle a book with no authors" in new TestFixture {
    val ids = isbns(1)
    val books = BookList(0, ids.size, ids.size,
      ids.map(isbn => Book(isbn, s"guid-$isbn", s"title-$isbn", TransactionDate, List())).toList)
    doReturn(Future.successful(books)).when(bookDao).getBooks(ids)

    within(5000.millis) {
      handler ! Event.xml(testMessage(1, 1, true).toString, eventHeader)
      checkPublishedEvent(output, expectedEmailMessage(1, 1, clubcardPoints = true, knownAuthor = false))

      output.send(output.lastSender, Success(()))
      expectMsgType[Success]

      verifyNoMoreInteractions(errorHandler)
    }
  }

  //
  // Failure scenarios.
  //

  it should "pass on non-well-formed XML input to its error handler" in new TestFixture {
    val msg = Event.xml("Not valid XML", eventHeader)

    within(5000.millis) {
      handler ! msg
      expectMsgType[Success]

      verify(errorHandler).handleError(matcherEq(msg), any[SAXException])
      verifyNoMoreInteractions(errorHandler)
    }
  }

  it should "pass on well-formed XML that fails in conversion to error handler" in new TestFixture {
    val msg = Event.xml("<p:purchase><invalid>Not the expected content</invalid></p:purchase>", eventHeader)

    within(5000.millis) {
      handler ! msg
      expectMsgType[Success]

      checkRecordedError[IllegalArgumentException](msg)
      verifyNoMoreInteractions(errorHandler)
    }
  }

  it should "handle event with no books as an error" in new TestFixture {
    doReturn(Future.successful(bookList(List()))).when(bookDao).getBooks(any[Seq[String]])

    val msg = Event.xml(testMessage(0, 1, false).toString, eventHeader)

    within(5000.millis) {
      handler ! msg
      expectMsgType[Success]

      checkRecordedError[IllegalArgumentException](msg)
      verifyNoMoreInteractions(errorHandler)
    }
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
