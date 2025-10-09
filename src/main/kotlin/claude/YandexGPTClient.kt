package org.kozyrev.claude

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.logging.Logger

/**
 * Модели данных для Yandex GPT API
 */
@Serializable
data class YandexGPTRequest(
    @SerialName("modelUri") val modelUri: String,
    @SerialName("completionOptions") val completionOptions: YandexCompletionOptions,
    val messages: List<YandexMessage>
)

@Serializable
data class YandexCompletionOptions(
    val stream: Boolean = false,
    val temperature: Double = 0.6,
    @SerialName("maxTokens") val maxTokens: String = "2000"
)

@Serializable
data class YandexMessage(
    val role: String,
    val text: String
)

@Serializable
data class YandexGPTResponse(
    val result: YandexResult? = null,
    val error: YandexError? = null
)

@Serializable
data class YandexResult(
    val alternatives: List<YandexAlternative>,
    val usage: YandexUsage,
    @SerialName("modelVersion") val modelVersion: String
)

@Serializable
data class YandexAlternative(
    val message: YandexMessage,
    val status: String
)

@Serializable
data class YandexUsage(
    @SerialName("inputTextTokens") val inputTextTokens: String,
    @SerialName("completionTokens") val completionTokens: String,
    @SerialName("totalTokens") val totalTokens: String
)

@Serializable
data class YandexError(
    val code: Int? = null,
    val message: String? = null,
    val details: List<kotlinx.serialization.json.JsonElement>? = null
)

/**
 * Конфигурация для Yandex GPT
 */
data class YandexGPTConfig(
    val apiKey: String,
    val folderId: String,
    val baseUrl: String = "https://llm.api.cloud.yandex.net/foundationModels/v1",
    val defaultModel: String = "yandexgpt-lite",
    val defaultMaxTokens: Int = 2000,
    val requestTimeout: Long = 60000L
) {
    init {
        require(apiKey.isNotBlank()) { "API key cannot be blank" }
        require(folderId.isNotBlank()) { "Folder ID cannot be blank" }
    }
}

/**
 * Клиент для работы с Yandex GPT API
 */
class YandexGPTClient(private val config: YandexGPTConfig) : AIClient {
    private val logger = Logger.getLogger(YandexGPTClient::class.java.name)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }

        install(Logging) {
            level = LogLevel.INFO
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    this@YandexGPTClient.logger.info(message)
                }
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeout
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 10000
        }

        defaultRequest {
            header("Authorization", "Api-Key ${config.apiKey}")
            header("x-folder-id", config.folderId)
        }
    }

    override suspend fun sendConversation(
        messages: List<Message>,
        model: String?,
        maxTokens: Int?,
        systemPrompt: String?,
        temperature: Double?
    ): AIResponse {
        val startTime = System.currentTimeMillis()

        // Преобразуем сообщения в формат Yandex GPT
        val yandexMessages = mutableListOf<YandexMessage>()

        // Добавляем системный промпт как первое сообщение от системы
        if (!systemPrompt.isNullOrBlank()) {
            yandexMessages.add(YandexMessage(role = "system", text = systemPrompt))
        }

        // Добавляем остальные сообщения
        messages.forEach { msg ->
            yandexMessages.add(
                YandexMessage(
                    role = msg.role,
                    text = msg.content
                )
            )
        }

        val modelToUse = model ?: config.defaultModel
        val modelUri = "gpt://${config.folderId}/${modelToUse}/latest"

        val request = YandexGPTRequest(
            modelUri = modelUri,
            completionOptions = YandexCompletionOptions(
                temperature = temperature ?: 0.6,
                maxTokens = (maxTokens ?: config.defaultMaxTokens).toString()
            ),
            messages = yandexMessages
        )

        try {
            logger.info("Запрос к Yandex GPT: modelUri=$modelUri, temperature=${temperature ?: 0.6}")

            val httpResponse = httpClient.post("${config.baseUrl}/completion") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val responseBody = httpResponse.body<String>()
            logger.info("Ответ от Yandex GPT (код ${httpResponse.status.value}): $responseBody")

            val response: YandexGPTResponse = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }.decodeFromString(responseBody)

            // Проверка на ошибку в ответе
            if (response.error != null) {
                val errorMsg = "Yandex GPT API error (${response.error.code}): ${response.error.message}"
                logger.severe(errorMsg)
                throw ClaudeException.ApiException(
                    response.error.code ?: 500,
                    errorMsg
                )
            }

            // Проверка на наличие result
            if (response.result == null) {
                logger.severe("Отсутствует поле 'result' в ответе от Yandex GPT")
                throw ClaudeException.ApiException(500, "Invalid response from Yandex GPT: missing 'result' field")
            }

            val endTime = System.currentTimeMillis()
            val responseTimeMs = endTime - startTime

            val content = response.result.alternatives.firstOrNull()?.message?.text
                ?: throw ClaudeException.ApiException(500, "No response from Yandex GPT")

            val inputTokens = response.result.usage.inputTextTokens.toIntOrNull() ?: 0
            val outputTokens = response.result.usage.completionTokens.toIntOrNull() ?: 0
            val totalTokens = response.result.usage.totalTokens.toIntOrNull() ?: 0

            return AIResponse(
                content = content,
                metrics = ResponseMetrics(
                    responseTimeMs = responseTimeMs,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    totalTokens = totalTokens,
                    estimatedCostUsd = null
                ),
                modelName = response.result.modelVersion,
                providerName = "Yandex GPT"
            )
        } catch (e: ClaudeException) {
            throw e
        } catch (e: Exception) {
            logger.severe("Ошибка при обращении к Yandex GPT: ${e.message}")
            e.printStackTrace()
            throw ClaudeException.NetworkException("Yandex GPT API error: ${e.message}", e)
        }
    }

    override fun getProviderName(): String = "Yandex GPT"

    override fun getDefaultModel(): String = config.defaultModel

    override fun close() {
        httpClient.close()
    }
}

/**
 * Builder для создания YandexGPTClient
 */
class YandexGPTClientBuilder {
    private var apiKey: String? = null
    private var folderId: String? = null
    private var baseUrl: String = "https://llm.api.cloud.yandex.net/foundationModels/v1"
    private var defaultModel: String = "yandexgpt-lite"
    private var defaultMaxTokens: Int = 2000
    private var requestTimeout: Long = 60000L

    fun apiKey(key: String) = apply { this.apiKey = key }
    fun folderId(id: String) = apply { this.folderId = id }
    fun baseUrl(url: String) = apply { this.baseUrl = url }
    fun defaultModel(model: String) = apply { this.defaultModel = model }
    fun defaultMaxTokens(tokens: Int) = apply { this.defaultMaxTokens = tokens }
    fun requestTimeout(timeout: Long) = apply { this.requestTimeout = timeout }

    fun build(): YandexGPTClient {
        val key = apiKey ?: throw IllegalStateException("API key is required")
        val folder = folderId ?: throw IllegalStateException("Folder ID is required")

        val config = YandexGPTConfig(
            apiKey = key,
            folderId = folder,
            baseUrl = baseUrl,
            defaultModel = defaultModel,
            defaultMaxTokens = defaultMaxTokens,
            requestTimeout = requestTimeout
        )

        return YandexGPTClient(config)
    }
}
