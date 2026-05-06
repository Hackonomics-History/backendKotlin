package com.hackonomics.backendkotlin.common.error

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException, req: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val ec = ex.errorCode
        val body = ErrorResponse(ec.httpStatus, ec.code, ec.message)
        return ResponseEntity.status(ec.httpStatus).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val msg = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        val body = ErrorResponse(400, "ValidationFailed", msg)
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuth(ex: AuthenticationException): ResponseEntity<ErrorResponse> {
        val body = ErrorResponse(401, "Unauthorized", ex.message ?: "Unauthorized")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleForbidden(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        val body = ErrorResponse(403, "Forbidden", "Access is forbidden")
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, req: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val requestId = req.getAttribute("requestId") as? String
        val body = ErrorResponse(500, "InternalError", "Internal server error", requestId)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }
}
