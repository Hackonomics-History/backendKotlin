package com.hackonomics.backendkotlin.common.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(1)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val requestId = req.getHeader("X-Request-ID") ?: UUID.randomUUID().toString()
        req.setAttribute("requestId", requestId)
        res.setHeader("X-Request-ID", requestId)

        // Propagate correlation ID set by central-auth; echo it back in the response
        // so client-side and distributed tracing tools can correlate cross-service calls.
        val correlationId = req.getHeader("X-Correlation-ID") ?: requestId
        req.setAttribute("correlationId", correlationId)
        res.setHeader("X-Correlation-ID", correlationId)

        chain.doFilter(req, res)
    }
}
