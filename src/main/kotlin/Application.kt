package org.kozyrev

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.kozyrev.claude.ChatManager
import org.kozyrev.claude.ClaudeClientBuilder
import org.kozyrev.ui.ChatScreen
import org.kozyrev.ui.ModelComparisonScreen
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
            title = "Claude AI Chat & Model Comparison",
            state = rememberWindowState(width = 1000.dp, height = 700.dp)
        ) {
            MainScreen(chatManager)
        }
    }
}

@Composable
fun MainScreen(chatManager: ChatManager) {
    var selectedTab by remember { mutableStateOf(0) }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("💬 Чат") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("🔬 Сравнение моделей") }
                )
            }

            when (selectedTab) {
                0 -> ChatScreen(chatManager)
                1 -> ModelComparisonScreen()
            }
        }
    }
}
