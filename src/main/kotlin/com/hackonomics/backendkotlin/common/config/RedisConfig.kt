package com.hackonomics.backendkotlin.common.config

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class RedisConfig {

    @Bean
    @Primary
    fun redisTemplate(factory: RedisConnectionFactory): RedisTemplate<String, String> =
        RedisTemplate<String, String>().apply {
            connectionFactory = factory
            keySerializer = StringRedisSerializer()
            valueSerializer = StringRedisSerializer()
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = StringRedisSerializer()
        }

    @Bean
    fun cacheManager(factory: RedisConnectionFactory): RedisCacheManager {
        val json = GenericJackson2JsonRedisSerializer()
        val base = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(json))
            .disableCachingNullValues()

        val perCache = mapOf(
            "business_news"  to base.entryTtl(Duration.ofHours(6)),
            "exchange_rates" to base.entryTtl(Duration.ofHours(1)),
            "countries"      to base.entryTtl(Duration.ofHours(24)),
        )

        return RedisCacheManager.builder(factory)
            .cacheDefaults(base.entryTtl(Duration.ofHours(1)))
            .withInitialCacheConfigurations(perCache)
            .build()
    }
}
