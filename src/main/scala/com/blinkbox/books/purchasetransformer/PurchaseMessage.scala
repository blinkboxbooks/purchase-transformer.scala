package com.blinkbox.books.purchasetransformer

import scala.util.{ Try, Success, Failure }
import scala.xml.{ XML, Node }
import com.blinkbox.books.hermes.common.XmlUtils._

// TODO: Use JodaMoney for money values?
case class Price(amount: BigDecimal, currency: String)

case class BasketItem(
  isbn: String,
  publisherId: Int,
  publisherUserName: String,
  salePrice: Price,
  listPrice: Price)

case class BillingProvider(
  name: String,
  region: String,
  transactionId: String,
  payment: Price)

case class PurchaseComplete(
  userId: Int,
  firstName: String,
  lastName: String,
  email: String,
  deviceId: Int,
  basketId: Int,
  clubcardNumber: String,
  clubcardPointsAward: Int,
  //transactionDateTime: DateTime, // TODO: Add JodaTime for this!
  totalPrice: Price,
  billingProviders: List[BillingProvider],
  basketItems: List[BasketItem])

