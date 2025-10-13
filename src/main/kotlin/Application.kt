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
import org.kozyrev.claude.YandexGPTClientBuilder
import org.kozyrev.ui.ChatScreen
import org.kozyrev.ui.ModelComparisonScreen
import org.kozyrev.ui.AgentInteractionScreen
import org.kozyrev.ui.TokenComparisonScreen
import java.util.logging.Logger

fun main() {
    // Настройка логгера
    val logger = Logger.getLogger("Main")

    // Получение API ключа Claude из переменных окружения
    val claudeApiKey = System.getenv("ANTHROPIC_API_KEY")
        ?: throw IllegalStateException("ANTHROPIC_API_KEY not found")

    // Создание Claude клиента с помощью builder
    val claudeClient = ClaudeClientBuilder()
        .apiKey(claudeApiKey)
        .defaultMaxTokens(1024)
        .build()

    // Попытка создать Yandex GPT клиент (опционально)
    val yandexClient = try {
        val yandexApiKey = System.getenv("YANDEX_API_KEY")
        val yandexFolderId = System.getenv("YANDEX_FOLDER_ID")

        if (yandexApiKey != null && yandexFolderId != null) {
            logger.info("Инициализация Yandex GPT клиента...")
            YandexGPTClientBuilder()
                .apiKey(yandexApiKey)
                .folderId(yandexFolderId)
                .defaultMaxTokens(2000)
                .build()
        } else {
            logger.warning("Yandex GPT не настроен (отсутствуют YANDEX_API_KEY или YANDEX_FOLDER_ID)")
            null
        }
    } catch (e: Exception) {
        logger.warning("Не удалось инициализировать Yandex GPT: ${e.message}")
        null
    }

    // Создание ChatManager с обоими клиентами
    val chatManager = ChatManager(
        claudeClient = claudeClient,
        yandexClient = yandexClient,
        systemPrompt = """
            Ты - дружелюбный AI помощник.

            Помогай пользователям с их вопросами, будь вежливым и конструктивным.
            Давай чёткие и понятные ответы на русском языке.
        """.trimIndent()
    )

    // Запуск GUI приложения
    application {
        Window(
            onCloseRequest = {
                claudeClient.close()
                yandexClient?.close()
                exitApplication()
            },
            title = "Claude AI: Chat, Model Comparison, Token Analysis & Agents",
            state = rememberWindowState(width = 1000.dp, height = 700.dp)
        ) {
            MainScreen(chatManager)
        }
    }
}

@Composable
fun MainScreen(chatManager: ChatManager) {
    var selectedTab by remember { mutableStateOf(0) }

    // Получаем AI клиент для агентов (используем Claude)
    val claudeApiKey = System.getenv("ANTHROPIC_API_KEY") ?: ""
    val aiClient = remember {
        ClaudeClientBuilder()
            .apiKey(claudeApiKey)
            .defaultMaxTokens(2048)
            .build()
    }

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
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("🔢 Анализ токенов") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("🤖 Агенты") }
                )
            }

            when (selectedTab) {
                0 -> ChatScreen(chatManager)
                1 -> ModelComparisonScreen()
                2 -> TokenComparisonScreen()
                3 -> AgentInteractionScreen(aiClient)
            }
        }
    }
}
