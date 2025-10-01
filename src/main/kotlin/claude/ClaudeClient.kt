package org.kozyrev.claude

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.logging.Level
import java.util.logging.Logger

class ClaudeClient(private val config: ClaudeConfig) : AutoCloseable {
    private val logger = Logger.getLogger(ClaudeClient::class.java.name)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }

        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeout
            connectTimeoutMillis = 10000L
            socketTimeoutMillis = 60000L
        }

        expectSuccess = false
    }

    suspend fun sendConversation(
        messages: List<Message>,
        model: String = config.defaultModel,
        maxTokens: Int = config.defaultMaxTokens,
        systemPrompt: String? = null,
        temperature: Double? = null
    ): ClaudeResponse {
        // Валидация параметров
        require(messages.isNotEmpty()) {
            "messages list cannot be empty"
        }

        require(maxTokens > 0) {
            "maxTokens must be positive, got: $maxTokens"
        }

        temperature?.let {
            require(it in 0.0..1.0) {
                "temperature must be between 0.0 and 1.0, got: $it"
            }
        }

        logger.info("Sending conversation to Claude API (${messages.size} messages)")

        val request = ClaudeRequest(
            model = model,
            maxTokens = maxTokens,
            messages = messages,
            system = systemPrompt,
            temperature = temperature
        )

        return makeRequest(request)
    }

    /**
     * Выполняет HTTP запрос к Claude API
     */
    private suspend fun makeRequest(request: ClaudeRequest): ClaudeResponse {
        val response: HttpResponse = try {
            client.post("${config.baseUrl}/messages") {
                header("x-api-key", config.apiKey)
                header("anthropic-version", config.anthropicVersion)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } catch (e: HttpRequestTimeoutException) {
            logger.log(Level.SEVERE, "Request timeout", e)
            throw ClaudeException.NetworkException("Request timeout after ${config.requestTimeout}ms", e)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Network error during request", e)
            throw ClaudeException.NetworkException("Network error: ${e.message}", e)
        }

        return handleResponse(response)
    }

    /**
     * Обрабатывает HTTP ответ и преобразует ошибки
     */
    private suspend fun handleResponse(response: HttpResponse): ClaudeResponse {
        when (response.status) {
            HttpStatusCode.OK -> {
                logger.info("Request successful")
                return response.body<ClaudeResponse>().also {
                    logger.fine("Response received: ${it.content.firstOrNull()?.text?.take(100)}")
                    it.usage?.let { usage ->
                        logger.info("Token usage - Input: ${usage.inputTokens}, Output: ${usage.outputTokens}")
                    }
                }
            }

            HttpStatusCode.Unauthorized -> {
                logger.severe("Authentication failed - Invalid API key")
                throw ClaudeException.AuthenticationException("Invalid API key. Please check your credentials.")
            }

            HttpStatusCode.TooManyRequests -> {
                val retryAfter = response.headers["retry-after"]?.toIntOrNull()
                logger.warning("Rate limit exceeded. Retry after: $retryAfter seconds")
                throw ClaudeException.RateLimitException(
                    "Rate limit exceeded. Please wait before making more requests.",
                    retryAfter
                )
            }

            HttpStatusCode.BadRequest -> {
                val errorBody = tryParseError(response)
                logger.severe("Invalid request: $errorBody")
                throw ClaudeException.InvalidRequestException(errorBody ?: "Invalid request parameters")
            }

            in HttpStatusCode.InternalServerError..HttpStatusCode.GatewayTimeout -> {
                val errorBody = tryParseError(response)
                logger.warning("Server error (${response.status.value}): $errorBody")
                throw ClaudeException.NetworkException("Server error: ${response.status.description}")
            }

            else -> {
                val errorBody = tryParseError(response)
                logger.severe("Unexpected API error (${response.status.value}): $errorBody")
                throw ClaudeException.ApiException(response.status.value, errorBody ?: response.status.description)
            }
        }
    }

    /**
     * Пытается распарсить тело ошибки из ответа
     */
    private suspend fun tryParseError(response: HttpResponse): String? {
        return try {
            val errorResponse = response.body<ErrorResponse>()
            errorResponse.error.message
        } catch (e: Exception) {
            logger.fine("Could not parse error response: ${e.message}")
            try {
                response.bodyAsText()
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun close() {
        logger.info("Closing Claude API client")
        client.close()
    }
}