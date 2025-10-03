package org.kozyrev.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.text.isNotBlank

@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<Message>,
    val system: String? = null,
    val temperature: Double? = null
)

@Serializable
data class Message(
    val role: String,
    val content: String,
    @SerialName("is_system_message") val isSystemMessage: Boolean = false
)

@Serializable
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<Content>,
    val model: String,
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: Usage? = null
)

@Serializable
data class Content(
    val type: String,
    val text: String
)

@Serializable
data class Usage(
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int
)

@Serializable
data class ErrorResponse(
    val type: String,
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val type: String,
    val message: String
)

data class ClaudeConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.anthropic.com/v1",
    val anthropicVersion: String = "2023-06-01",
    val defaultModel: String = "claude-3-haiku-20240307",//"claude-sonnet-4-20250514",
    val defaultMaxTokens: Int = 1024,
    val requestTimeout: Long = 60000L
) {
    init {
        require(apiKey.isNotBlank()) { "API key cannot be blank" }
    }
}