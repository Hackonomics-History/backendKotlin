package com.hackonomics.backendkotlin.exchange.application.port.out

import java.time.LocalDate

interface FrankfurterPort {
    fun getLatestRate(base: String, target: String): Double
    fun getHistoricalRates(
        start: LocalDate,
        end: LocalDate,
        base: String,
        target: String,
    ): Map<String, Map<String, Double>>
}
