package com.hackonomics.backendkotlin.events.adapter.`in`.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger(BlacklistSyncConsumer::class.java)

private const val FALLBACK_TTL_SECONDS = 86400L * 7 // 7 day fallback when expiresAt is absent or past (admin blocks have no natural token expiry)
private const val MIN_TTL_SECONDS = 60L        // minimum to guard against clock skew

// Mirrors central-auth's blacklist-sync Kafka topic. Supported TargetType values:
//   "USER"        → all tokens for a KratosID  → key: auth:blacklist:USER:{kratosID}
//   "DEVICE"      → single device session       → key: auth:blacklist:DEVICE:{kratosID}:{deviceID}
//   "JTI"         → single access token         → key: auth:blacklist:JTI:{jti}
//   "SERVICE_KEY" → S2S service key             → key: auth:blacklist:SERVICE_KEY:{key}
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
            val expiresAtRaw = msg.path("expires_at").asText("")
            val key = "auth:blacklist:$targetType:$targetValue"

            when (eventType) {
                "blacklist.sync" -> {
                    val ttlSeconds = computeTtlSeconds(expiresAtRaw)
                    redis.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS)
                    log.debug("Blacklisted {}:{} ttl={}s", targetType, targetValue, ttlSeconds)
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

    private fun computeTtlSeconds(expiresAtRaw: String): Long {
        if (expiresAtRaw.isBlank()) return FALLBACK_TTL_SECONDS
        return try {
            val remaining = Instant.parse(expiresAtRaw).epochSecond - Instant.now().epochSecond
            maxOf(remaining, MIN_TTL_SECONDS)
        } catch (_: DateTimeParseException) {
            log.warn("Invalid expires_at value '{}', using fallback TTL", expiresAtRaw)
            FALLBACK_TTL_SECONDS
        }
    }
}
