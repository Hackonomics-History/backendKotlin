package com.hackonomics.backendkotlin.exchange.application.service

import com.hackonomics.backendkotlin.account.application.port.out.ExchangeRatePort
import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
import com.hackonomics.backendkotlin.exchange.adapter.out.external.FrankfurterClient
import org.springframework.stereotype.Service
import java.time.LocalDate

data class HistoryRow(val date: String, val rate: Double)

@Service
class ExchangeService(private val frankfurter: FrankfurterClient) : ExchangeRatePort {

    private val periodMonths = mapOf("3m" to 3, "6m" to 6, "1y" to 12, "2y" to 24)
    val defaultCurrency = "CAD"
    val defaultPeriod = "6m"

    override fun getUsdRate(targetCurrency: String): Double =
        frankfurter.getLatestRate("USD", targetCurrency)

    fun getUsdHistoryUntilToday(currency: String?, period: String?): List<HistoryRow> {
        val cur = (currency ?: defaultCurrency).uppercase()
        val per = period ?: defaultPeriod
        val months = periodMonths[per] ?: throw BusinessException(ErrorCode.INVALID_PARAMETER)
        val end = LocalDate.now()
        val start = end.minusMonths(months.toLong())

        val rawRates = frankfurter.getHistoricalRates(start, end, "USD", cur)

        return rawRates.entries
            .sortedBy { it.key }
            .mapNotNull { (date, rates) ->
                val rate = rates[cur] ?: return@mapNotNull null
                HistoryRow(date, rate)
            }
    }
}
