package com.blinkbox.books.purchasetransformer

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

/**
 * Tests that check the behaviour of the overall app, only mocking out RabbitMQ and external web services.
 */
@RunWith(classOf[JUnitRunner])
class ClubcardMessageHandlerTest extends TestKit(ActorSystem("test-system")) with ImplicitSender
  with FunSuite with BeforeAndAfter with MockitoSugar {

  // TODO: Consider which of these test cases to pull out into tests of a common
  // actor class for message handlers (most of them!), and which test cases to leave here.

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

  test("XML that's not valid against schema") {
    fail("TODO")
  }

  test("XML that passes schema validation but can't be converted to expected object") {
    fail("TODO")
  }

  test("Forwarding message fails with unrecoverable error") {
    // Should ack + write to error handler.
    fail("TODO")
  }

  test("Forwarding message fails with temporary error") {
    // Should schedule message to be retried.
    // Or go through cycle of retries followed by final success/failure?
    fail("TODO")
  }

  test("Acking message fails") {
    fail("TODO")
  }

}
