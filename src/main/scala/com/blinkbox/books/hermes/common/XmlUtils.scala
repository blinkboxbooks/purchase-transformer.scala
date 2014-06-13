package com.blinkbox.books.hermes.common

//
// NOTE!!!
//
// This code is copied and adapted from the reporting-server project,
// the code in com.blinkboxbooks.mimir.reporting.XmlUtils.scala.
// 
// The generally useful bits of this
// needs to go into a common library
// that can be reused across services that do messaging should be extracted
// and put in a common library.
// 

import scala.xml.NodeSeq
import java.text.{ DateFormat, SimpleDateFormat }
import java.util.Date
import javax.xml.validation.SchemaFactory
import javax.xml.transform.stream.StreamSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.transform.Source
import javax.xml.XMLConstants

import Common._

/**
 * Collection of utility functions for parsing XML messages from platform services.
 */
object XmlUtils {

  // TODO: Change these to use JodaTime.
  def messageTimestampFormat: DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

  def messageDateFormat: DateFormat = new SimpleDateFormat("yyyy-MM-dd")

  private def schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

  /** Parse timestamp according to format used in messages. */
  def parseDateTime(str: String): Date = messageTimestampFormat.parse(str)

  /** Parse date according to format used in messages. */
  def parseDate(dateStr: String) = messageDateFormat.parse(dateStr)

  /**
   * The resource for the schema will be looked up on the classpath. The directory
   * of the main schema will be used as the base directory for the validator, which means
   * that imported schemas in the same directory will be automatically picked up.
   *
   * @param schemaNames A list of schema names to use. Note, that the order of the names matters.
   * @return A validator for a combined schema
   */
  def validatorFor(schemaNames: String*) = {
    var inputs = Array[InputStream]()
    try {
      for (schemaName <- schemaNames) {
        inputs = inputs :+ getClass.getResourceAsStream(schemaName)
      }
      val sources: Array[Source] = inputs.map(new StreamSource(_))
      schemaFactory.newSchema(sources).newValidator()
    } finally {
      inputs.foreach(_.close())
    }
  }

  /**
   * @return a single String value if it exists.
   *
   * @throws MessageException if no value or more than one value exist.
   */
  def requiredValue(nodes: NodeSeq) = {
    if (nodes.size == 0) throw new MessageException("No matching element found")
    if (nodes.size > 1) throw new MessageException(
      s"Expected a single matching element for ${nodes(0).buildString(true)}, found ${nodes.size}")
    val text = nodes(0).text.trim()
    if (text.isEmpty) throw new MessageException(
      s"Empty value found at element ${nodes(0).buildString(true)}")
    text
  }

  /**
   * @return a String value if it exists, otherwise None.
   *
   * @throws MessageException if more than one value exist.
   */
  def optionalValue(nodes: NodeSeq): Option[String] = {
    if (nodes.size == 0) None
    else if (nodes.size > 1) throw new MessageException(
      s"Expected 0 or 1 matching elements for ${nodes(0).buildString(true)}, found ${nodes.size}")
    else
      Some(nodes(0).text.trim())
  }

  /**
   * @return an Int value if it exists, otherwise None.
   *
   * @throws MessageException if more than one value exist, or
   * an NumberFormatException if the found value can't be parsed.
   */
  def optionalInt(elem: NodeSeq): Option[Int] = {
    val optVal = optionalValue(elem)
    optVal match {
      case None => None
      case Some(value) => Some(Integer.parseInt(value))
    }
  }
}