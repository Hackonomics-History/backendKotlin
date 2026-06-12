package com.hackonomics.backendkotlin.exchange.adapter.`in`.web.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ExchangeRateSeriesResponse(
    val base: String,
    val target: String,
    val period: String,
    @JsonProperty("end_date") val endDate: String,
    val history: List<ExchangeRatePoint>,
)
