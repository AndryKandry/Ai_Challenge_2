package org.kozyrev.claude

/**
 * Общий интерфейс для всех AI клиентов (Claude, HuggingFace и т.д.)
 */
interface AIClient : AutoCloseable {
    /**
     * Отправляет разговор в модель и получает ответ с метриками
     */
    suspend fun sendConversation(
        messages: List<Message>,
        model: String? = null,
        maxTokens: Int? = null,
        systemPrompt: String? = null,
        temperature: Double? = null
    ): AIResponse

    /**
     * Возвращает имя провайдера (Claude, HuggingFace и т.д.)
     */
    fun getProviderName(): String

    /**
     * Возвращает имя используемой модели по умолчанию
     */
    fun getDefaultModel(): String
}

/**
 * Унифицированный ответ от любого AI провайдера
 */
data class AIResponse(
    val content: String,
    val metrics: ResponseMetrics,
    val modelName: String,
    val providerName: String
)

/**
 * Метрики ответа от модели
 */
data class ResponseMetrics(
    val responseTimeMs: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val estimatedCostUsd: Double? = null
) {
    override fun toString(): String {
        val costStr = estimatedCostUsd?.let { String.format("%.6f USD", it) } ?: "Бесплатно"
        return """
            Время ответа: ${responseTimeMs}ms
            Токены: вход=$inputTokens, выход=$outputTokens, всего=$totalTokens
            Стоимость: $costStr
        """.trimIndent()
    }
}
