package org.kozyrev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.kozyrev.agents.AgentOrchestrator
import org.kozyrev.agents.TaskStatus
import org.kozyrev.claude.AIClient
import org.kozyrev.claude.ClaudeClientBuilder
import org.kozyrev.claude.YandexGPTClientBuilder

enum class AIProvider {
    CLAUDE,
    YANDEX_GPT
}

/**
 * UI для визуализации взаимодействия агентов
 */
@Composable
fun AgentInteractionScreen(defaultAiClient: AIClient) {
    var topic by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var writerStatus by remember { mutableStateOf(TaskStatus.PENDING) }
    var reviewerStatus by remember { mutableStateOf(TaskStatus.PENDING) }
    var selectedProvider by remember { mutableStateOf(AIProvider.CLAUDE) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Создаем клиенты для разных провайдеров
    val claudeClient = remember {
        val apiKey = System.getenv("CLAUDE_API_KEY") ?: ""
        ClaudeClientBuilder()
            .apiKey(apiKey)
            .defaultMaxTokens(2048)
            .build()
    }

    val yandexClient = remember {
        try {
            val apiKey = System.getenv("YANDEX_API_KEY")
            val folderId = System.getenv("YANDEX_FOLDER_ID")
            if (apiKey != null && folderId != null) {
                YandexGPTClientBuilder()
                    .apiKey(apiKey)
                    .folderId(folderId)
                    .defaultMaxTokens(2000)
                    .build()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    val isYandexAvailable = yandexClient != null

    // Выбираем текущий клиент в зависимости от провайдера
    val currentClient = when (selectedProvider) {
        AIProvider.CLAUDE -> claudeClient
        AIProvider.YANDEX_GPT -> yandexClient ?: claudeClient
    }

    val orchestrator = remember(currentClient) { AgentOrchestrator(currentClient) }

    // Настраиваем коллбэк для отслеживания прогресса
    LaunchedEffect(orchestrator) {
        orchestrator.onProgressUpdate = { message, status ->
            progressText = message
            when {
                message.contains("Писатель") -> writerStatus = status
                message.contains("Ревьюер") -> reviewerStatus = status
            }
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp)
        ) {
            // Заголовок
            Text(
                text = "🤖 Взаимодействие AI Агентов",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Описание системы
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE3F2FD),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Система из двух агентов:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1️⃣ Агент-Писатель - генерирует статью по теме",
                        fontSize = 12.sp,
                        color = Color(0xFF1976D2)
                    )
                    Text(
                        text = "2️⃣ Агент-Ревьюер - проверяет качество статьи",
                        fontSize = 12.sp,
                        color = Color(0xFF1976D2)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Результат работы первого агента передается второму",
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = Color(0xFF1565C0).copy(alpha = 0.7f)
                    )
                }
            }

            // Выбор AI модели
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF3E5F5),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🤖 Выбор AI модели для агентов",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6A1B9A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedProvider == AIProvider.CLAUDE) Color(0xFF9C27B0) else Color(0xFFBDBDBD),
                            modifier = Modifier.clickable(enabled = !isRunning) {
                                selectedProvider = AIProvider.CLAUDE
                            }
                        ) {
                            Text(
                                text = "🔵 Claude",
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedProvider == AIProvider.YANDEX_GPT) Color(0xFF9C27B0) else Color(0xFFBDBDBD),
                            modifier = Modifier.clickable(enabled = !isRunning && isYandexAvailable) {
                                if (isYandexAvailable) {
                                    selectedProvider = AIProvider.YANDEX_GPT
                                }
                            }
                        ) {
                            Text(
                                text = "🟡 Yandex GPT",
                                fontSize = 12.sp,
                                color = if (isYandexAvailable) Color.White else Color.Gray,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (!isYandexAvailable) {
                        Text(
                            text = "⚠️ Yandex GPT недоступен (не настроен API ключ)",
                            fontSize = 10.sp,
                            color = Color(0xFF6A1B9A).copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Text(
                        text = "Текущая модель: ${if (selectedProvider == AIProvider.CLAUDE) "Claude Sonnet" else "Yandex GPT"}",
                        fontSize = 10.sp,
                        color = Color(0xFF6A1B9A),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // Статусы агентов
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Статус агента-писателя
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (writerStatus) {
                        TaskStatus.PENDING -> Color(0xFFE0E0E0)
                        TaskStatus.RUNNING -> Color(0xFFFFF9C4)
                        TaskStatus.COMPLETED -> Color(0xFFC8E6C9)
                        TaskStatus.FAILED -> Color(0xFFFFCDD2)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "✍️ Писатель",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (writerStatus) {
                                TaskStatus.PENDING -> "Ожидает"
                                TaskStatus.RUNNING -> "Работает..."
                                TaskStatus.COMPLETED -> "Готово ✓"
                                TaskStatus.FAILED -> "Ошибка ✗"
                            },
                            fontSize = 10.sp
                        )
                    }
                }

                // Стрелка
                Box(
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = "→",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9C27B0)
                    )
                }

                // Статус агента-ревьюера
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (reviewerStatus) {
                        TaskStatus.PENDING -> Color(0xFFE0E0E0)
                        TaskStatus.RUNNING -> Color(0xFFFFF9C4)
                        TaskStatus.COMPLETED -> Color(0xFFC8E6C9)
                        TaskStatus.FAILED -> Color(0xFFFFCDD2)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔍 Ревьюер",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (reviewerStatus) {
                                TaskStatus.PENDING -> "Ожидает"
                                TaskStatus.RUNNING -> "Проверяет..."
                                TaskStatus.COMPLETED -> "Готово ✓"
                                TaskStatus.FAILED -> "Ошибка ✗"
                            },
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Прогресс
            if (progressText.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF3E0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFFFF6F00),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = progressText,
                            fontSize = 12.sp,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }

            // Поле ввода темы
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                label = { Text("Введите тему для статьи") },
                placeholder = { Text("Например: Искусственный интеллект в медицине") },
                enabled = !isRunning,
                singleLine = true
            )

            // Кнопка запуска
            Button(
                onClick = {
                    if (topic.isNotBlank() && !isRunning) {
                        isRunning = true
                        resultText = ""
                        writerStatus = TaskStatus.PENDING
                        reviewerStatus = TaskStatus.PENDING
                        progressText = "Запуск процесса с ${if (selectedProvider == AIProvider.CLAUDE) "Claude" else "Yandex GPT"}..."

                        scope.launch {
                            try {
                                val result = orchestrator.runAgentWorkflow(topic)
                                resultText = orchestrator.formatResult(result)

                                // Прокрутка к результату
                                listState.animateScrollToItem(0)
                            } catch (e: Exception) {
                                resultText = "❌ Ошибка: ${e.message}"
                                writerStatus = TaskStatus.FAILED
                                reviewerStatus = TaskStatus.FAILED
                            } finally {
                                isRunning = false
                                progressText = "Процесс завершен"
                            }
                        }
                    }
                },
                enabled = !isRunning && topic.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9C27B0)
                )
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Агенты работают...")
                } else {
                    Text("🚀 Запустить агентов")
                }
            }

            // Результат
            if (resultText.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = resultText,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(16.dp),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Введите тему и нажмите кнопку для запуска",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}
