package com.hackonomics.backendkotlin

import org.junit.jupiter.api.Test

/**
 * Smoke test: verifies the full Spring application context starts successfully
 * against real Postgres, real Redis, and an embedded Kafka broker.
 * Kept in integrationTest sourceSet so the unit-test job never attempts
 * external connections.
 */
class BackendKotlinApplicationTests : BaseIntegrationTest() {

    @Test
    fun contextLoads() {
    }
}
