package com.blinkbox.books.purchasetransformer

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.actor.Status.{ Success, Failure }
import akka.testkit.{ ImplicitSender, TestKit }
import com.blinkbox.books.messaging._
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Matchers.{ eq => matcherEq }
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.scalatest.FunSuiteLike
import org.xml.sax.SAXException
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.xml.Utility.trim
import TestMessages._

@RunWith(classOf[JUnitRunner])
class ClubcardMessageHandlerTest extends TestKit(ActorSystem("test-system")) with ImplicitSender
  with FunSuiteLike with BeforeAndAfter with MockitoSugar {

  private var errorHandler: ErrorHandler = _
  private var eventPublisher: EventPublisher = _

  private var handler: ActorRef = _

  val retryInterval = 100.millis
  val eventContext = EventContext("test")

  before {
    eventPublisher = mock[EventPublisher]
    doReturn(Future.successful(())).when(eventPublisher).publish(any[Event])
    errorHandler = mock[ErrorHandler]
    doReturn(Future.successful(())).when(errorHandler).handleError(any[Event], any[Throwable])

    handler = system.actorOf(Props(
      new ClubcardMessageHandler(eventPublisher, errorHandler, retryInterval)))
  }

  //
  // Happy path.
  //

  test("Send message with clubcard points") {
    within(2009999.millis) {
      handler ! Event(testMessage(2, 2, true).toString, eventContext)
      expectMsgType[Success]

      checkPublishedEvent(eventPublisher, expectedClubcardMessage)
      verifyNoMoreInteractions(errorHandler, eventPublisher)
    }
  }

  test("Send message without clubcard points") {
    within(200.millis) {
      handler ! Event(testMessage(1, 1, false).toString, eventContext)
      expectMsgType[Success]

      // Should not send any message if the purchase had no clubcard points.
      verifyNoMoreInteractions(errorHandler, eventPublisher)
    }
  }

  private def expectedClubcardMessage = {
    val xml =
      <ClubcardMessage v:version="1.0" r:originator="purchasing-service" xmlns="http://schemas.blinkboxbooks.com/events/clubcard/v1" xmlns:r="http://schemas.blinkboxbooks.com/messaging/routing/v1" xmlns:v="http://schemas.blinkboxbooks.com/messaging/versioning">
        <userId>{ UserId }</userId>
        <clubcardNumber>{ ClubcardNumber }</clubcardNumber>
        <points>{ ClubcardPoints }</points>
        <transactions>{ isbns(1) }</transactions>
        <transactionDate>{ TransactionDate }</transactionDate>
        <reason>Purchased basket #{ BasketId }</reason>
      </ClubcardMessage>
    trim(xml)
  }

  //
  // Failure scenarios.
  //

  test("non-well-formed XML input") {
    val msg = Event("Not valid XML", eventContext)

    within(500.millis) {
      handler ! msg

      expectMsgType[Success]
      verify(errorHandler).handleError(matcherEq(msg), any[SAXException])
      verifyNoMoreInteractions(errorHandler, eventPublisher)
    }
  }

  test("Well formed XML that can't be converted OK") {
    // Not sure if we can do this, given no schema for the input nor output?
    val msg = Event("<not><the><right>XML</right></the></not>", eventContext)

    within(500.millis) {
      handler ! msg

      expectMsgType[Success]
      verify(errorHandler).handleError(matcherEq(msg), any[IllegalArgumentException])
      verifyNoMoreInteractions(errorHandler, eventPublisher)
    }
  }

}
