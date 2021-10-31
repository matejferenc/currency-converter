package com.ematiq

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.ematiq.ExchangeApp.actorSystem
import com.ematiq.converter.CachingCurrencyConverter
import com.ematiq.domain.RateExtractionQuery

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object CurrencyConverter {

  trait CurrencyConversionProtocol
  
  case class CurrencyConversionQuery(extraction: RateExtractionQuery, replyTo: ActorRef[CurrencyConversionResponse]) extends CurrencyConversionProtocol

  sealed trait CurrencyConversionResponse extends CurrencyConversionProtocol
  
  case class CurrencyConversionSuccess(rate: BigDecimal) extends CurrencyConversionResponse
  
  case class CurrencyConversionFailure(message: String) extends CurrencyConversionResponse
  
  case class CurrencyDatePair(currency: String, date: LocalDate)
  
  def apply(): Behavior[CurrencyConversionQuery] = Behaviors.setup[CurrencyConversionQuery] { context =>
    def spawnConverterActor(actorName: String): ActorRef[CurrencyConversionQuery] = {
      context.log.info(s"Spawning actor $actorName")
      context.spawn(CachingCurrencyConverter.apply(None), actorName)
    }
    def cachedBehavior(cache: Map[CurrencyDatePair, ActorRef[CurrencyConversionQuery]]): Behavior[CurrencyConversionQuery] = {
      Behaviors.receiveMessage[CurrencyConversionQuery] { query =>
        val currencyDatePair = CurrencyDatePair(query.extraction.from, query.extraction.date)
        val actorName = s"exchange-rate-from-${query.extraction.from}-to-${query.extraction.to}-on-${query.extraction.date}"
        val actorRef = cache.getOrElse(currencyDatePair, spawnConverterActor(actorName))
        val responseFuture = actorRef.ask[CurrencyConversionResponse](ref => 
          query.copy(replyTo = ref)
        )(timeout = 20.seconds, scheduler = actorSystem.scheduler)
        responseFuture.onComplete {
          case Success(currencyConversionResponse) => query.replyTo ! currencyConversionResponse
          case Failure(throwable) => query.replyTo ! CurrencyConversionFailure(throwable.getMessage)
        }
        cachedBehavior(cache + (currencyDatePair -> actorRef))
      }
    }
    cachedBehavior(Map.empty)
  }
  
}
