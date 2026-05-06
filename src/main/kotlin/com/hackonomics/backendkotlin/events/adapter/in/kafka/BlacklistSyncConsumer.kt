package com.hackonomics.backendkotlin.events.adapter.`in`.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger(BlacklistSyncConsumer::class.java)

// Mirrors Django's BlacklistSyncConsumer: listens to Central-Auth's
// blacklist-sync topic and writes revocation entries to Redis L1 cache.
@Component
class BlacklistSyncConsumer(
    private val redis: RedisTemplate<String, String>,
    private val mapper: ObjectMapper,
) {
    @KafkaListener(
        topics = ["\${kafka.topics.blacklist-sync}"],
        groupId = "hackonomics-spring-blacklist",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consume(raw: String) {
        try {
            val msg = mapper.readTree(raw)
            val eventType = msg.path("event_type").asText()
            val targetType = msg.path("target_type").asText()
            val targetValue = msg.path("target_value").asText()
            val key = "blacklist:$targetType:$targetValue"

            when (eventType) {
                "blacklist.sync" -> {
                    redis.opsForValue().set(key, "1", 60, TimeUnit.SECONDS)
                    log.debug("Blacklisted {}:{}", targetType, targetValue)
                }
                "blacklist.unblock" -> {
                    redis.delete(key)
                    log.debug("Unblocked {}:{}", targetType, targetValue)
                }
                else -> log.warn("Unknown blacklist event_type: {}", eventType)
            }
        } catch (ex: Exception) {
            log.error("Failed to process blacklist-sync message: {}", ex.message)
        }
    }
}
