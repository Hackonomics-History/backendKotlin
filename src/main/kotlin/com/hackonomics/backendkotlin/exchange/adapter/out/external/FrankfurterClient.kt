package com.hackonomics.backendkotlin.exchange.adapter.out.external

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.hackonomics.backendkotlin.common.cache.DistributedL1L2Cache
import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
import com.hackonomics.backendkotlin.exchange.application.port.out.FrankfurterPort
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

@Component
class FrankfurterClient(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : FrankfurterPort {
    private val log = LoggerFactory.getLogger(javaClass)
    private val baseUrl = "https://api.frankfurter.dev"
    private val client = RestClient.create(baseUrl)

    // One cache instance per base currency, created lazily on first use.
    // L2 (50 min) < L1 base (55 min) so L1 always has a warm L2 to fall back to on first expiry.
    // Jitter (0–10 min) on top of L1 base desynchronises pods that started simultaneously.
    private val ratesCaches = ConcurrentHashMap<String, DistributedL1L2Cache<Map<String, Double>>>()

    override fun getLatestRate(base: String, target: String): Double =
        try {
            val rates = ratesCaches.computeIfAbsent(base) { buildRatesCache(base) }
                .get { fetchAllRates(base) }
            val rate = rates[target]
            log.debug("[DEBUG] rate lookup [{}/{}] → {}", base, target, rate)
            rate ?: 0.0
        } catch (ex: BusinessException) {
            log.warn("Exchange rate fetch failed for {}/{}, returning 0.0: {}", base, target, ex.message)
            0.0
        }

    override fun getHistoricalRates(
        start: LocalDate,
        end: LocalDate,
        base: String,
        target: String,
    ): Map<String, Map<String, Double>> {
        log.debug("[DEBUG] Frankfurter historical outgoing → {}/v1/{}..{}?from={}&to={}", baseUrl, start, end, base, target)
        return try {
            val entity = client.get()
                .uri("/v1/{start}..{end}?from={base}&to={target}", start, end, base, target)
                .retrieve()
                .toEntity(String::class.java)
            val body = entity.body ?: throw BusinessException(ErrorCode.DATA_NOT_FOUND)
            log.debug("[DEBUG] Frankfurter historical raw body: {}", body)
            val response = objectMapper.readValue(body, Map::class.java)
            log.debug("[DEBUG] Frankfurter historical top-level keys: {}", response.keys)
            @Suppress("UNCHECKED_CAST")
            response["rates"] as? Map<String, Map<String, Double>>
                ?: throw BusinessException(ErrorCode.INVALID_RESPONSE)
        } catch (ex: BusinessException) {
            throw ex
        } catch (ex: Exception) {
            log.error("[DEBUG] Frankfurter historical request failed: {} {}", ex::class.simpleName, ex.message)
            throw BusinessException(ErrorCode.EXTERNAL_API_FAILED)
        }
    }

    private fun buildRatesCache(base: String) = DistributedL1L2Cache(
        redisTemplate = redisTemplate,
        l2Key = "exchange:latest:$base",
        lockKey = "exchange:latest:$base:lock",
        l1BaseTtl = Duration.ofMinutes(55),
        l1JitterMax = Duration.ofMinutes(10),
        l2Ttl = Duration.ofMinutes(50),
        serialize = { objectMapper.writeValueAsString(it) },
        deserialize = { json ->
            objectMapper.readValue(json, object : TypeReference<Map<String, Any>>() {})
                .mapValues { (_, v) -> (v as? Number)?.toDouble() ?: 0.0 }
        },
    )

    // Fetches all currency rates for the given base in one call (/v1/latest?from=USD returns
    // all targets at once) so the cache is populated for any target currency on first access.
    @Suppress("UNCHECKED_CAST")
    private fun fetchAllRates(base: String): Map<String, Double> {
        log.debug("[DEBUG] Frankfurter outgoing → {}/v1/latest?from={}", baseUrl, base)
        return try {
            val entity = client.get()
                .uri("/v1/latest?from={base}", base)
                .retrieve()
                .toEntity(String::class.java)
            val body = entity.body ?: throw BusinessException(ErrorCode.EXTERNAL_API_FAILED)
            log.debug("[DEBUG] Frankfurter raw response body: {}", body)
            val response = objectMapper.readValue(body, Map::class.java)
            log.debug("[DEBUG] Frankfurter parsed top-level keys: {}", response.keys)
            val rates = response["rates"]
            log.debug("[DEBUG] Frankfurter rates map: {}", rates)
            (rates as? Map<String, Any>)
                ?.mapValues { (_, v) -> (v as? Number)?.toDouble() ?: 0.0 }
                ?: throw BusinessException(ErrorCode.INVALID_RESPONSE)
        } catch (ex: BusinessException) {
            log.debug("[DEBUG] Frankfurter BusinessException path: {}", ex.message)
            throw ex
        } catch (ex: RestClientException) {
            log.warn("[DEBUG] Frankfurter RestClientException for base {}: {} {}", base, ex::class.simpleName, ex.message)
            throw BusinessException(ErrorCode.EXTERNAL_API_FAILED)
        } catch (ex: Exception) {
            log.error("[DEBUG] Frankfurter unexpected exception for base {}: {} {}", base, ex::class.simpleName, ex.message, ex)
            throw BusinessException(ErrorCode.EXTERNAL_API_FAILED)
        }
    }
}
