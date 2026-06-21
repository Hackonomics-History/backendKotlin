package com.hackonomics.backendkotlin.common.filter

import com.hackonomics.backendkotlin.auth.config.BffAuthenticationToken
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

private val log = LoggerFactory.getLogger(BFFServiceKeyFilter::class.java)

class BFFServiceKeyFilter(
    private val expectedKey: String,
    @Qualifier("redisTemplate") private val redis: RedisTemplate<String, String>,
) : OncePerRequestFilter() {

    init {
        require(expectedKey.isNotBlank()) { "KOTLIN_SERVICE_KEY must not be blank" }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/actuator/")

    override fun doFilterInternal(
        req: HttpServletRequest,
        res: HttpServletResponse,
        chain: FilterChain,
    ) {

        log.info(
            "[BFF-FILTER] HIT path={} serviceKeyExists={} userId={}",
            req.requestURI,
            req.getHeader("X-Service-Key") != null,
            req.getHeader("X-User-ID"),
        )

        val incomingKey = req.getHeader("X-Service-Key")

        if (incomingKey == null || !constantTimeEquals(incomingKey, expectedKey)) {
            log.warn(
                "[BFF-FILTER] INVALID SERVICE KEY path={} remote={}",
                req.requestURI,
                req.remoteAddr,
            )

            res.sendError(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Missing or invalid service key",
            )
            return
        }

        val userId = req.getHeader("X-User-ID")

        log.info(
            "[BFF-FILTER] SERVICE KEY OK path={} userId={}",
            req.requestURI,
            userId,
        )

        if (userId != null) {

            if (isUserBlacklisted(userId)) {
                log.warn(
                    "[BFF-FILTER] USER BLACKLISTED userId={}",
                    userId.take(8),
                )

                res.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "User access revoked",
                )
                return
            }

            SecurityContextHolder.getContext().authentication =
                BffAuthenticationToken(userId)

            log.info(
                "[BFF-FILTER] AUTH SET userId={} authClass={}",
                userId.take(8),
                SecurityContextHolder.getContext().authentication?.javaClass?.simpleName,
            )
        } else {
            log.info(
                "[BFF-FILTER] ANONYMOUS REQUEST path={}",
                req.requestURI,
            )
        }

        log.info(
            "[BFF-FILTER] CONTINUE path={} auth={}",
            req.requestURI,
            SecurityContextHolder.getContext().authentication?.javaClass?.simpleName,
        )

        chain.doFilter(req, res)
    }

    private fun isUserBlacklisted(userId: String): Boolean {
        return runCatching {
            redis.hasKey("auth:blacklist:USER:$userId") == true
        }
            .onFailure {
                log.error(
                    "Redis blacklist check failed for user {}: {}",
                    userId.take(8),
                    it.message,
                )
            }
            .getOrDefault(true)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}