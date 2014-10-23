package com.blinkbox.books.purchasetransformer

import akka.actor.Status.Success
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.blinkbox.books.messaging._
import com.blinkbox.books.purchasetransformer.TestMessages._
import org.junit.runner.RunWith
import org.mockito.Matchers.{eq => matcherEq, _}
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfter, FunSuiteLike}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.xml.sax.SAXException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.xml.Utility.trim

@RunWith(classOf[JUnitRunner])
class ClubcardMessageHandlerTest extends TestKit(ActorSystem("test-system")) with ImplicitSender
  with FunSuiteLike with BeforeAndAfter with MockitoSugar {

  private var errorHandler: ErrorHandler = _
  private var output: TestProbe = _

  private var handler: ActorRef = _

  val retryInterval = 100.millis
  val eventHeader = EventHeader("test")

  before {
    output = TestProbe()
    errorHandler = mock[ErrorHandler]
    doReturn(Future.successful(())).when(errorHandler).handleError(any[Event], any[Throwable])

    handler = system.actorOf(Props(
      new ClubcardMessageHandler(output.ref, errorHandler, retryInterval)))
  }

  //
  // Happy path.
  //

  test("Send message with clubcard points") {
    within(5000.millis) {
      // Send test message to actor. 
      handler ! Event.xml(testMessage(2, 2, true).toString, eventHeader)

      // Check the right output message was generated.
      checkPublishedEvent(output, expectedClubcardMessage)

      // Make the test probe that is the output send a Success notification back.
      output.send(output.lastSender, Success())

      // Check the success message is passed on.
      expectMsgType[Success]

      verifyNoMoreInteractions(errorHandler)
    }
  }

  test("Send message without clubcard points") {
    within(5000.millis) {
      handler ! Event.xml(testMessage(1, 1, false).toString, eventHeader)
      expectMsgType[Success]

      // Should not send any message if the purchase had no clubcard points.
      verifyNoMoreInteractions(errorHandler)
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
        <transactionValue>6.0</transactionValue>
      </ClubcardMessage>
    trim(xml)
  }

  //
  // Failure scenarios.
  //

  test("non-well-formed XML input") {
    val msg = Event.xml("Not valid XML", eventHeader)

    within(5000.millis) {
      handler ! msg

      expectMsgType[Success]
      verify(errorHandler).handleError(matcherEq(msg), any[SAXException])
      verifyNoMoreInteractions(errorHandler)
    }
  }

  test("Well formed XML that can't be converted OK") {
    // Not sure if we can do this, given no schema for the input nor output?
    val msg = Event.xml("<not><the><right>XML</right></the></not>", eventHeader)

    within(5000.millis) {
      handler ! msg

      expectMsgType[Success]
      verify(errorHandler).handleError(matcherEq(msg), any[IllegalArgumentException])
      verifyNoMoreInteractions(errorHandler)
    }
  }

}
