package com.hackonomics.backendkotlin.account.domain

import java.math.BigDecimal

data class Account(
    val userId: String,             // ory_identity_id
    val countryCode: String?,
    val currency: String?,
    val annualIncome: BigDecimal?,
    val monthlyInvestableAmount: BigDecimal?,
) {
    val annualIncomeMonthly: BigDecimal?
        get() = annualIncome?.divide(BigDecimal("12"), 2, java.math.RoundingMode.DOWN)
}
