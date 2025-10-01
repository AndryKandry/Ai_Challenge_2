package org.kozyrev.claude

class ClaudeClientBuilder {
    private var apiKey: String? = null
    private var baseUrl: String = "https://api.anthropic.com/v1"
    private var anthropicVersion: String = "2023-06-01"
    private var defaultModel: String = "claude-3-haiku-20240307"
    private var defaultMaxTokens: Int = 1024
    private var requestTimeout: Long = 60000L

    fun apiKey(key: String) = apply { this.apiKey = key }
    fun baseUrl(url: String) = apply { this.baseUrl = url }
    fun anthropicVersion(version: String) = apply { this.anthropicVersion = version }
    fun defaultModel(model: String) = apply { this.defaultModel = model }
    fun defaultMaxTokens(tokens: Int) = apply { this.defaultMaxTokens = tokens }
    fun requestTimeout(timeout: Long) = apply { this.requestTimeout = timeout }

    fun build(): ClaudeClient {
        val key = apiKey ?: throw kotlin.IllegalStateException("API key is required")

        val config = ClaudeConfig(
            apiKey = key,
            baseUrl = baseUrl,
            anthropicVersion = anthropicVersion,
            defaultModel = defaultModel,
            defaultMaxTokens = defaultMaxTokens,
            requestTimeout = requestTimeout
        )

        return ClaudeClient(config)
    }
}