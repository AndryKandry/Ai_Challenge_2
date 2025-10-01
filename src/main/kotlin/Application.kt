package org.kozyrev

import org.kozyrev.claude.ClaudeClientBuilder
import org.kozyrev.claude.ClaudeException
import java.io.File
import java.util.*
import java.util.logging.Logger

fun main(args: Array<String>) {
    val propertiesFile = File("local.properties")
    val properties = Properties()
    properties.load(propertiesFile.inputStream())

    // Настройка логгера
    val logger = Logger.getLogger("Main")

    // Получение API ключа из переменных окружения
    val apiKey = properties.getProperty("ANTHROPIC_API_KEY")
        ?: System.getenv("ANTHROPIC_API_KEY")
        ?: throw IllegalStateException("ANTHROPIC_API_KEY not found")

    // Создание клиента с помощью builder
    val client = ClaudeClientBuilder()
        .apiKey(apiKey)
        .defaultMaxTokens(1024)
        .build()

    client.use { claude ->
        try {
            val chatManager = ChatManager(client, systemPrompt = "Ты - helpful AI assistant")
            chatManager.startChat()
        } catch (e: ClaudeException.AuthenticationException) {
            logger.severe("Ошибка аутентификации: ${e.message}")
        } catch (e: ClaudeException.RateLimitException) {
            logger.warning("Превышен лимит запросов: ${e.message}")
            e.retryAfter?.let {
                logger.info("Можно повторить через $it секунд")
            }
        } catch (e: ClaudeException.NetworkException) {
            logger.warning("Сетевая ошибка: ${e.message}")
        } catch (e: ClaudeException) {
            logger.severe("Ошибка Claude API: ${e.message}")
        }
    }
}
