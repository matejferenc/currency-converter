package com.ematiq

import java.time.{LocalDate, LocalDateTime}

package object domain {

  case class Bet(marketId: Long, selectionId: Long, odds: BigDecimal, stake: BigDecimal, currency: String, date: LocalDateTime)

  case class RateExtractionQuery(from: String, to: String, date: LocalDate)
   
}
