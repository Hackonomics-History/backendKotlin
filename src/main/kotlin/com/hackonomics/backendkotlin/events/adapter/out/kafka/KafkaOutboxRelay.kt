package com.hackonomics.backendkotlin.events.adapter.out.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.hackonomics.backendkotlin.events.adapter.out.persistence.OutboxJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private const val TOPIC = "user-activities"
private const val BATCH_SIZE = 100

@Component
class KafkaOutboxRelay(
    private val repo: OutboxJpaRepository,
    private val kafka: KafkaTemplate<String, String>,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    @Transactional
    fun relay() {
        val events = repo.findUnpublishedForUpdate().take(BATCH_SIZE)
        if (events.isEmpty()) return

        val published = mutableListOf<Long>()
        for (event in events) {
            try {
                val message = mapper.writeValueAsString(mapOf(
                    "event_id" to event.eventId,
                    "aggregate_type" to event.aggregateType,
                    "aggregate_id" to event.aggregateId,
                    "event_type" to event.eventType,
                    "payload" to event.payload,
                    "occurred_at" to event.createdAt.toString(),
                    "service_name" to "hackonomics-spring",
                ))
                kafka.send(TOPIC, event.aggregateId, message).get()
                published.add(event.id)
            } catch (ex: Exception) {
                log.error("Kafka delivery failed for event_id={}: {}", event.eventId, ex.message)
                break
            }
        }

        if (published.isNotEmpty()) {
            repo.markPublished(published)
            log.info("Published {} outbox event(s)", published.size)
        }
    }
}
