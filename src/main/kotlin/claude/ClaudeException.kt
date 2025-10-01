package org.kozyrev.claude

sealed class ClaudeException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthenticationException(message: String) : ClaudeException(message)
    class RateLimitException(message: String, val retryAfter: Int? = null) : ClaudeException(message)
    class NetworkException(message: String, cause: Throwable? = null) : ClaudeException(message, cause)
    class ApiException(val statusCode: Int, message: String) : ClaudeException("API Error ($statusCode): $message")
    class InvalidRequestException(message: String) : ClaudeException(message)
    class CommonException(message: String, cause: Throwable? = null) : ClaudeException(message)
}