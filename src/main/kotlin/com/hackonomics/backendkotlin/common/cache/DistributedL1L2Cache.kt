package com.hackonomics.backendkotlin.common.cache

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantReadWriteLock

private val log = LoggerFactory.getLogger(DistributedL1L2Cache::class.java)

// Two-level lazy cache with distributed coordination:
//   L1 — JVM in-process (l1BaseTtl + random jitter): zero-latency reads on warm path
//   L2 — Redis (l2Ttl): shared across pods; absorbs concurrent L1 misses
//
// On L2 miss: a SETNX distributed lock ensures only one pod fetches from origin
// while others wait up to (lockRetries × lockRetryMs) then read the warmed L2.
//
// Jitter on L1 TTL prevents pods that started together from expiring L1 simultaneously
// (thundering herd). Lazy-load only: no background refresh thread — silent during
// off-peak hours when no requests arrive.
class DistributedL1L2Cache<T>(
    private val redisTemplate: RedisTemplate<String, String>,
    private val l2Key: String,
    private val lockKey: String,
    private val l1BaseTtl: Duration,
    private val l1JitterMax: Duration,
    private val l2Ttl: Duration,
    private val lockTtl: Duration = Duration.ofSeconds(30),
    private val lockRetryMs: Long = 200,
    private val lockRetries: Int = 10,
    private val serialize: (T) -> String,
    private val deserialize: (String) -> T,
) {
    private val rwLock = ReentrantReadWriteLock()

    // Atomic release: only deletes the lock key if the stored value matches our lockValue,
    // preventing a slow pod from releasing a lock it no longer owns.
    private val releaseLockScript = DefaultRedisScript<Long>().apply {
        setScriptText(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end"
        )
        resultType = Long::class.java
    }

    @Volatile private var l1Value: T? = null
    @Volatile private var l1ExpiresAt: Instant = Instant.EPOCH

    fun get(origin: () -> T): T {
        // Fast path: L1 hit — no Redis round-trip needed
        rwLock.readLock().lock()
        try {
            val cached = l1Value
            if (cached != null && Instant.now().isBefore(l1ExpiresAt)) return cached
        } finally {
            rwLock.readLock().unlock()
        }
        return refresh(origin)
    }

    private fun refresh(origin: () -> T): T {
        rwLock.writeLock().lock()
        try {
            // Double-check: another thread in this pod may have already refreshed L1.
            val cached = l1Value
            if (cached != null && Instant.now().isBefore(l1ExpiresAt)) return cached

            // L2 hit: another pod refreshed already — sync L1 without touching origin.
            val l2Json = runCatching { redisTemplate.opsForValue().get(l2Key) }.getOrNull()
            if (l2Json != null) {
                val value = deserialize(l2Json)
                updateL1(value)
                log.debug("Cache[$l2Key] warmed from L2 Redis")
                return value
            }

            // L2 cold: compete for distributed lock before hitting origin.
            val lockValue = UUID.randomUUID().toString()
            val acquired = runCatching {
                redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, lockTtl) == true
            }.getOrDefault(false)

            return if (acquired) {
                try { fetchAndStore(origin) } finally { releaseLock(lockValue) }
            } else {
                awaitL2OrFallback(origin)
            }
        } finally {
            rwLock.writeLock().unlock()
        }
    }

    // Waits for the lock-holder to populate L2 then reads from it.
    // Falls back to a direct origin fetch if retries exhaust (e.g. lock holder crashed).
    private fun awaitL2OrFallback(origin: () -> T): T {
        repeat(lockRetries) {
            Thread.sleep(lockRetryMs)
            val l2Json = runCatching { redisTemplate.opsForValue().get(l2Key) }.getOrNull()
            if (l2Json != null) {
                val value = deserialize(l2Json)
                updateL1(value)
                log.debug("Cache[$l2Key] loaded from L2 after lock wait")
                return value
            }
        }
        log.warn("Cache[$l2Key] lock wait exhausted — fetching from origin as fallback")
        return fetchAndStore(origin)
    }

    private fun fetchAndStore(origin: () -> T): T {
        val value = origin()
        runCatching {
            redisTemplate.opsForValue().set(l2Key, serialize(value), l2Ttl)
        }.onFailure { log.warn("Cache[$l2Key] failed to write to L2: {}", it.message) }
        updateL1(value)
        log.info("Cache[$l2Key] refreshed from origin")
        return value
    }

    private fun releaseLock(lockValue: String) {
        runCatching {
            redisTemplate.execute(releaseLockScript, listOf(lockKey), lockValue)
        }.onFailure { log.warn("Cache[$l2Key] failed to release distributed lock: {}", it.message) }
    }

    private fun updateL1(value: T) {
        l1Value = value
        val jitterMs = ThreadLocalRandom.current().nextLong(l1JitterMax.toMillis())
        l1ExpiresAt = Instant.now().plus(l1BaseTtl).plusMillis(jitterMs)
    }
}
