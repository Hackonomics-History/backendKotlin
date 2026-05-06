package com.hackonomics.backendkotlin.simulation.adapter.`in`.web

import com.hackonomics.backendkotlin.auth.domain.OryIdentity
import com.hackonomics.backendkotlin.simulation.application.service.SimulationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class SimulationRequest(
    val period: String = "1y",
    val depositRate: Double? = null,
)

@RestController
@RequestMapping("/api/simulation")
class SimulationController(private val service: SimulationService) {

    @PostMapping("/compare/dca-vs-deposit/")
    fun compare(identity: OryIdentity, @RequestBody req: SimulationRequest): ResponseEntity<Map<String, Any>> {
        val result = service.compareDcaVsDeposit(identity.id, req.period, req.depositRate)
        return ResponseEntity.ok(result.toMap())
    }
}
