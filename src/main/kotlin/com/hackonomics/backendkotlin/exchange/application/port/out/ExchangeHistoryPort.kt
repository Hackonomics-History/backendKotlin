package com.hackonomics.backendkotlin.exchange.application.port.out

import com.hackonomics.backendkotlin.exchange.adapter.`in`.web.dto.ExchangeRatePoint

interface ExchangeHistoryPort {
    fun getUsdHistoryUntilToday(currency: String?, period: String?): List<ExchangeRatePoint>
}
