package com.hackonomics.backendkotlin.news.adapter.out.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

private val log = LoggerFactory.getLogger(NewsRefreshKafkaPublisher::class.java)

@Component
class NewsRefreshKafkaPublisher(
    private val kafka: KafkaTemplate<String, String>,
    private val mapper: ObjectMapper,
    @Value("\${kafka.topics.news-refresh-request}") private val topic: String,
) {
    fun publishRequest(countryCode: String, force: Boolean, requestedBy: String = "scheduler") {
        val eventId = UUID.randomUUID().toString()
        val payload = mapper.writeValueAsString(
            mapOf("country_code" to countryCode, "force" to force, "requested_by" to requestedBy)
        )
        val envelope = mapper.writeValueAsString(
            mapOf(
                "event_id"       to eventId,
                "aggregate_type" to "News",
                "aggregate_id"   to countryCode,
                "event_type"     to "NEWS_REFRESH_REQUESTED",
                "payload"        to mapper.readTree(payload),
                "occurred_at"    to Instant.now().toString(),
                "service_name"   to "hackonomics-spring",
            )
        )
        kafka.send(topic, countryCode, envelope)
            .whenComplete { _, ex ->
                if (ex != null) log.error("Failed to publish NEWS_REFRESH_REQUESTED for {}: {}", countryCode, ex.message)
                else log.debug("Published NEWS_REFRESH_REQUESTED for {} (force={})", countryCode, force)
            }
    }
}
