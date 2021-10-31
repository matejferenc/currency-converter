package com.ematiq.api

import akka.http.scaladsl.model.{HttpRequest, Uri}
import com.ematiq.ExchangeApp.executionContext
import com.ematiq.domain.RateExtractionQuery

import scala.concurrent.Future

trait Extractor[T] {
  
  def extract(query: RateExtractionQuery): Future[BigDecimal] = {
    val uri = createUri(query)
    val request = HttpRequest(uri = uri)
    sendRequest(request).map(extractRate(query))
  }

  protected def createUri(query: RateExtractionQuery): Uri

  protected def sendRequest(request: HttpRequest): Future[T]

  protected def extractRate(query: RateExtractionQuery)(response: T): BigDecimal

}
