package com.hackonomics.backendkotlin.account.adapter.`in`.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class AccountResponse(
    @JsonProperty("country_code") val countryCode: String?,
    @JsonProperty("currency") val currency: String?,
    @JsonProperty("annual_income") val annualIncome: String?,
    @JsonProperty("monthly_investable_amount") val monthlyInvestableAmount: String?,
)
