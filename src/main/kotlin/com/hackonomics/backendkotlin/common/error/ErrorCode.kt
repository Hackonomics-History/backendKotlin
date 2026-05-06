package com.hackonomics.backendkotlin.common.error

enum class ErrorCode(val httpStatus: Int, val code: String, val message: String) {
    INVALID_PARAMETER(400, "InvalidParameter", "Invalid request parameter"),
    VALIDATION_FAILED(400, "ValidationFailed", "Validation failed"),
    MISSING_REQUIRED_FIELD(400, "MissingRequiredField", "Required field is missing"),
    INVALID_CREDENTIALS(401, "InvalidCredentials", "Invalid email or password"),
    UNAUTHORIZED(401, "Unauthorized", "Unauthorized"),
    TOKEN_INVALID(401, "InvalidToken", "Invalid token"),
    TOKEN_EXPIRED(401, "TokenExpired", "Token expired"),
    FORBIDDEN(403, "Forbidden", "Access is forbidden"),
    ACCESS_DENIED(403, "AccessDenied", "You do not have permission"),
    DATA_NOT_FOUND(404, "DataNotFound", "Requested resource not found"),
    USER_NOT_FOUND(404, "UserNotFound", "User not found"),
    USER_CALENDAR_NOT_FOUND(404, "UserCalendarNotFound", "User Calendar is not found"),
    DUPLICATE_ENTRY(409, "DuplicateEntry", "Resource already exists"),
    INTERNAL_ERROR(500, "InternalError", "Internal server error"),
    EXTERNAL_API_FAILED(502, "ExternalApiFailed", "External service failed"),
    INVALID_RESPONSE(502, "InvalidResponse", "Invalid response from external service"),
    SERVICE_UNAVAILABLE(503, "ServiceUnavailable", "Service temporarily unavailable"),
    TIMEOUT(504, "Timeout", "External service timeout"),
}
