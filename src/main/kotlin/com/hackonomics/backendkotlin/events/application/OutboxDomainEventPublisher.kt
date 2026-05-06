package com.hackonomics.backendkotlin.events.application

import com.hackonomics.backendkotlin.events.domain.OutboxEvent
import org.springframework.stereotype.Component

@Component
class OutboxDomainEventPublisher(private val outboxPublisher: OutboxPublisher) : DomainEventPublisher {
    override fun publish(aggregateType: String, aggregateId: String, eventType: String, payload: Map<String, Any?>) {
        outboxPublisher.publish(
            OutboxEvent(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                payload = payload,
            )
        )
    }
}
