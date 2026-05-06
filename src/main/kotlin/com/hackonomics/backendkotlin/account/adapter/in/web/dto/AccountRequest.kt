package com.hackonomics.backendkotlin.account.adapter.`in`.web.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class UpdateAccountRequest(
    @field:NotBlank @field:Size(min = 2, max = 2) val countryCode: String,
    @field:NotBlank @field:Size(min = 3, max = 3) val currency: String,
    @field:DecimalMin("0.01") val annualIncome: BigDecimal,
    @field:DecimalMin("0.00") val monthlyInvestableAmount: BigDecimal,
)
