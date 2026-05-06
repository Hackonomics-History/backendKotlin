package com.hackonomics.backendkotlin.auth.resolver

import com.hackonomics.backendkotlin.auth.domain.OryIdentity
import com.hackonomics.backendkotlin.common.error.BusinessException
import com.hackonomics.backendkotlin.common.error.ErrorCode
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

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
            as? JwtAuthenticationToken
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val sub = auth.token.subject ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        return OryIdentity(sub)
    }
}
