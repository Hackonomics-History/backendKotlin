package com.hackonomics.backendkotlin.exchange.adapter.`in`.web

import com.hackonomics.backendkotlin.exchange.application.service.ExchangeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/exchange")
class ExchangeController(private val service: ExchangeService) {

    @GetMapping("/usd-to/{currency}/")
    fun usdTo(@PathVariable currency: String): ResponseEntity<Map<String, Any>> {
        val rate = service.getUsdRate(currency.uppercase())
        return ResponseEntity.ok(mapOf("base" to "USD", "target" to currency.uppercase(), "rate" to rate))
    }

    @GetMapping("/history/")
    fun history(
        @RequestParam(required = false) currency: String?,
        @RequestParam(defaultValue = "6m") period: String,
    ): ResponseEntity<Map<String, Any>> {
        val cur = (currency ?: service.defaultCurrency).uppercase()
        val history = service.getUsdHistoryUntilToday(currency, period)
        return ResponseEntity.ok(mapOf(
            "base" to "USD",
            "target" to cur,
            "period" to period,
            "end_date" to LocalDate.now().toString(),
            "history" to history.map { mapOf("date" to it.date, "rate" to it.rate) },
        ))
    }
}
