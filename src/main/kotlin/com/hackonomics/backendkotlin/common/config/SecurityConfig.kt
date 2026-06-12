package com.hackonomics.backendkotlin.common.config

import com.hackonomics.backendkotlin.common.filter.BFFServiceKeyFilter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    @Value("\${kotlin.service-key}") private val kotlinServiceKey: String,
    @Qualifier("redisTemplate") private val redisTemplate: RedisTemplate<String, String>,
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        val bffFilter = BFFServiceKeyFilter(kotlinServiceKey, redisTemplate)

        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(bffFilter, UsernamePasswordAuthenticationFilter::class.java)
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
            .build()
    }
}
