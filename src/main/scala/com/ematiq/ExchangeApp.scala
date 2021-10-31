package com.ematiq

import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.ematiq.CurrencyConverter.{CurrencyConversionFailure, CurrencyConversionQuery, CurrencyConversionResponse, CurrencyConversionSuccess}
import com.ematiq.domain.{Bet, RateExtractionQuery}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.math.BigDecimal.RoundingMode
import scala.util.{Failure, Success}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val localDateTimeFormat: RootJsonFormat[LocalDateTime] = new RootJsonFormat[LocalDateTime] {
    private val iso_date_time = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    def write(x: LocalDateTime): JsString = JsString(iso_date_time.format(x))
    def read(value: JsValue): LocalDateTime = value match {
      case JsString(x) => LocalDateTime.parse(x, iso_date_time)
      case x => throw new RuntimeException(s"Unexpected type ${x.getClass.getName} when trying to parse LocalDateTime")
    }
  }
  implicit val itemFormat: RootJsonFormat[Bet] = jsonFormat6(Bet)
}

object ExchangeApp extends App with JsonSupport {

  implicit val actorSystem: ActorSystem[CurrencyConversionQuery] = ActorSystem(CurrencyConverter(), "CurrencyConverter")
  val currencyConverter: ActorRef[CurrencyConversionQuery] = actorSystem
  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext
  implicit val timeout: Timeout = Timeout(5.seconds)
  val desiredCurrency = "EUR"
  
  private def convertStake(stake: BigDecimal, rate: BigDecimal) = {
    (stake * rate).setScale(5, RoundingMode.HALF_UP)
  }

  lazy val route: Route = pathPrefix("api") {
    path("v1" / "conversion" / "trade") {
      post {
        entity(as[Bet]) { bet =>
          val result: Future[CurrencyConversionResponse] = currencyConverter.ask(ref =>
            CurrencyConversionQuery(RateExtractionQuery(bet.currency, desiredCurrency, bet.date.toLocalDate), ref))
          onComplete(result) {
            case Success(CurrencyConversionSuccess(rate)) => complete(bet.copy(currency = desiredCurrency, stake = convertStake(bet.stake, rate)))
            case Success(CurrencyConversionFailure(message)) => complete(InternalServerError, message)
            case Failure(_) => complete(InternalServerError, "Failure")
          }
        }
      }
    }
  }

  val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)
  println(s"Server started at http://localhost:8080")
  Await.result(actorSystem.whenTerminated, Duration.Inf)
  
}
