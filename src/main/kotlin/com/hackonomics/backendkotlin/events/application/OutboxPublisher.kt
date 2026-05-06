package com.hackonomics.backendkotlin.events.application

import com.hackonomics.backendkotlin.events.adapter.out.persistence.OutboxJpaEntity
import com.hackonomics.backendkotlin.events.adapter.out.persistence.OutboxJpaRepository
import com.hackonomics.backendkotlin.events.domain.OutboxEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxPublisher(private val repo: OutboxJpaRepository) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun publish(event: OutboxEvent) {
        repo.save(OutboxJpaEntity(
            eventId = event.eventId,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            eventType = event.eventType,
            payload = event.payload,
            createdAt = event.occurredAt,
        ))
    }
}
