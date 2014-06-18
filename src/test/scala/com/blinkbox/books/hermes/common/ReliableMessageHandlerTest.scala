package com.blinkbox.books.hermes.common

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.actor.Status.{ Success, Failure }
import akka.testkit.{ TestKit, ImplicitSender }
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
  private var mockHandler: Handler = _

  private var handler: ActorRef = _

  val message = Message("consumer-tag", null, null, "Test message".getBytes("UTF-8"))
  val retryInterval = 100.millis

  before {
    messageSender = mock[MessageSender]
    doReturn(Future.successful(())).when(messageSender).send(any[Message])
    errorHandler = mock[ErrorHandler]
    doReturn(Future.successful(())).when(errorHandler).handleError(any[Message], any[Throwable])
    mockHandler = mock[Handler]
    doReturn(Future.successful(())).when(mockHandler).handleMessage(any[Message], any[ActorRef])

    handler = messageHandler
  }

  trait Handler {
    def handleMessage(message: Message, originalSender: ActorRef): Future[Unit]
  }

  // A concrete message handler class for testing.
  private class TestMessageHandler(output: MessageSender, errorHandler: ErrorHandler, retryInterval: FiniteDuration)
    extends ReliableMessageHandler(output, errorHandler, retryInterval) {

    // Pass on invocations to a mock so we can instrument responses and check invocations.
    override def handleMessage(message: Message, originalSender: ActorRef): Future[Unit] =
      mockHandler.handleMessage(message, originalSender)

    // For the tests, consider IOExceptions temporary and all other exceptions unrecoverable.
    override protected def isTemporaryFailure(e: Throwable) = e.isInstanceOf[IOException]

  }

  private def messageHandler = system.actorOf(Props(
    new TestMessageHandler(messageSender, errorHandler, retryInterval)))

  test("Handle valid message") {
    within(200.millis) {
      handler ! message

      expectMsgType[Success]
      verify(mockHandler).handleMessage(message, self)
      verifyNoMoreInteractions(mockHandler, errorHandler)
    }
  }

  test("Handle temporary failure, check recovery") {
    // Make implementation fail N times then succeed.
    // Fail twice then succeed.
    val temporaryError = new IOException("Test exception")
    when(mockHandler.handleMessage(any[Message], any[ActorRef]))
      .thenReturn(Future.failed(temporaryError))
      .thenReturn(Future.failed(temporaryError))
      .thenReturn(Future.successful(()))

    within(retryInterval * 3 + 200.millis) {
      handler ! message

      // Should retry 3 times, succeeding on the last attempt.
      expectMsgType[Success]
      verify(mockHandler, times(3)).handleMessage(message, self)
      verifyNoMoreInteractions(mockHandler, errorHandler)
    }
  }

  test("Handle unrecoverable failure") {
    val unrecoverableError = new Exception("Test exception")
    when(mockHandler.handleMessage(any[Message], any[ActorRef]))
      .thenReturn(Future.failed(unrecoverableError))

    within(200.millis) {
      handler ! message

      // Unrecoverable error should be dealt with by error handler,
      // still returning successful status for processing of the message.
      expectMsgType[Success]
      verify(mockHandler).handleMessage(message, self)
      verify(errorHandler).handleError(message, unrecoverableError)
      verifyNoMoreInteractions(mockHandler, errorHandler)
    }
  }

  test("Handle failure to record unrecoverable failure") {
    val unrecoverableError = new Exception("Test exception")
    when(mockHandler.handleMessage(any[Message], any[ActorRef]))
      .thenReturn(Future.failed(unrecoverableError))

    val ex = new Exception("Test exception from error handler")

    // Make error handler fail twice then recover.
    when(errorHandler.handleError(any[Message], any[Throwable]))
      .thenReturn(Future.failed(ex))
      .thenReturn(Future.failed(ex))
      .thenReturn(Future.successful(()))

    within(retryInterval * 3 + 500.millis) {
      handler ! message

      // In case of failure to record an error, retry processing the
      // message from the start.
      expectMsgType[Success]
      verify(mockHandler, times(3)).handleMessage(message, self)
      verify(errorHandler, times(3)).handleError(message, unrecoverableError)
      verifyNoMoreInteractions(mockHandler, errorHandler)
    }
  }

}
