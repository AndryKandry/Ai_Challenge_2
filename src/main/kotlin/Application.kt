package org.kozyrev

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.kozyrev.claude.ChatManager
import org.kozyrev.claude.ClaudeClientBuilder
import org.kozyrev.ui.ChatScreen
import java.util.logging.Logger

fun main() {
    // Настройка логгера
    val logger = Logger.getLogger("Main")

    // Получение API ключа из переменных окружения
    val apiKey = System.getenv("ANTHROPIC_API_KEY")
        ?: throw IllegalStateException("ANTHROPIC_API_KEY not found")

    // Создание клиента с помощью builder
    val client = ClaudeClientBuilder()
        .apiKey(apiKey)
        .defaultMaxTokens(1024)
        .build()

    // Создание ChatManager
    val chatManager = ChatManager(client, systemPrompt = "Ты - helpful AI assistant")

    // Запуск GUI приложения
    application {
        Window(
            onCloseRequest = {
                client.close()
                exitApplication()
            },
            title = "Claude AI Chat",
            state = rememberWindowState(width = 800.dp, height = 600.dp)
        ) {
            ChatScreen(chatManager)
        }
    }
}
