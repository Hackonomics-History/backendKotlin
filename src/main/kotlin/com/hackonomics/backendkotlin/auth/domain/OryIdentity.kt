package com.hackonomics.backendkotlin.auth.domain

data class OryIdentity(
    val id: String,
    val roles: List<String> = emptyList(),
    val permissions: Set<String> = emptySet(),
    val deviceId: String? = null,
)
