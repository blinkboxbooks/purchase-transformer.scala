package com.blinkbox.books.purchasetransformer

import com.blinkbox.books.messaging.Xml._
import com.blinkbox.books.messaging.EventHeader
import java.io.ByteArrayInputStream
import scala.util.{ Try, Success, Failure }
import scala.xml.{ XML, Node }
import scala.xml.NodeSeq

// Code to convert incoming message to case classes.
// This kind of code should perhaps live in a separate library that can then be
// used by both the publisher(s) and consumers of the messages. Ideally, alongside a schema
// for the message.

/**
 * A Purchase Complete message as published by the payment service after customer has bought something.
 */
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
   * Convert input message to Purchase object.
   */
  def fromXml(xml: Array[Byte]): Purchase = {
    val purchase = XML.load(new ByteArrayInputStream(xml))
    val basketId = purchase.stringValue("basketId")
    val basketItems = for (basketItem <- purchase \ "basketItems" \ "basketItem")
      yield BasketItem(basketItem.stringValue("isbn"),
      price(basketItem \ "salePrice"), price(basketItem \ "listPrice"))

    Purchase(purchase.stringValue("userId"), basketId, purchase.stringValue("firstName"),
      purchase.stringValue("lastName"), purchase.stringValue("email"),
      purchase.stringValueOptional("clubcardNumber"), purchase.stringValueOptional("clubcardPointsAward").map(_.toInt),
      price(purchase \ "totalPrice"), basketItems)
  }

  private def price(priceNode: NodeSeq) =
    Price(BigDecimal(priceNode.stringValue("amount")), priceNode.stringValue("currency"))

  /** Get Event Context from fields of purchase message. */
  def context(purchase: Purchase) =
    EventHeader(originator = PurchaseTransformerService.Originator,
      userId = Some(purchase.userId), transactionId = Some(purchase.basketId))
}
