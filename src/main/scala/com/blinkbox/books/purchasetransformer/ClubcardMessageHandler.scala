package com.blinkbox.books.purchasetransformer

import akka.actor.ActorRef
import com.blinkbox.books.hermes.common.{ ErrorHandler, MessageSender, ReliableMessageHandler }
import com.blinkboxbooks.hermes.rabbitmq.Message
import java.io.{ IOException, StringReader, StringWriter }
import java.util.concurrent.TimeoutException
import javax.xml.transform.stream.{ StreamSource, StreamResult }
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.xml.XML

import Purchase._

/**
 * Actor that receives incoming purchase-complete messages
 * and passes on Clubcard messages.
 */
class ClubcardMessageHandler(output: MessageSender, errorHandler: ErrorHandler, retryInterval: FiniteDuration)
  extends ReliableMessageHandler(output, errorHandler, retryInterval) {

  // Use XSLT to transform the input and pass on the result to the output.
  override def handleMessage(message: Message, originalSender: ActorRef): Future[Unit] = Future {
    val content = new String(message.body, "UTF-8")
    val purchase = fromXml(message.body)
    if (purchase.clubcardPointsAward.isDefined) {
      log.debug(s"Sending email message for userUd ${purchase.userId}, basketId ${purchase.basketId}")
      output.send(outgoingMessage(message, transform(content)))
    } else {
      log.debug(s"Ignoring purchase message for userUd ${purchase.userId}, basketId ${purchase.basketId}, with no clubcard points awarded")
      Future.successful(())
    }
  }

  private def transform(input: String): String = {
    val stylesheet = getClass.getResourceAsStream("/clubcard.listener.xsl")
    try {
      val styleSource = new StreamSource(stylesheet)
      // Use Saxon instead of built-in XSLT support, for XSLT 2.0.
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

  // TODO: Check!
  override def isTemporaryFailure(e: Throwable) = e.isInstanceOf[IOException] || e.isInstanceOf[TimeoutException]

}

