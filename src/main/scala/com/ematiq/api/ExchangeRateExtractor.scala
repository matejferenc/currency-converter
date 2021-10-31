package com.ematiq.api

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.ematiq.ExchangeApp.{actorSystem, executionContext}
import com.ematiq.domain.RateExtractionQuery
import org.slf4j.{Logger, LoggerFactory}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.time.format.DateTimeFormatter
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class ExchangeRateResponse(base: String, rates: Map[String, BigDecimal])

trait ExchangeRateJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val itemFormat: RootJsonFormat[ExchangeRateResponse] = jsonFormat2(ExchangeRateResponse)
}

object ExchangeRateExtractor extends Extractor[ExchangeRateResponse] with ExchangeRateJsonSupport {

  val logger: Logger = LoggerFactory.getLogger(ExchangeRateExtractor.getClass)

  private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  protected override def createUri(query: RateExtractionQuery): Uri = {
    val dateString = query.date.format(dateFormat)
    Uri("https://api.exchangerate.host")
      .withPath(Path("/" + dateString))
      .withQuery(Query(Map(
        "base" -> query.from,
        "symbols" -> query.to
      )))
  }

  protected override def sendRequest(request: HttpRequest): Future[ExchangeRateResponse] =
    Http().singleRequest(request).transformWith {
      case Success(response) if response.status == StatusCodes.OK =>
        logger.info(s"Success from ${request.uri}")
        Unmarshal(response.entity).to[ExchangeRateResponse]
      case Success(response) =>
        logger.info(s"Failed for ${request.uri}")
        response.discardEntityBytes()
        throw new Exception(s"HttpRequest failed: $response")
      case Failure(throwable) =>
        logger.info(s"Failed for ${request.uri}")
        throw throwable
    }

  protected override def extractRate(query: RateExtractionQuery)(exchangeRateResponse: ExchangeRateResponse): BigDecimal = exchangeRateResponse match {
    case ExchangeRateResponse(query.`from`, rates) if rates.contains(query.to) =>
      rates(query.to)
    case ExchangeRateResponse(query.`from`, _) =>
      throw new Exception(s"Desired currency '${query.to}' was not found in the response")
    case ExchangeRateResponse(_, _) =>
      throw new Exception(s"Source currency '${query.from}' was not recognized")
  }

}
