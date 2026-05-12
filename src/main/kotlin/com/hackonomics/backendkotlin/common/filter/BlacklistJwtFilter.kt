package com.hackonomics.backendkotlin.common.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private val log = LoggerFactory.getLogger(BlacklistJwtFilter::class.java)

// Runs after Spring Security's FilterChainProxy (-100) so SecurityContextHolder
// already contains the validated JWT when this filter executes.
// Checks three Redis keys written by BlacklistSyncConsumer (auth: prefix shared with auth:jwks:):
//   auth:blacklist:USER:{sub}           → full user logout / admin block
//   auth:blacklist:DEVICE:{sub}:{deviceId} → single-device logout
//   auth:blacklist:JTI:{jti}            → per-token admin block
@Component
@Order(2)
class BlacklistJwtFilter(
    private val redis: RedisTemplate<String, String>,
) : OncePerRequestFilter() {

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val auth = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
        if (auth != null && isBlacklisted(auth)) {
            log.warn("Blocked revoked token sub={}", auth.token.subject?.take(8))
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked")
            return
        }
        chain.doFilter(req, res)
    }

    private fun isBlacklisted(auth: JwtAuthenticationToken): Boolean {
        val sub = auth.token.subject ?: return false
        val jti = auth.token.id
        val deviceId = extractDeviceId(auth)

        val keysToCheck = buildList {
            add("auth:blacklist:USER:$sub")
            if (jti != null) add("auth:blacklist:JTI:$jti")
            if (deviceId != null) add("auth:blacklist:DEVICE:$sub:$deviceId")
        }

        return keysToCheck.any { key ->
            runCatching { redis.hasKey(key) == true }
                .onFailure { log.error("Redis blacklist check failed for key={}: {}", key, it.message) }
                .getOrDefault(false)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractDeviceId(auth: JwtAuthenticationToken): String? =
        (auth.token.claims["ext"] as? Map<String, Any?>)?.get("device_id") as? String
}
