package com.hackonomics.backendkotlin.auth.config

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

// Authentication token set by BFFServiceKeyFilter for requests arriving via
// Central-auth BFF. Principal is the kratosID from X-User-ID (null for anonymous
// requests to permitAll endpoints that have no session attached).
class BffAuthenticationToken(private val userId: String?) : AbstractAuthenticationToken(
    listOf(SimpleGrantedAuthority("ROLE_BFF"))
) {
    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null
    override fun getPrincipal(): String? = userId
}
