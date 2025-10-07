package org.kozyrev.claude

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.logging.Logger
import kotlin.system.measureTimeMillis

/**
 * Клиент для работы с HuggingFace Inference API
 */
class HuggingFaceClient(private val config: HuggingFaceConfig) : AIClient {
    private val logger = Logger.getLogger(HuggingFaceClient::class.java.name)

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

        logger.info("Отправка запроса к HuggingFace модели: $modelToUse")

        // Конвертируем сообщения в формат HuggingFace
        val hfMessages = convertMessages(messages, systemPrompt)

        val request = HuggingFaceRequest(
            model = modelToUse,
            messages = hfMessages,
            maxTokens = maxTokensToUse,
            temperature = temperature,
            stream = false
        )

        var response: HuggingFaceResponse
        val timeMs = measureTimeMillis {
            response = makeRequest(request)
        }

        val content = response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("Пустой ответ от HuggingFace API")

        val usage = response.usage ?: HuggingFaceUsage(0, 0, 0)

        // Рассчитываем стоимость (если модель платная)
        val cost = calculateCost(modelToUse, usage.promptTokens, usage.completionTokens)

        return AIResponse(
            content = content,
            metrics = ResponseMetrics(
                responseTimeMs = timeMs,
                inputTokens = usage.promptTokens,
                outputTokens = usage.completionTokens,
                totalTokens = usage.totalTokens,
                estimatedCostUsd = cost
            ),
            modelName = modelToUse,
            providerName = "HuggingFace"
        )
    }

    private fun convertMessages(messages: List<Message>, systemPrompt: String?): List<HuggingFaceMessage> {
        val result = mutableListOf<HuggingFaceMessage>()

        // Добавляем системный промпт если есть
        systemPrompt?.let {
            result.add(HuggingFaceMessage(role = "system", content = it))
        }

        // Конвертируем остальные сообщения
        messages.forEach { msg ->
            result.add(HuggingFaceMessage(role = msg.role, content = msg.content))
        }

        return result
    }

    private suspend fun makeRequest(request: HuggingFaceRequest): HuggingFaceResponse {
        val response: HttpResponse = try {
            client.post(config.baseUrl) {
                header("Authorization", "Bearer ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } catch (e: Exception) {
            logger.severe("Ошибка сети: ${e.message}")
            throw ClaudeException.NetworkException("Network error: ${e.message}", e)
        }

        return handleResponse(response)
    }

    private suspend fun handleResponse(response: HttpResponse): HuggingFaceResponse {
        when (response.status) {
            HttpStatusCode.OK -> {
                logger.info("Запрос успешен")
                return response.body<HuggingFaceResponse>()
            }

            HttpStatusCode.Unauthorized -> {
                logger.severe("Ошибка аутентификации")
                throw ClaudeException.AuthenticationException("Invalid HuggingFace API key")
            }

            HttpStatusCode.TooManyRequests -> {
                logger.warning("Превышен лимит запросов")
                throw ClaudeException.RateLimitException("Rate limit exceeded", null)
            }

            else -> {
                val errorBody = response.bodyAsText()
                logger.severe("Ошибка API (${response.status.value}): $errorBody")
                throw ClaudeException.ApiException(response.status.value, errorBody)
            }
        }
    }

    /**
     * Расчет стоимости на основе модели и количества токенов
     * Большинство моделей на HuggingFace бесплатны через Inference API
     */
    private fun calculateCost(model: String, inputTokens: Int, outputTokens: Int): Double? {
        // Для большинства open-source моделей на HF стоимость = 0
        // Но можно добавить расчет для платных моделей через API
        return when {
            model.contains("gpt", ignoreCase = true) -> {
                // Примерная стоимость для GPT моделей (если они доступны через HF)
                (inputTokens * 0.0005 / 1000) + (outputTokens * 0.0015 / 1000)
            }
            else -> null // Бесплатно
        }
    }

    override fun getProviderName(): String = "HuggingFace"

    override fun getDefaultModel(): String = config.defaultModel

    override fun close() {
        logger.info("Закрытие HuggingFace клиента")
        client.close()
    }
}

/**
 * Конфигурация для HuggingFace API
 */
data class HuggingFaceConfig(
    val apiKey: String,
    val baseUrl: String = "https://router.huggingface.co/v1/chat/completions",
    val defaultModel: String = "meta-llama/Llama-3.1-8B-Instruct",
    val defaultMaxTokens: Int = 1024,
    val requestTimeout: Long = 120000L // 2 минуты, т.к. HF может быть медленнее
) {
    init {
        require(apiKey.isNotBlank()) { "API key cannot be blank" }
    }
}

// Модели данных для HuggingFace API

@Serializable
data class HuggingFaceRequest(
    val model: String,
    val messages: List<HuggingFaceMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Double? = null,
    val stream: Boolean = false
)

@Serializable
data class HuggingFaceMessage(
    val role: String,
    val content: String
)

@Serializable
data class HuggingFaceResponse(
    val id: String? = null,
    val choices: List<HuggingFaceChoice>,
    val usage: HuggingFaceUsage? = null,
    val model: String? = null
)

@Serializable
data class HuggingFaceChoice(
    val index: Int? = null,
    val message: HuggingFaceMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class HuggingFaceUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)
