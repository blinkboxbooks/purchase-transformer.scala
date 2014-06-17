package com.blinkbox.books.purchasetransformer

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.scalatest.FunSuiteLike

@RunWith(classOf[JUnitRunner])
class ClubcardMessageHandlerTest extends TestKit(ActorSystem("test-system")) with ImplicitSender
  with FunSuiteLike with BeforeAndAfter with MockitoSugar {

  //
  // Happy path.
  //

  test("Send message with all optional fields populated") {
    fail("TODO")
  }

  test("Send message without optional fields") {
    fail("TODO")
  }

  //
  // Failure scenarios.
  //

  test("non-well-formed XML input") {
    fail("TODO")
  }

  test("Well formed XML that can't be converted to expected object") {
    fail("TODO")
  }

  test("Forwarding message fails with unrecoverable error") {
    // Should ack + write to error handler.
    fail("TODO")
  }

}
