package com.hackonomics.backendkotlin.events.domain

import java.time.Instant
import java.util.UUID

data class OutboxEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: Map<String, Any?>,
    val occurredAt: Instant = Instant.now(),
)
