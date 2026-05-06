package com.hackonomics.backendkotlin.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests { auth ->
            auth.requestMatchers(
                "/actuator/health",
                "/actuator/info",
                "/v3/api-docs/**",
                "/api/docs/**",
                "/api/exchange/**",
                "/api/meta/**",
            ).permitAll()
            auth.anyRequest().authenticated()
        }
        .oauth2ResourceServer { oauth2 -> oauth2.jwt { } }
        .build()
}
