package com.hackonomics.backendkotlin.news.adapter.`in`.kafka

import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(NewsRefreshResultConsumer::class.java)

@Component
class NewsRefreshResultConsumer(
    private val mapper: ObjectMapper,
    private val cacheManager: CacheManager,
) {
    @KafkaListener(
        topics = ["\${kafka.topics.news-refresh-result}"],
        groupId = "hackonomics-spring-news-result",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consume(raw: String) {
        try {
            val msg = mapper.readTree(raw)
            val eventType = msg.path("event_type").asText()
            val countryCode = msg.path("aggregate_id").asText()

            when (eventType) {
                "NEWS_REFRESH_COMPLETED" -> {
                    val count = msg.path("payload").path("items_count").asInt()
                    log.info("News refreshed for {} ({} items) — evicting cache", countryCode, count)
                    cacheManager.getCache("business_news")?.evict(countryCode)
                }
                "NEWS_REFRESH_FAILED" -> {
                    val error = msg.path("payload").path("error_message").asText()
                    log.warn("News refresh failed for {}: {}", countryCode, error)
                }
                else -> log.debug("Ignoring news result event_type: {}", eventType)
            }
        } catch (ex: Exception) {
            log.error("Failed to process news.refresh.result: {}", ex.message)
        }
    }
}
