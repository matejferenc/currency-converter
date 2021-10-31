package com.ematiq.api

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.ematiq.ExchangeApp.{actorSystem, executionContext}
import com.ematiq.api.model.CurrConvResponse
import com.ematiq.domain.RateExtractionQuery
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol

import java.time.format.DateTimeFormatter
import scala.concurrent.Future
import scala.util.{Failure, Success}

object model {
  type CurrConvResponse = Map[String, Map[String, BigDecimal]]
}

trait CurrConvJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {}

object CurrConvExtractor extends Extractor[CurrConvResponse] with CurrConvJsonSupport {

  val logger: Logger = LoggerFactory.getLogger(CurrConvExtractor.getClass)

  private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  protected override def createUri(query: RateExtractionQuery): Uri = {
    val dateString = query.date.format(dateFormat)
    Uri("https://free.currconv.com")
      .withPath(Path("/api/v7/convert"))
      .withQuery(Query(Map(
        "q" -> s"${query.from}_${query.to}",
        "date" -> dateString,
        "compact" -> "ultra",
        "apiKey" -> "f97926a46587d2c3b6dc"
      )))
  }

  protected override def sendRequest(request: HttpRequest): Future[CurrConvResponse] =
    Http().singleRequest(request).transformWith {
      case Success(response) if response.status == StatusCodes.OK =>
        logger.info(s"Success from ${request.uri}")
        Unmarshal(response.entity).to[CurrConvResponse]
      case Success(response) =>
        logger.info(s"Failed for ${request.uri}")
        response.discardEntityBytes()
        throw new Exception(s"HttpRequest failed: $response")
      case Failure(throwable) =>
        logger.info(s"Failed for ${request.uri}")
        throw throwable
    }

  protected override def extractRate(query: RateExtractionQuery)(currConvResponse: CurrConvResponse): BigDecimal = {
    val currencyPair = s"${query.from}_${query.to}"
    val dateString = query.date.format(dateFormat)
    currConvResponse
      .getOrElse(currencyPair, throw new Exception(s"Currency pair $currencyPair was not found in response"))
      .getOrElse(dateString, throw new Exception(s"Date $dateString was not found in response"))
  }

}
