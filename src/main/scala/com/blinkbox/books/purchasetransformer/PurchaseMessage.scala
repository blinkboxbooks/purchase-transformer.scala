package com.blinkbox.books.purchasetransformer

import scala.util.{ Try, Success, Failure }
import scala.xml.{ XML, Node }
import com.blinkbox.books.hermes.common.XmlUtils._

// TODO: Use JodaMoney for money values?
case class Price(amount: BigDecimal, currency: String)

case class BasketItem(
  isbn: String,
  salePrice: Price,
  listPrice: Price)

case class Purchase(
  firstName: String,
  lastName: String,
  email: String,
  clubcardNumber: String,
  clubcardPointsAward: Int,
  totalPrice: Price,
  basketItems: Seq[BasketItem]) {
  require(basketItems.size > 0, "No books given")
}

