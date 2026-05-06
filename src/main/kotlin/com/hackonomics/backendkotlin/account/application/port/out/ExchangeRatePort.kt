package com.hackonomics.backendkotlin.account.application.port.out

interface ExchangeRatePort {
    fun getUsdRate(targetCurrency: String): Double
}
