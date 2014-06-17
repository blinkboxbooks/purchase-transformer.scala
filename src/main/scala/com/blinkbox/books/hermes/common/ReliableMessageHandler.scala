package com.blinkbox.books.hermes.common

import akka.actor.{ Actor, ActorRef, ActorLogging, Status }
import com.blinkboxbooks.hermes.rabbitmq.Message
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import scala.util.{ Success, Failure }

/**
 * Common base class for message-driven actors, i.e. actors that:
 *
 * - Receive messages from a message queue or other reliable source of messages (message can
 * be NACKed or thrown away without ACKing without loss of data).
 * - Performs processing on incoming message, e.g. enrichment with data from other services.
 * - Forwards results to some output which may fail.
 *
 * This class implements the handling of various errors that may occur. Specifically, it will:
 *
 * - Retry processing when a temporary failure occurs.
 * - Pass the incoming message to an error handler when an unrecoverable failure occurs for this message.
 * - Acknowledge the message when processing has completed, whether it results in successful output or
 *   forwarding to the error handler.
 *
 * Concrete implementations are responsible for:
 *
 * - Performing any processing steps for each message, including performing any side-effecting operations
 * such as writing to output databases or similar.
 * - Deciding whether any errors are temporary or unrecoverable.
 *
 */
abstract class ReliableMessageHandler(output: MessageSender, errorHandler: ErrorHandler, retryInterval: FiniteDuration)
  extends Actor with ActorLogging {

  implicit val ec = context.dispatcher

  def receive = {
    case msg: Message =>
      val originalSender = sender
      val result = handleMessage(msg, originalSender)

      result.onComplete {
        case Success(_) => originalSender ! Status.Success("Sent message")
        case Failure(e) if isTemporaryFailure(e) => reschedule(msg, originalSender)
        case Failure(e) => handleUnrecoverableFailure(msg, e, originalSender)
      }

    case msg => log.warning(s"Unexpected message: $msg")
  }

  /**
   *  Override in concrete implementations. These should return a Future that indicates
   *  the succesful or otherwise result of processing the message.
   */
  protected def handleMessage(msg: Message, originalSender: ActorRef): Future[Unit]

  /** Override in concrete implementations to classify failures into temporary vs. unrecoverable. */
  protected def isTemporaryFailure(e: Throwable): Boolean

  // TODO: change to different Message class, and set outgoing headers correctly.
  /** Convert content to outgoing message type. */
  protected def outgoingMessage(inputMessage: Message, content: String) =
    Message("", inputMessage.envelope, inputMessage.properties, content.getBytes("UTF-8"))

  /**
   * Reschedule message to be retried after an interval. When re-sent, make sure
   * that the message still has the same sender as the original.
   */
  private def reschedule(msg: Any, originalSender: ActorRef) =
    context.system.scheduler.scheduleOnce(retryInterval, self, msg)(ec, originalSender)

  /**
   * An unrecoverable failure should be ACKed, i.e. we have successfully competed processing of it,
   * even if the result wasn't as desired. Hence this will send a Success message to the original sender.
   * The handling of the unrecoverable error is delegated to the error handler.
   *
   * If the error handler itself fails, this should cause a retry of the message again.
   */
  private def handleUnrecoverableFailure(msg: Message, e: Throwable, originalSender: ActorRef) = {
    log.error(s"Unable to process message: ${e.getMessage}\nInput message was: ${new String(msg.body)}", e)
    errorHandler.handleError(msg, e).onComplete {
      case scala.util.Success(_) => {
        log.info("Handled invalid message for later processing")
        originalSender ! akka.actor.Status.Success(e)
      }
      case scala.util.Failure(e) => {
        log.warning("Error handler failed to deal with error, rescheduling", e)
        reschedule(msg, originalSender)
      }
    }
  }

}

