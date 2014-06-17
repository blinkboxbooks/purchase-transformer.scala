package com.blinkbox.books.hermes.common

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import com.blinkbox.books.hermes.common.XmlUtils.NodeSeqWrapper

@RunWith(classOf[JUnitRunner])
class XmlUtilsTest extends FunSuite {

  val root =
    <root>
      <element>
        42
      </element>
      <nested>
        <element>
          42
        </element>
      </nested>
    </root>

  test("Get required value when it exists") {
    assert(root.value("element") == "42")
    assert((root \ "nested").value("element") == "42")
  }

  test("Get required value when it doesn't exist") {
    val ex = intercept[IllegalArgumentException] { root.value("nonExistent") }
    assert(ex.getMessage.contains("nonExistent")
      && ex.getMessage.contains("root"))
    intercept[IllegalArgumentException] { (root \ "nested").value("nonExistent") }
    assert(ex.getMessage.contains("nonExistent")
      && ex.getMessage.contains("nested"))
  }

  test("Get optional value when it exists") {
    assert(root.optionalValue("element") == Some("42"))
    assert((root \ "nested").optionalValue("element") == Some("42"))
  }

  test("Get optional value when it doesn't exist") {
    assert(root.optionalValue("nonExistent") == None)
  }

}
