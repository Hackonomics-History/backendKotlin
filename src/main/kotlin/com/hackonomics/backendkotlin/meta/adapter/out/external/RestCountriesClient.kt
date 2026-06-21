package com.hackonomics.backendkotlin.meta.adapter.out.external

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.hackonomics.backendkotlin.common.cache.DistributedL1L2Cache
import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

@Component
class RestCountriesClient(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${restcountries.api-key}") private val apiKey: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient by lazy {
        RestClient.builder()
            .baseUrl("https://api.restcountries.com")
            .defaultHeader("Authorization", "Bearer $apiKey")
            .build()
    }

    // Countries change very rarely — 22h L1 + 0–30min jitter desynchronises pods;
    // L2 (22.5h) gives a shared warm copy while L1 is cold on a fresh pod.
    private val allCountriesCache = DistributedL1L2Cache<List<CountryV5Response>>(
        redisTemplate = redisTemplate,
        l2Key = "meta:countries:all",
        lockKey = "meta:countries:all:lock",
        l1BaseTtl = Duration.ofHours(22),
        l1JitterMax = Duration.ofMinutes(30),
        l2Ttl = Duration.ofMinutes(22 * 60 + 30),
        serialize = { objectMapper.writeValueAsString(it) },
        deserialize = { objectMapper.readValue(it, object : TypeReference<List<CountryV5Response>>() {}) },
    )

    @PostConstruct
    fun validateConfig() {
        check(apiKey.isNotBlank()) { "restcountries.api-key must not be blank" }
        runCatching {
            client.get()
                .uri("/countries/v5")
                .exchange { req, res ->
                    log.info("RestCountries startup probe: GET {} → {}", req.uri, res.statusCode)
                }
        }.onFailure { ex ->
            log.warn("RestCountries startup probe failed: {}", ex.message)
        }
    }

    fun fetchAll(): List<CountryV5Response> = allCountriesCache.get { fetchAllFromOrigin() }

    // Reuses the cached fetchAll result — avoids a separate API call per code lookup.
    fun fetchByCode(code: String): CountryV5Response =
        fetchAll().firstOrNull { it.codes.alpha2.equals(code, ignoreCase = true) }
            ?: throw BusinessException(ErrorCode.DATA_NOT_FOUND)

    private fun fetchAllFromOrigin(): List<CountryV5Response> {
        val all = mutableListOf<CountryV5Response>()
        val limit = 100
        var offset = 0
        while (true) {
            val response = try {
                client.get()
                    .uri { b ->
                        b.path("/countries/v5")
                            .queryParam("limit", limit)
                            .queryParam("offset", offset)
                            .build()
                    }
                    .retrieve()
                    .body(CountriesResponse::class.java)
                    ?: throw IllegalStateException("Null response body at offset $offset")
            } catch (ex: Exception) {
                log.error("RestCountries fetchAll failed at offset={}: {}", offset, ex.message)
                throw BusinessException(ErrorCode.EXTERNAL_API_FAILED)
            }

            val objects = response.data.objects
            val meta = response.data.meta
            log.info(
                "RestCountries fetch offset={} limit={} returned={} totalAccumulated={}",
                offset,
                limit,
                objects.size,
                all.size + objects.size,
            )

            if (objects.isEmpty()) break
            all.addAll(objects)

            if (meta.total != null && all.size >= meta.total) break
            if (objects.size < limit) break

            offset += limit
        }
        log.info("RestCountries fetchAll complete: {} countries", all.size)
        return all
    }
}
