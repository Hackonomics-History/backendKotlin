package com.hackonomics.backendkotlin.exchange.adapter.out.cache

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.hackonomics.backendkotlin.common.cache.DistributedL1L2Cache
import com.hackonomics.backendkotlin.exchange.adapter.`in`.web.dto.ExchangeRatePoint
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

@Component
class ExchangeHistoryCache(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val caches = ConcurrentHashMap<String, DistributedL1L2Cache<List<ExchangeRatePoint>>>()

    fun get(
        currency: String,
        period: String,
        date: LocalDate,
        origin: () -> List<ExchangeRatePoint>,
    ): List<ExchangeRatePoint> {
        val l2Key = "exchange:history:USD:$currency:$period:$date"
        return caches.computeIfAbsent(l2Key) { buildCache(l2Key) }.get(origin)
    }

    private fun buildCache(l2Key: String) = DistributedL1L2Cache(
        redisTemplate = redisTemplate,
        l2Key = l2Key,
        lockKey = "$l2Key:lock",
        l1BaseTtl = Duration.ofHours(25),
        l1JitterMax = Duration.ofMinutes(30),
        l2Ttl = Duration.ofHours(26),
        serialize = { objectMapper.writeValueAsString(it) },
        deserialize = { json ->
            objectMapper.readValue(json, object : TypeReference<List<ExchangeRatePoint>>() {})
        },
    )
}
