// SKAFFOLD-SYNC-MARKER-20260611
// SKAFFOLD-SYNC-MARKER-SECOND-20260611
package com.hackonomics.backendkotlin.auth.resolver

import com.hackonomics.backendkotlin.auth.config.BffAuthenticationToken
import com.hackonomics.backendkotlin.auth.domain.OryIdentity
import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

private val log = LoggerFactory.getLogger(OryIdentityArgumentResolver::class.java)

@Component
class OryIdentityArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == OryIdentity::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): OryIdentity {

        val auth = SecurityContextHolder.getContext().authentication

        log.info(
            "[ORY-RESOLVER1] authClass={} principal={}",
            auth?.javaClass?.simpleName,
            auth?.principal,
        )

        val bffAuth = auth as? BffAuthenticationToken
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        val userId = bffAuth.principal
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        val request =
            webRequest.getNativeRequest(HttpServletRequest::class.java)

        val deviceId = request?.getHeader("X-Device-ID")

        return OryIdentity(
            id = userId,
            deviceId = deviceId,
        )
    }
}