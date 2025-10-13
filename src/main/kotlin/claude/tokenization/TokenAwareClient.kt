package org.kozyrev.claude.tokenization

import kotlinx.serialization.Serializable
import org.kozyrev.claude.AIClient
import org.kozyrev.claude.AIResponse
import org.kozyrev.claude.Message
import org.kozyrev.claude.ResponseMetrics
import java.util.logging.Logger

/**
 * Конфигурация для обработки токенов
 */
data class TokenConfig(
    val modelName: String,
    val maxContextTokens: Int,
    val expectedResponseTokens: Int = 1024,
    val compressionStrategy: CompressionStrategy? = null,
    val tokenizer: Tokenizer? = null
) {
    fun getTokenBudget(): Int {
        return maxContextTokens - expectedResponseTokens
    }
}

/**
 * Расширенные метрики с информацией о трансформациях
 */
@Serializable
data class EnhancedMetrics(
    val originalPromptTokens: Int,
    val finalPromptTokens: Int,
    val responseTokens: Int,
    val totalTokens: Int,
    val transformationApplied: String,
    val timestamp: Long,
    val responseTimeMs: Long,
    val estimatedCostUsd: Double? = null
) {
    fun toJson(): String {
        return """
            {
              "tokens": {
                "input": $finalPromptTokens,
                "output": $responseTokens,
                "total": $totalTokens
              },
              "transformation": {
                "original_tokens": $originalPromptTokens,
                "final_tokens": $finalPromptTokens,
                "type": "$transformationApplied"
              },
              "performance": {
                "response_time_ms": $responseTimeMs,
                "timestamp": $timestamp
              },
              "cost": ${estimatedCostUsd ?: "null"}
            }
        """.trimIndent()
    }
}

/**
 * Обёртка над AI клиентом с поддержкой автоматической обработки токенов
 */
class TokenAwareClient(
    private val underlyingClient: AIClient,
    private val config: TokenConfig
) : AIClient by underlyingClient {

    private val logger = Logger.getLogger(TokenAwareClient::class.java.name)
    private val tokenizer: Tokenizer = config.tokenizer ?: TokenizerFactory.forModel(config.modelName)

    /**
     * Отправляет запрос с автоматической обработкой токенов
     */
    override suspend fun sendConversation(
        messages: List<Message>,
        model: String?,
        maxTokens: Int?,
        systemPrompt: String?,
        temperature: Double?
    ): AIResponse {
        val modelToUse = model ?: config.modelName

        // Подсчитываем токены в исходном запросе
        val fullPrompt = buildFullPrompt(messages, systemPrompt)
        val originalTokens = tokenizer.countTokens(fullPrompt)

        logger.info("""
            Original prompt tokens: $originalTokens
            Token budget: ${config.getTokenBudget()}
            Model: $modelToUse
        """.trimIndent())

        // Применяем сжатие если необходимо
        val (processedMessages, compressionResult) = if (originalTokens > config.getTokenBudget()) {
            logger.warning("Prompt exceeds token budget, applying compression...")
            applyCompression(messages, systemPrompt)
        } else {
            Pair(messages, null)
        }

        // Логируем трансформацию
        compressionResult?.let {
            logger.info(it.toLogString())
        }

        // Отправляем запрос
        val response = underlyingClient.sendConversation(
            messages = processedMessages,
            model = modelToUse,
            maxTokens = maxTokens,
            systemPrompt = systemPrompt,
            temperature = temperature
        )

        // Возвращаем ответ с дополнительной информацией в метриках
        return response
    }

    /**
     * Отправляет запрос с полными метриками включая информацию о трансформациях
     */
    suspend fun sendConversationWithEnhancedMetrics(
        messages: List<Message>,
        model: String? = null,
        maxTokens: Int? = null,
        systemPrompt: String? = null,
        temperature: Double? = null
    ): Pair<AIResponse, EnhancedMetrics> {
        val startTime = System.currentTimeMillis()

        val fullPrompt = buildFullPrompt(messages, systemPrompt)
        val originalTokens = tokenizer.countTokens(fullPrompt)

        val (processedMessages, compressionResult) = if (originalTokens > config.getTokenBudget()) {
            applyCompression(messages, systemPrompt)
        } else {
            Pair(messages, null)
        }

        val finalPrompt = buildFullPrompt(processedMessages, systemPrompt)
        val finalTokens = tokenizer.countTokens(finalPrompt)

        val response = underlyingClient.sendConversation(
            messages = processedMessages,
            model = model,
            maxTokens = maxTokens,
            systemPrompt = systemPrompt,
            temperature = temperature
        )

        val endTime = System.currentTimeMillis()

        val enhancedMetrics = EnhancedMetrics(
            originalPromptTokens = originalTokens,
            finalPromptTokens = finalTokens,
            responseTokens = response.metrics.outputTokens,
            totalTokens = finalTokens + response.metrics.outputTokens,
            transformationApplied = compressionResult?.transformationType ?: "none",
            timestamp = startTime,
            responseTimeMs = endTime - startTime,
            estimatedCostUsd = response.metrics.estimatedCostUsd
        )

        return Pair(response, enhancedMetrics)
    }

    /**
     * Применяет стратегию сжатия к сообщениям
     */
    private suspend fun applyCompression(
        messages: List<Message>,
        systemPrompt: String?
    ): Pair<List<Message>, CompressionResult> {
        val strategy = config.compressionStrategy
            ?: TruncationStrategy(TruncationStrategy.TruncationMode.KEEP_START)

        // Объединяем все сообщения в один текст для сжатия
        val combinedText = messages.joinToString("\n") { "${it.role}: ${it.content}" }

        val result = strategy.process(
            text = combinedText,
            tokenizer = tokenizer,
            targetTokens = config.getTokenBudget()
        )

        // Преобразуем обратно в сообщения
        val processedMessages = if (result.transformationType == "none") {
            messages
        } else {
            // Для упрощения создаём одно сообщение с обработанным текстом
            listOf(Message(role = "user", content = result.processedText))
        }

        return Pair(processedMessages, result)
    }

    /**
     * Строит полный промпт из сообщений и системного промпта
     */
    private fun buildFullPrompt(messages: List<Message>, systemPrompt: String?): String {
        val parts = mutableListOf<String>()
        systemPrompt?.let { parts.add("System: $it") }
        parts.addAll(messages.map { "${it.role}: ${it.content}" })
        return parts.joinToString("\n")
    }

    /**
     * Подсчитывает токены в тексте
     */
    fun countTokens(text: String): Int {
        return tokenizer.countTokens(text)
    }

    /**
     * Возвращает используемый токенизатор
     */
    fun getTokenizer(): Tokenizer = tokenizer
}
