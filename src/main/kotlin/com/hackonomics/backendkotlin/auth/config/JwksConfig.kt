package com.hackonomics.backendkotlin.auth.config

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantReadWriteLock

private val log = LoggerFactory.getLogger(JwksConfig::class.java)

// Layered JWKS caching — lazy loading only (no background refresh):
//   L1 — JVM in-memory (60 min + 0–10 min jitter): zero-latency reads on warm path
//   L2 — Redis shared  (55 min, key: auth:jwks:v1): shared across pods
//   Origin — Hydra /.well-known/jwks.json: fetched only on full L2 miss
//
// Jitter prevents pods that started simultaneously from expiring L1 at the same instant,
// which would otherwise cause a burst of concurrent L2 reads (thundering herd).
//
// Distributed lock (SETNX on auth:jwks:lock) ensures at most one pod fetches from
// origin when L2 is cold; all other pods wait up to 2 s then read the warm L2.
//
// Keys share the auth: namespace with auth:blacklist: entries on the same Redis instance.
@Configuration
class JwksConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private val jwksUri: String,
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private val issuerUri: String,
    private val redisTemplate: RedisTemplate<String, String>,
) {

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val jwkSource = CachedRemoteJwkSource(jwksUri, redisTemplate)

        val jwtProcessor = DefaultJWTProcessor<SecurityContext>()
        jwtProcessor.jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)

        val decoder = NimbusJwtDecoder(jwtProcessor)
        if (issuerUri.isNotBlank()) {
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri))
        }
        return decoder
    }
}

private class CachedRemoteJwkSource(
    private val jwksUri: String,
    private val redisTemplate: RedisTemplate<String, String>,
    private val l1BaseTtl: Duration = Duration.ofMinutes(60),
    private val l1JitterMax: Duration = Duration.ofMinutes(10),
    private val l2Ttl: Duration = Duration.ofMinutes(55),
    private val lockTtl: Duration = Duration.ofSeconds(30),
    private val lockRetryMs: Long = 200,
    private val lockRetries: Int = 10,
) : JWKSource<SecurityContext> {

    private val l2Key = "auth:jwks:v1"
    private val lockKey = "auth:jwks:lock"
    private val lock = ReentrantReadWriteLock()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    // Atomic lock release: only deletes the key if the stored value matches our lockValue,
    // preventing a slow pod from releasing a lock it no longer owns (expired and re-acquired).
    private val releaseLockScript = DefaultRedisScript<Long>().apply {
        setScriptText(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end"
        )
        resultType = Long::class.java
    }

    @Volatile private var l1Cache: JWKSet? = null
    @Volatile private var l1ExpiresAt: Instant = Instant.EPOCH

    override fun get(selector: JWKSelector, context: SecurityContext?): List<JWK> =
        selector.select(getOrFetch())

    private fun getOrFetch(): JWKSet {
        lock.readLock().lock()
        try {
            val cached = l1Cache
            if (cached != null && Instant.now().isBefore(l1ExpiresAt)) return cached
        } finally {
            lock.readLock().unlock()
        }
        return fetchAndPopulate()
    }

    private fun fetchAndPopulate(): JWKSet {
        lock.writeLock().lock()
        try {
            // Double-check: another thread in this pod may have already refreshed L1.
            val cached = l1Cache
            if (cached != null && Instant.now().isBefore(l1ExpiresAt)) return cached

            // L2 hit: another pod refreshed already — sync L1 without touching origin.
            val l2Json = runCatching { redisTemplate.opsForValue().get(l2Key) }.getOrNull()
            if (l2Json != null) {
                val jwkSet = JWKSet.parse(l2Json)
                updateL1(jwkSet)
                log.debug("JWKS loaded from Redis L2 cache")
                return jwkSet
            }

            // L2 cold — compete for distributed lock before hitting origin.
            val lockValue = UUID.randomUUID().toString()
            val acquired = runCatching {
                redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, lockTtl) == true
            }.getOrDefault(false)

            return if (acquired) {
                try {
                    fetchFromOriginAndCache()
                } finally {
                    releaseLock(lockValue)
                }
            } else {
                awaitL2OrFallback()
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    // Waits for the lock-holding pod to populate L2, then reads from it.
    // Falls back to a direct origin fetch if retries exhaust (e.g. lock holder crashed mid-refresh).
    private fun awaitL2OrFallback(): JWKSet {
        repeat(lockRetries) {
            Thread.sleep(lockRetryMs)
            val l2Json = runCatching { redisTemplate.opsForValue().get(l2Key) }.getOrNull()
            if (l2Json != null) {
                val jwkSet = JWKSet.parse(l2Json)
                updateL1(jwkSet)
                log.debug("JWKS loaded from L2 after waiting for distributed lock")
                return jwkSet
            }
        }
        log.warn("Distributed lock wait exhausted — fetching JWKS from origin as fallback")
        return fetchFromOriginAndCache()
    }

    private fun fetchFromOriginAndCache(): JWKSet {
        val jwkSet = fetchFromOrigin()
        runCatching {
            redisTemplate.opsForValue().set(l2Key, jwkSet.toString(false), l2Ttl)
        }.onFailure { log.warn("Failed to write JWKS to L2: {}", it.message) }
        updateL1(jwkSet)
        log.info("JWKS refreshed from origin {}", jwksUri)
        return jwkSet
    }

    private fun releaseLock(lockValue: String) {
        runCatching {
            redisTemplate.execute(releaseLockScript, listOf(lockKey), lockValue)
        }.onFailure { log.warn("Failed to release JWKS distributed lock: {}", it.message) }
    }

    private fun updateL1(jwkSet: JWKSet) {
        l1Cache = jwkSet
        val jitterMs = ThreadLocalRandom.current().nextLong(l1JitterMax.toMillis())
        l1ExpiresAt = Instant.now().plus(l1BaseTtl).plusMillis(jitterMs)
    }

    private fun fetchFromOrigin(): JWKSet {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(jwksUri))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "JWKS origin returned HTTP ${response.statusCode()} from $jwksUri"
        }
        return JWKSet.parse(response.body())
    }
}
