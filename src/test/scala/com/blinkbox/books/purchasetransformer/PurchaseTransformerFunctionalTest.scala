package com.blinkbox.books.purchasetransformer

import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

/**
 * Tests that check the behaviour of the overall app, only mocking out RabbitMQ and external web services.
 */
@RunWith(classOf[JUnitRunner])
class PurchaseTransformerFunctionalTest extends FunSuite with BeforeAndAfter with MockitoSugar {

  test("Email message flow") {
    fail("TODO")
  }

  test("Clubcard message flow") {
    fail("TODO")
  }

}
