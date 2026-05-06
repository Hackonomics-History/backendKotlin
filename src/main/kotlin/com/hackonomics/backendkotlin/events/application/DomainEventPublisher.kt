package com.hackonomics.backendkotlin.events.application

interface DomainEventPublisher {
    fun publish(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payload: Map<String, Any?>,
    )
}
