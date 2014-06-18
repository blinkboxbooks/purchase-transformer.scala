package com.blinkbox.books.purchasetransformer

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.actor.Status.{ Success, Failure }
import akka.testkit.{ ImplicitSender, TestKit }
import com.blinkboxbooks.hermes.rabbitmq.Message
import com.blinkbox.books.hermes.common.MessageSender
import com.blinkbox.books.hermes.common.ErrorHandler
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
  private var messageSender: MessageSender = _

  private var handler: ActorRef = _

  val retryInterval = 100.millis

  before {
    messageSender = mock[MessageSender]
    doReturn(Future.successful(())).when(messageSender).send(any[Message])
    errorHandler = mock[ErrorHandler]
    doReturn(Future.successful(())).when(errorHandler).handleError(any[Message], any[Throwable])

    handler = system.actorOf(Props(
      new ClubcardMessageHandler(messageSender, errorHandler, retryInterval)))
  }

  //
  // Happy path.
  //

  test("Send message with clubcard points") {
    within(2009999.millis) {
      handler ! message(testMessage(2, 2, true).toString)
      expectMsgType[Success]

      checkSentMessage(messageSender, expectedClubcardMessage)
      verifyNoMoreInteractions(errorHandler, messageSender)
    }
  }

  ignore("Send message without clubcard points") {
    within(200.millis) {
      handler ! message(testMessage(1, 1, false).toString)
      expectMsgType[Success]

      // Should not send any message if the purchase had no clubcard points.
      verifyNoMoreInteractions(errorHandler, messageSender)
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

  ignore("non-well-formed XML input") {
    val msg = message("Not valid XML")

    within(500.millis) {
      handler ! msg

      expectMsgType[Success]
      verify(errorHandler).handleError(matcherEq(msg), any[SAXException])
      verifyNoMoreInteractions(errorHandler, messageSender)
    }
  }

  ignore("Well formed XML that can't be converted OK") {
    // Not sure if we can do this, given no schema for the input nor output?
    val msg = message("<not><the><right>XML</right></the></not>")

    within(500.millis) {
      handler ! msg

      expectMsgType[Success]
      verify(errorHandler).handleError(matcherEq(msg), any[IllegalArgumentException])
      verifyNoMoreInteractions(errorHandler, messageSender)
    }
  }

}
