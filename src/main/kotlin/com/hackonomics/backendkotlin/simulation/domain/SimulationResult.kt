package com.hackonomics.backendkotlin.simulation.domain

data class SimulationResult(
    val currency: String,
    val period: String,
    val monthlyAmount: Double,
    val depositRate: Double,
    val totalInvested: Double,
    val usdFinal: Double,
    val depositFinal: Double,
    val winner: String,
    val diffPercent: Double,
    val summary: String,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "currency" to currency,
        "period" to period,
        "monthly_amount" to (Math.round(monthlyAmount * 100.0) / 100.0),
        "deposit_rate" to (Math.round(depositRate * 100.0) / 100.0),
        "usd" to mapOf(
            "invested" to (Math.round(totalInvested * 100.0) / 100.0),
            "final" to (Math.round(usdFinal * 100.0) / 100.0),
        ),
        "deposit" to mapOf(
            "invested" to (Math.round(totalInvested * 100.0) / 100.0),
            "final" to (Math.round(depositFinal * 100.0) / 100.0),
        ),
        "winner" to winner,
        "diff_percent" to (Math.round(diffPercent * 100.0) / 100.0),
        "summary" to summary,
    )
}
