package com.hackonomics.backendkotlin.exchange.adapter.`in`.web.dto

data class ExchangeRateResponse(
    val base: String?,
    val target: String?,
    val rate: Double,
)
