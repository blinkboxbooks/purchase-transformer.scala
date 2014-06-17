package com.blinkbox.books.purchasetransformer

import scala.util.{ Try, Success, Failure }
import scala.xml.{ XML, Node }
import com.blinkbox.books.hermes.common.XmlUtils._

// TODO: Use JodaMoney for money values?
case class Price(amount: BigDecimal, currency: String)

case class BasketItem(
  id: String,
  isbn: String,
  salePrice: Price,
  listPrice: Price)

case class Purchase(
  userId: String,
  firstName: String,
  lastName: String,
  email: String,
  clubcardNumber: Option[String],
  clubcardPointsAward: Option[Int],
  totalPrice: Price,
  basketItems: Seq[BasketItem]) {
  require(basketItems.size > 0, "No books given")
}

