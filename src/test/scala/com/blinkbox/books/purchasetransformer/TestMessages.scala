package com.blinkbox.books.purchasetransformer

import java.io.ByteArrayInputStream

import akka.testkit.TestProbe
import com.blinkbox.books.messaging._
import org.custommonkey.xmlunit.XMLAssert
import org.scalatest.Assertions._

import scala.concurrent.duration._
import scala.xml.Utility.trim
import scala.xml.{Node, XML}

/** Helper methods for creating "purchase complete" messages. */
object TestMessages {

  val BaseIsbn = 122344566780L
  val ClubcardNumber = "1234567890123451"
  val ClubcardPoints = 100
  val UserId = 101
  val BasketId = 1001
  val TransactionDate = "2013-10-15T13:32:51"

  def isbn(id: Int) = BaseIsbn + id
  def isbns(ids: Int*) = ids.map(isbn(_).toString).toList

  /** Create a purchase complete message based on a template. */
  def testMessage(numBooks: Int, numBillingProviders: Int,
    includeClubcardFields: Boolean = true) =
    <p:purchase xmlns:p="http://schemas.blinkbox.com/books/purchasing/v1">
      <userId>{ UserId }</userId>
      <firstName>FirstName</firstName>
      <lastName>LastName</lastName>
      <email>email@blinkbox.com</email>
      <basketId>{ BasketId }</basketId>
      <deviceId>9999</deviceId>
      {
        if (includeClubcardFields) {
          <clubcardNumber>{ ClubcardNumber }</clubcardNumber>
          <clubcardPointsAward>{ ClubcardPoints }</clubcardPointsAward>
        }
      }
      <transactionDate>{ TransactionDate }</transactionDate>
      <totalPrice>
        <amount>12.0</amount>
        <currency>GBP</currency>
      </totalPrice>
      <billingProviders>
        {
          for (providerNum <- 1 to numBillingProviders)
            yield <billingProvider>
                    <name>{ if (providerNum == 1) "braintree" else "billing-provider-" + (providerNum - 1) }</name>
                    <region>UK</region>
                    <payment>
                      <amount>{ 12.0 / numBillingProviders }</amount>
                      <currency>GBP</currency>
                    </payment>
                  </billingProvider>
        }
      </billingProviders>
      <basketItems>
        {
          for (bookNum <- 1 to numBooks)
            yield <basketItem>
                    <isbn>{ isbn(bookNum) }</isbn>
                    <publisherId>{ 100 + bookNum }</publisherId>
                    <publisherUsername>ftp_username</publisherUsername>
                    <salePrice>
                      <amount>{ 12.0 / numBooks }</amount>
                      <currency>GBP</currency>
                    </salePrice>
                    <listPrice>
                      <amount>{ 15.0 / numBooks }</amount>
                      <currency>GBP</currency>
                    </listPrice>
                  </basketItem>
        }
      </basketItems>
    </p:purchase>

  /** Check content of event published to the given actor against expected XML data. */
  def checkPublishedEvent(output: TestProbe, expectedContent: Node) {
    val event = output.receiveOne(3.second).asInstanceOf[Event]

    assert(event.body.asString.contains("""<?xml version="1.0" encoding="UTF-8"?>"""), "Message should contain standard XML declaration")
    XMLAssert.assertXMLEqual(normalisedXml(event.body.asString).toString, expectedContent.toString)
  }

  private def normalisedXml(str: String) = trim(XML.load(new ByteArrayInputStream(str.getBytes("UTF-8"))))

}
