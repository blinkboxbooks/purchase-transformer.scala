package com.blinkbox.books.purchasetransformer

import scala.util.{ Try, Success, Failure }
import scala.xml.{ XML, Node }
import com.blinkbox.books.hermes.common.XmlUtils._
import scala.xml.NodeSeq
import java.io.ByteArrayInputStream

case class Purchase(
  userId: String,
  basketId: String,
  firstName: String,
  lastName: String,
  email: String,
  clubcardNumber: Option[String],
  clubcardPointsAward: Option[Int],
  totalPrice: Price,
  basketItems: Seq[BasketItem]) {
  require(basketItems.size > 0, "No books given")
}

case class BasketItem(
  isbn: String,
  salePrice: Price,
  listPrice: Price)

case class Price(amount: BigDecimal, currency: String)

object Purchase {

  /**
   * Parse input message.
   */
  def fromXml(xml: Array[Byte]): Purchase = {
    val purchase = XML.load(new ByteArrayInputStream(xml))
    val basketId = purchase.value("basketId")
    val basketItems = for (basketItem <- purchase \ "basketItems" \ "basketItem")
      yield BasketItem(basketItem.value("isbn"),
      price(basketItem \ "salePrice"), price(basketItem \ "listPrice"))

    Purchase(purchase.value("userId"), basketId, purchase.value("firstName"),
      purchase.value("lastName"), purchase.value("email"),
      purchase.optionalValue("clubcardNumber"), purchase.optionalValue("clubcardPointsAward").map(_.toInt),
      price(purchase \ "totalPrice"), basketItems)
  }

  private def price(priceNode: NodeSeq) =
    Price(BigDecimal(priceNode.value("amount")), priceNode.value("currency"))

}
