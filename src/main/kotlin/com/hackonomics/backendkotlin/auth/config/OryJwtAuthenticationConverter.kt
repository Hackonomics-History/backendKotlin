package com.hackonomics.backendkotlin.auth.config

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

// Maps JWT ext.roles → ROLE_{name} and ext.permissions → exact authority name
// so @PreAuthorize("hasRole('admin')") and @PreAuthorize("hasAuthority('trade:execute')")
// can be used in controllers once roles are populated in central-auth's consent ext.
@Component
class OryJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val ext = jwt.claims["ext"] as? Map<*, *>

        val roleAuthorities = (ext?.get("roles") as? List<*>)
            ?.filterIsInstance<String>()
            ?.map { SimpleGrantedAuthority("ROLE_$it") }
            ?: emptyList()

        val permissionAuthorities = (ext?.get("permissions") as? List<*>)
            ?.filterIsInstance<String>()
            ?.map { SimpleGrantedAuthority(it) }
            ?: emptyList()

        return JwtAuthenticationToken(jwt, roleAuthorities + permissionAuthorities)
    }
}
