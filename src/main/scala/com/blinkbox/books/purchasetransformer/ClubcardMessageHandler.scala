package com.blinkbox.books.purchasetransformer

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.blinkbox.books.messaging._
import java.io.{ IOException, StringReader, StringWriter }
import java.util.concurrent.TimeoutException
import javax.xml.transform.stream.{ StreamSource, StreamResult }
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.annotation.tailrec
import scala.util.{ Try, Success }
import scala.xml.XML

/**
 * Actor that receives incoming purchase-complete messages
 * and passes on Clubcard messages.
 */
class ClubcardMessageHandler(output: ActorRef, errorHandler: ErrorHandler, retryInterval: FiniteDuration)
  extends ReliableEventHandler(errorHandler, retryInterval) {

  private implicit val timeout = Timeout(retryInterval)

  // Use XSLT to transform the input and pass on the result to the output.
  override def handleEvent(event: Event, originalSender: ActorRef) = {
    val purchase = Purchase.fromXml(event.body.content)
    val eventContext = Purchase.context(purchase);
    if (purchase.clubcardPointsAward.isDefined) {
      log.debug(s"Sending email message for userUd ${purchase.userId}, basketId ${purchase.basketId}")
      (output ? Event.xml(transform(event.body.asString), eventContext)).map(res => ())
    } else {
      log.debug(s"Ignoring purchase message for userUd ${purchase.userId}, basketId ${purchase.basketId}, with no clubcard points awarded")
      Future.successful(())
    }
  }

  private def transform(input: String): String = {
    val stylesheet = getClass.getResourceAsStream("/clubcard.listener.xsl")
    try {
      val styleSource = new StreamSource(stylesheet)
      // Use Saxon instead of native Java XSLT support, for XSLT 2.0.
      val transformer = new net.sf.saxon.TransformerFactoryImpl().newTransformer(styleSource)
      val inputSource = new StreamSource(new StringReader(input))
      val writer = new StringWriter()
      val result = new StreamResult(writer)
      transformer.transform(inputSource, result)
      writer.toString
    } finally {
      stylesheet.close()
    }
  }

  // Consider the error temporary if the exception or its root cause is an IO exception or timeout.
  @tailrec
  final override def isTemporaryFailure(e: Throwable) =
    e.isInstanceOf[IOException] || e.isInstanceOf[TimeoutException] ||
      Option(e.getCause).isDefined && isTemporaryFailure(e.getCause)

}
