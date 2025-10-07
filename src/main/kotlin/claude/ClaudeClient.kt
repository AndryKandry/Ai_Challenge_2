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
import kotlin.system.measureTimeMillis
import org.kozyrev.claude.AIClient


class ClaudeClient(private val config: ClaudeConfig) : AIClient {
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

    override suspend fun sendConversation(
        messages: List<Message>,
        model: String?,
        maxTokens: Int?,
        systemPrompt: String?,
        temperature: Double?
    ): AIResponse {
        val modelToUse = model ?: config.defaultModel
        val maxTokensToUse = maxTokens ?: config.defaultMaxTokens

        // Валидация параметров
        require(messages.isNotEmpty()) {
            "messages list cannot be empty"
        }

        require(maxTokensToUse > 0) {
            "maxTokens must be positive, got: $maxTokensToUse"
        }

        temperature?.let {
            require(it in 0.0..1.0) {
                "temperature must be between 0.0 and 1.0, got: $it"
            }
        }

        logger.info("Sending conversation to Claude API (${messages.size} messages)")

        val request = ClaudeRequest(
            model = modelToUse,
            maxTokens = maxTokensToUse,
            messages = messages,
            system = systemPrompt,
            temperature = temperature
        )

        var claudeResponse: ClaudeResponse
        val timeMs = measureTimeMillis {
            claudeResponse = makeRequest(request)
        }

        val content = claudeResponse.content.firstOrNull()?.text
            ?: throw IllegalStateException("Пустой ответ от API")

        val usage = claudeResponse.usage ?: Usage(0, 0)

        // Расчет стоимости для Claude
        val cost = calculateClaudeCost(modelToUse, usage.inputTokens, usage.outputTokens)

        return AIResponse(
            content = content,
            metrics = ResponseMetrics(
                responseTimeMs = timeMs,
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                totalTokens = usage.inputTokens + usage.outputTokens,
                estimatedCostUsd = cost
            ),
            modelName = modelToUse,
            providerName = "Claude"
        )
    }

    private fun calculateClaudeCost(model: String, inputTokens: Int, outputTokens: Int): Double {
        // Цены на Claude модели (per 1M tokens)
        val (inputPrice, outputPrice) = when {
            model.contains("claude-3-opus") -> 15.0 to 75.0
            model.contains("claude-3-sonnet") -> 3.0 to 15.0
            model.contains("claude-3-haiku") -> 0.25 to 1.25
            model.contains("claude-sonnet-4") -> 3.0 to 15.0
            else -> 3.0 to 15.0 // По умолчанию как Sonnet
        }

        return (inputTokens * inputPrice / 1_000_000) + (outputTokens * outputPrice / 1_000_000)
    }

    override fun getProviderName(): String = "Claude"

    override fun getDefaultModel(): String = config.defaultModel

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