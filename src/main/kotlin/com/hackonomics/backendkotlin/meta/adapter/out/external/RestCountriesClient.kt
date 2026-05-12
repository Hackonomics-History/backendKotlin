package com.hackonomics.backendkotlin.meta.adapter.out.external

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.hackonomics.backendkotlin.common.cache.DistributedL1L2Cache
import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

@Component
class RestCountriesClient(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = RestClient.create("https://restcountries.com/v3.1")

    // Countries change very rarely — 22h L1 + 0–30min jitter desynchronises pods;
    // L2 (22.5h) gives a shared warm copy while L1 is cold on a fresh pod.
    private val allCountriesCache = DistributedL1L2Cache<List<Map<String, Any>>>(
        redisTemplate = redisTemplate,
        l2Key = "meta:countries:all",
        lockKey = "meta:countries:all:lock",
        l1BaseTtl = Duration.ofHours(22),
        l1JitterMax = Duration.ofMinutes(30),
        l2Ttl = Duration.ofMinutes(22 * 60 + 30),
        serialize = { objectMapper.writeValueAsString(it) },
        deserialize = { objectMapper.readValue(it, object : TypeReference<List<Map<String, Any>>>() {}) },
    )

    fun fetchAll(): List<Map<String, Any>> = allCountriesCache.get { fetchAllFromOrigin() }

    // Reuses the cached fetchAll result — avoids a separate API call per code lookup.
    // Both the /all and /alpha/{code} endpoints return the same requested fields.
    fun fetchByCode(code: String): Map<String, Any> =
        fetchAll().firstOrNull { (it["cca2"] as? String).equals(code, ignoreCase = true) }
            ?: throw BusinessException(ErrorCode.DATA_NOT_FOUND)

    @Suppress("UNCHECKED_CAST")
    private fun fetchAllFromOrigin(): List<Map<String, Any>> {
        return try {
            client.get()
                .uri("/all?fields=cca2,name,currencies,flags")
                .retrieve()
                .body(List::class.java) as? List<Map<String, Any>>
                ?: emptyList()
        } catch (ex: Exception) {
            log.error("RestCountries fetchAll failed: {}", ex.message)
            throw BusinessException(ErrorCode.EXTERNAL_API_FAILED)
        }
    }
}
