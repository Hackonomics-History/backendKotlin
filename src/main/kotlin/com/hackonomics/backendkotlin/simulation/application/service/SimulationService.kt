package com.hackonomics.backendkotlin.simulation.application.service

import com.hackonomics.backendkotlin.account.application.port.out.AccountRepository
import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
import com.hackonomics.backendkotlin.exchange.application.service.ExchangeService
import com.hackonomics.backendkotlin.exchange.application.service.HistoryRow
import com.hackonomics.backendkotlin.simulation.domain.SimulationResult
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

@Service
class SimulationService(
    private val accountRepo: AccountRepository,
    private val exchangeService: ExchangeService,
) {
    private val periodMap = mapOf("1y" to 12, "2y" to 24)
    private val defaultDepositRate = BigDecimal("3.0")
    private val mc = MathContext(28)

    fun compareDcaVsDeposit(oryId: String, period: String, depositRate: Double?): SimulationResult {
        val months = periodMap[period] ?: throw BusinessException(ErrorCode.INVALID_PARAMETER)

        val depositRateD = if (depositRate == null) defaultDepositRate
        else {
            val d = BigDecimal(depositRate.toString())
            if (d < BigDecimal.ZERO) throw BusinessException(ErrorCode.INVALID_PARAMETER)
            d
        }

        val account = accountRepo.findByOryId(oryId)
            ?: throw BusinessException(ErrorCode.DATA_NOT_FOUND)
        val currency = account.currency?.uppercase()
            ?: throw BusinessException(ErrorCode.DATA_NOT_FOUND)
        val monthlyAmount = account.monthlyInvestableAmount?.let { BigDecimal(it.toPlainString()) }
            ?: throw BusinessException(ErrorCode.DATA_NOT_FOUND)
        if (monthlyAmount <= BigDecimal.ZERO) throw BusinessException(ErrorCode.INVALID_PARAMETER)

        val history = exchangeService.getUsdHistoryUntilToday(currency, period)
        if (history.isEmpty()) throw BusinessException(ErrorCode.DATA_NOT_FOUND)

        val monthlyRates = extractMonthlyRates(history, months)
        if (monthlyRates.size < months) throw BusinessException(ErrorCode.DATA_NOT_FOUND)

        var totalUsd = BigDecimal.ZERO
        var totalInvested = BigDecimal.ZERO
        for (h in monthlyRates) {
            val rate = BigDecimal(h.rate.toString())
            val usd = monthlyAmount.divide(rate, mc)
            totalUsd += usd
            totalInvested += monthlyAmount
        }

        val lastRate = BigDecimal(history.last().rate.toString())
        val usdFinal = totalUsd * lastRate
        val depositFinal = totalInvested * (BigDecimal.ONE + depositRateD.divide(BigDecimal("100"), mc))

        val winner: String
        val diff: BigDecimal
        if (usdFinal > depositFinal) {
            winner = "usd"
            diff = (usdFinal - depositFinal).divide(depositFinal, mc) * BigDecimal("100")
        } else {
            winner = "deposit"
            diff = (depositFinal - usdFinal).divide(usdFinal, mc) * BigDecimal("100")
        }

        return SimulationResult(
            currency = currency,
            period = period,
            monthlyAmount = monthlyAmount.toDouble(),
            depositRate = depositRateD.toDouble(),
            totalInvested = totalInvested.toDouble(),
            usdFinal = usdFinal.toDouble(),
            depositFinal = depositFinal.toDouble(),
            winner = winner,
            diffPercent = diff.toDouble(),
            summary = makeSummary(winner, diff, currency),
        )
    }

    private fun extractMonthlyRates(history: List<HistoryRow>, months: Int): List<HistoryRow> {
        val seen = mutableSetOf<Pair<Int, Int>>()
        val result = mutableListOf<HistoryRow>()
        for (h in history) {
            val d = LocalDate.parse(h.date)
            val key = d.year to d.monthValue
            if (key !in seen) {
                result.add(h)
                seen.add(key)
            }
            if (result.size == months) break
        }
        return result
    }

    private fun makeSummary(winner: String, diff: BigDecimal, currency: String): String {
        val rounded = diff.setScale(2, RoundingMode.HALF_UP)
        return if (winner == "usd")
            "Over this period, investing in USD through Dollar-Cost Averaging (DCA) outperformed the fixed-term deposit by approximately $rounded%. ($currency based)"
        else
            "Over this period, the fixed-term deposit outperformed investing in USD through Dollar-Cost Averaging (DCA) by approximately $rounded%. ($currency based)"
    }
}
