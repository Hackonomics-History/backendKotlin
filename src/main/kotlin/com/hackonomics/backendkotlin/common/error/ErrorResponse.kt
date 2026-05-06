package com.hackonomics.backendkotlin.common.error

data class ErrorResponse(
    val status: Int,
    val code: String,
    val message: String,
    val requestId: String? = null,
)
