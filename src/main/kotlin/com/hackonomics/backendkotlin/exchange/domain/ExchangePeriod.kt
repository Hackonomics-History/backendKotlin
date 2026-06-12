package com.hackonomics.backendkotlin.exchange.domain

import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
import java.time.LocalDate

enum class ExchangePeriod(val label: String, private val months: Long) {
    THREE_MONTHS("3m", 3),
    SIX_MONTHS("6m", 6),
    ONE_YEAR("1y", 12),
    TWO_YEARS("2y", 24);

    fun startDate(end: LocalDate): LocalDate = end.minusMonths(months)

    companion object {
        fun fromLabel(label: String): ExchangePeriod =
            entries.find { it.label == label }
                ?: throw BusinessException(ErrorCode.INVALID_PARAMETER)
    }
}
