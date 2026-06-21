package com.hackonomics.backendkotlin.exchange.adapter.`in`.web

import com.hackonomics.backendkotlin.exchange.adapter.`in`.web.dto.ExchangeRateResponse
import com.hackonomics.backendkotlin.exchange.adapter.`in`.web.dto.ExchangeRateSeriesResponse
import com.hackonomics.backendkotlin.exchange.application.service.ExchangeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/exchange")
class ExchangeController(private val service: ExchangeService) {

    @GetMapping("/usd-to/{currency}/")
    fun usdTo(@PathVariable currency: String): ResponseEntity<ExchangeRateResponse> {
        val cur = currency.uppercase()
        val rate = service.getUsdRate(cur)
        return ResponseEntity.ok(ExchangeRateResponse("USD", cur, rate))
    }

    @GetMapping("/history/")
    fun history(
        @RequestParam(required = false) currency: String?,
        @RequestParam(defaultValue = "6m") period: String,
    ): ResponseEntity<ExchangeRateSeriesResponse> {
        val cur = (currency ?: ExchangeService.DEFAULT_CURRENCY).uppercase()
        val history = service.getUsdHistoryUntilToday(currency, period)
        return ResponseEntity.ok(
            ExchangeRateSeriesResponse(
                base = "USD",
                target = cur,
                period = period,
                endDate = LocalDate.now().toString(),
                history = history,
            )
        )
    }
}
