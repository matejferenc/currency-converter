package com.ematiq.converter

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.ematiq.CurrencyConverter._
import com.ematiq.ExchangeApp.executionContext
import com.ematiq.api.{CurrConvExtractor, ExchangeRateExtractor}
import org.slf4j.{Logger, LoggerFactory}

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.util.{Failure, Success}

object CachingCurrencyConverter {

  val logger: Logger = LoggerFactory.getLogger(CachingCurrencyConverter.getClass)

  private case class CachedResponse(dateTime: LocalDateTime, rate: BigDecimal)

  def apply(cachedResponse: Option[CachedResponse]): Behavior[CurrencyConversionProtocol] = Behaviors.receive[CurrencyConversionProtocol] { (context, currencyConversionProtocol) =>
    currencyConversionProtocol match {
      case query: CurrencyConversionQuery =>
        val responseFuture: Future[BigDecimal] =
          cachedResponse match {
            case Some(CachedResponse(dateTime, response)) if cacheTimeValid(dateTime) =>
              context.log.info(s"Using cached rate for query $query")
              Future.successful(response)
            case None =>
              ExchangeRateExtractor.extract(query.extraction)
              .transformWith {
                case Success(rate) =>
                  Future.successful(rate)
                case Failure(exception) =>
                  logger.info(s"Request to primary endpoint failed, falling back", exception)
                  CurrConvExtractor.extract(query.extraction)
              }
          }
        context.pipeToSelf(responseFuture) {
          case Success(rate: BigDecimal) =>
            val response: CurrencyConversionResponse = CurrencyConversionSuccess(rate)
            query.replyTo ! response
            context.log.info(s"Currency conversion successful")
            response
          case Failure(ex) =>
            val failure = CurrencyConversionFailure(ex.getMessage)
            query.replyTo ! failure
            context.log.info(s"Currency conversion failed")
            failure
        }
        Behaviors.same
      case CurrencyConversionSuccess(rate) => apply(Some(CachedResponse(LocalDateTime.now(), rate)))
      case CurrencyConversionFailure(_) => Behaviors.same
    }
  }

  private def cacheTimeValid(dateTime: LocalDateTime): Boolean = {
    dateTime.plusHours(2).isAfter(LocalDateTime.now())
  }

}
