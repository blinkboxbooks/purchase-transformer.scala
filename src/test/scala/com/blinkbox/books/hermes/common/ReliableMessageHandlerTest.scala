package com.blinkbox.books.hermes.common

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Props
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import com.blinkboxbooks.hermes.rabbitmq.Message
import java.io.IOException
import java.util.concurrent.TimeoutException
import org.junit.runner.RunWith
import org.mockito.Matchers._
import org.mockito.Matchers.{ eq => matcherEq }
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuiteLike
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import scala.concurrent.duration._
import scala.concurrent.Future

@RunWith(classOf[JUnitRunner])
class ReliableMessageHandlerTest extends TestKit(ActorSystem("test-system")) with ImplicitSender
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

    handler = messageHandler
  }

  class TestMessageHandler(output: MessageSender, errorHandler: ErrorHandler, retryInterval: FiniteDuration)
    extends ReliableMessageHandler(output, errorHandler, retryInterval) {

    override def handleMessage(message: Message, originalSender: ActorRef): Future[Unit] = ??? // TODO!!!

    override protected def isTemporaryFailure(e: Throwable) =
      e.isInstanceOf[IOException] || e.isInstanceOf[TimeoutException]

  }

  private def messageHandler = system.actorOf(Props(
    new TestMessageHandler(messageSender, errorHandler, retryInterval)))

  test("Handle valid message") {
    fail("TODO")
  }

  test("Handle temporary failure") {
    fail("TODO")
  }

  test("Handle unrecoverable failure") {
    fail("TODO")
  }

  test("Handle failure to record unrecoverable failure") {
    fail("TODO")
  }

}
