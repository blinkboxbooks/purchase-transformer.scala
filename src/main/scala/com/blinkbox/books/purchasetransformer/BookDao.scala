package com.blinkbox.books.purchasetransformer

import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.{ Promise, Future, ExecutionContext }
import akka.util.Timeout
import akka.actor.ActorRef
import akka.pattern.ask
import org.json4s.jackson.JsonMethods._
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import spray.http.HttpResponse
import spray.http.StatusCodes
import spray.httpx.RequestBuilding._
import org.json4s.jvalue2extractable
import org.json4s.string2JsonInput

//
// NOTE! This client class for the book services is copied from the reporting service. 
// There's also a Java book client in its own library.
// We should ideally move to a new common Scala client lib... but if not, consider which other option to go for.
//

/** Data classes for books. */
case class BookList(offset: Int, count: Int, numberOfResults: Int, items: List[Book])
case class Book(id: String, guid: String, title: String, publicationDate: String, links: List[Link])
case class Link(rel: String, href: String, targetGuid: Option[String], title: String) {
  def isContributorLink = rel.startsWith(Link.CONTRIBUTOR_REL)
}

class BookException(msg: String, cause: Throwable) extends Exception(msg, cause) {
  def this(msg: String) = this(msg, null)
}

object Link {
  val CONTRIBUTOR_REL = "urn:blinkboxbooks:schema:contributor"
}

/**
 * Common interface for getting book information asynchronously.
 */
trait BookDao {

  /**
   * Get list of books with given IDs (ISBNs).
   */
  def getBooks(ids: Seq[String]): Future[BookList]

}

/**
 * Implementation of book DAO that gets book data from book service.
 */
class HttpBookDao(httpActor: ActorRef, baseUrl: String)(
  implicit val timeout: Timeout, implicit val ec: ExecutionContext)
  extends BookDao with Logging {

  import HttpBookDao._

  implicit val formats = Serialization.formats(NoTypeHints)

  override def getBooks(ids: Seq[String]): Future[BookList] = {
    if (ids.isEmpty) {
      return Promise.failed(new BookException("No book IDs given")).future
    }
    val request = Get(baseUrl + HttpBookDao.BOOK_SERVICE_URL + "?" + queryParams(ids))
    (httpActor ? request)
      .mapTo[HttpResponse]
      .transform(response =>
        convertBookResponse(response), wrapInBookException("Error getting book data"))
  }

  private def convertBookResponse(response: HttpResponse): BookList = response match {
    case HttpResponse(StatusCodes.OK, entity, _, _) =>
      parse(response.entity.asString).extract[BookList]
    case resp @ HttpResponse(status, entity, _, _) => {
      val msg = s"Unexpected HTTP response status: $status, content=${entity.asString take 1000} ..."
      logger.debug(msg)
      throw new BookException(msg)
    }
    case res @ _ => throw new BookException("Unexpected response from book service: " + res)
  }

  /** Helper function for generating request parameters. */
  private def queryParams(values: Seq[Any]): String =
    (values.map(isbn => "id=" + isbn)).mkString("&")

}

object HttpBookDao {

  def wrapInBookException(msg: String)(t: Throwable): BookException = t match {
    case e: BookException => e
    case t @ _ => new BookException(msg, t)
  }

  // TODO: This needs to be configurable.
  val BOOK_SERVICE_URL = "/service/catalogue/books"

}
