package com.blinkbox.books.purchasetransformer

import com.blinkbox.books.messaging._
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.verify
import org.scalatest.Assertions._
import scala.xml.Node
import scala.xml.Utility.trim
import scala.xml.XML

object TestMessages {

  val BaseIsbn = 122344566780L
  val ClubcardNumber = "1234567890123451"
  val ClubcardPoints = 100
  val UserId = 101
  val BasketId = 1001
  val TransactionDate = "2013-10-15T13:32:51"

  def isbn(id: Int) = BaseIsbn + id
  def isbns(ids: Int*) = ids.map(isbn(_).toString).toList

  //  /** Create test message with given content. */
  //  def message(content: String) = Event("consumer-tag", null, null, content.getBytes("UTF-8"))

  /** Parse string into a normalised XML representation that's convenient for comparisons. */
  def xml(str: String) = trim(XML.loadString(str))

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
                    <name>{ "billing-provider-" + providerNum }</name>
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

  def checkPublishedEvent(publisher: EventPublisher, expectedContent: Node) {
    val argument = ArgumentCaptor.forClass(classOf[Event])
    verify(publisher).publish(argument.capture)
    val content = new String(argument.getValue.body, "UTF-8")

    // TODO: Horrible hack to get around weirdnesses in XML comparison in Scala.
    // Would be nice to come up with a general solution for this. Just comparing the strings picks up insignificant
    // differences e.g. orders of attributes. Comparing the XML elements directly avoids this but has other quirks
    // that means it throws up differences where it shoudln't in some cases.
    // (I recommend the comments in scala.xml.Equality for an impression of the issues involved - and a good laugh!).
    assert(xml(content) == expectedContent || xml(content).toString == expectedContent.toString)
  }

}
