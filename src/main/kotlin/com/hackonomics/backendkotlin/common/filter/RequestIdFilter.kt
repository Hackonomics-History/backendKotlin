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
        val id = req.getHeader("X-Request-ID") ?: UUID.randomUUID().toString()
        req.setAttribute("requestId", id)
        res.setHeader("X-Request-ID", id)
        chain.doFilter(req, res)
    }
}
