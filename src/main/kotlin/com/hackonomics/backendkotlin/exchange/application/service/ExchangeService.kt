package com.hackonomics.backendkotlin.exchange.application.service

import com.hackonomics.backendkotlin.account.application.port.out.ExchangeRatePort
import com.hackonomics.backendkotlin.exchange.adapter.`in`.web.dto.ExchangeRatePoint
import com.hackonomics.backendkotlin.exchange.adapter.out.external.FrankfurterClient
import com.hackonomics.backendkotlin.exchange.domain.ExchangePeriod
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class ExchangeService(private val frankfurter: FrankfurterClient) : ExchangeRatePort {

    override fun getUsdRate(targetCurrency: String): Double =
        frankfurter.getLatestRate("USD", targetCurrency)

    fun getUsdHistoryUntilToday(currency: String?, period: String?): List<ExchangeRatePoint> {
        val cur = (currency ?: DEFAULT_CURRENCY).uppercase()
        val p = ExchangePeriod.fromLabel(period ?: DEFAULT_PERIOD)
        val end = LocalDate.now()
        val start = p.startDate(end)

        return frankfurter.getHistoricalRates(start, end, "USD", cur)
            .entries
            .sortedBy { it.key }
            .mapNotNull { (date, rates) ->
                val rate = rates[cur] ?: return@mapNotNull null
                ExchangeRatePoint(date, rate)
            }
    }

    companion object {
        const val DEFAULT_CURRENCY = "CAD"
        const val DEFAULT_PERIOD = "6m"
    }
}
