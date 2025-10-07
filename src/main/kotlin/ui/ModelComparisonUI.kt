package org.kozyrev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.kozyrev.claude.*

@Composable
fun ModelComparisonScreen() {
    var testPrompt by remember { mutableStateOf("Объясни простыми словами, что такое квантовая запутанность. Ответ должен быть не более 3 предложений.") }
    var temperature by remember { mutableStateOf(0.7f) }
    var isComparing by remember { mutableStateOf(false) }
    var comparisonResult by remember { mutableStateOf<ComparisonResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp)
        ) {
            // Заголовок
            Text(
                text = "🔬 Сравнение AI моделей",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Сравните производительность и качество различных моделей",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Панель настроек
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Настройки тестирования",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Поле для тестового промпта
                    OutlinedTextField(
                        value = testPrompt,
                        onValueChange = { testPrompt = it },
                        label = { Text("Тестовый запрос") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        minLines = 3,
                        maxLines = 5,
                        enabled = !isComparing
                    )

                    // Ползунок температуры
                    Text(
                        text = "Температура: ${String.format("%.1f", temperature)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..1f,
                        steps = 9,
                        enabled = !isComparing,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF1976D2),
                            activeTrackColor = Color(0xFF1976D2),
                            inactiveTrackColor = Color(0xFFBBDEFB)
                        )
                    )

                    // Кнопка запуска сравнения
                    Button(
                        onClick = {
                            if (testPrompt.isNotBlank()) {
                                scope.launch {
                                    isComparing = true
                                    errorMessage = null
                                    comparisonResult = null

                                    try {
                                        val result = runComparison(testPrompt, temperature.toDouble())
                                        comparisonResult = result
                                    } catch (e: Exception) {
                                        errorMessage = "Ошибка: ${e.message}"
                                    } finally {
                                        isComparing = false
                                    }
                                }
                            }
                        },
                        enabled = !isComparing && testPrompt.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        if (isComparing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Тестирование...")
                        } else {
                            Text("Запустить сравнение")
                        }
                    }
                }
            }

            // Ошибка
            errorMessage?.let { error ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "⚠️ $error",
                        color = Color(0xFFC62828),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Результаты
            comparisonResult?.let { result ->
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Заголовок результатов
                    item {
                        Text(
                            text = "Результаты сравнения",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // Карточки с результатами каждой модели
                    items(result.results) { modelResult ->
                        ModelResultCard(modelResult)
                    }

                    // Сводная таблица
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SummaryTable(result)
                    }

                    // Победители
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        WinnersCard(result)
                    }
                }
            }
        }
    }
}

@Composable
fun ModelResultCard(result: ModelResult) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (result.error == null) Color.White else Color(0xFFFFEBEE),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Заголовок с именем модели
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.providerName,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = result.modelName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (result.error == null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF4CAF50)
                    ) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF44336)
                    ) {
                        Text(
                            text = "✗",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (result.error != null) {
                Text(
                    text = "Ошибка: ${result.error}",
                    color = Color(0xFFC62828),
                    fontSize = 12.sp
                )
            } else {
                // Ответ модели
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Text(
                        text = result.response.take(300) + if (result.response.length > 300) "..." else "",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Метрики
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricChip("⚡ ${result.metrics.responseTimeMs}ms", Color(0xFFE3F2FD))
                    MetricChip("📥 ${result.metrics.inputTokens}tok", Color(0xFFFFF3E0))
                    MetricChip("📤 ${result.metrics.outputTokens}tok", Color(0xFFE8F5E9))

                    val costStr = result.metrics.estimatedCostUsd?.let {
                        String.format("$%.4f", it)
                    } ?: "Бесплатно"
                    MetricChip("💰 $costStr", Color(0xFFF3E5F5))
                }
            }
        }
    }
}

@Composable
fun MetricChip(text: String, backgroundColor: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SummaryTable(result: ComparisonResult) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Сравнительная таблица",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val successfulResults = result.results.filter { it.error == null }

            if (successfulResults.isEmpty()) {
                Text(
                    text = "Нет успешных результатов",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            } else {
                // Заголовки таблицы
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Модель", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(2f))
                    Text("Время", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text("Токены", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text("Цена", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Строки таблицы
                successfulResults.forEach { modelResult ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${modelResult.providerName}/${modelResult.modelName}".take(20),
                            fontSize = 10.sp,
                            modifier = Modifier.weight(2f)
                        )
                        Text("${modelResult.metrics.responseTimeMs}ms", fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("${modelResult.metrics.totalTokens}", fontSize = 10.sp, modifier = Modifier.weight(1f))

                        val costStr = modelResult.metrics.estimatedCostUsd?.let {
                            String.format("$%.4f", it)
                        } ?: "Free"
                        Text(costStr, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun WinnersCard(result: ComparisonResult) {
    val successfulResults = result.results.filter { it.error == null }

    if (successfulResults.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFFF9C4),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🏆 Победители",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val fastest = successfulResults.minByOrNull { it.metrics.responseTimeMs }
            fastest?.let {
                Text(
                    text = "⚡ Самая быстрая: ${it.providerName}/${it.modelName} (${it.metrics.responseTimeMs}мс)",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            val cheapest = successfulResults.minByOrNull { it.metrics.estimatedCostUsd ?: 0.0 }
            cheapest?.let {
                val costStr = it.metrics.estimatedCostUsd?.let { cost ->
                    String.format("$%.6f", cost)
                } ?: "Бесплатно"
                Text(
                    text = "💰 Самая дешевая: ${it.providerName}/${it.modelName} ($costStr)",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            val mostTokens = successfulResults.maxByOrNull { it.metrics.outputTokens }
            mostTokens?.let {
                Text(
                    text = "📝 Самый подробный: ${it.providerName}/${it.modelName} (${it.metrics.outputTokens} токенов)",
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Функция для запуска сравнения моделей
 */
private suspend fun runComparison(testPrompt: String, temperature: Double): ComparisonResult {
    val claudeApiKey = System.getenv("ANTHROPIC_API_KEY")
    val hfApiKey = System.getenv("HUGGINGFACE_API_KEY")

    val clients = mutableListOf<AIClient>()

    // Добавляем Claude если доступен
    if (!claudeApiKey.isNullOrBlank()) {
        clients.add(
            ClaudeClient(
                ClaudeConfig(
                    apiKey = claudeApiKey,
                    defaultModel = "claude-3-haiku-20240307"
                )
            )
        )
    }

    // Добавляем HuggingFace модели если доступны
    if (!hfApiKey.isNullOrBlank()) {
        // Модель 1: Meta Llama 3.1 8B (популярная и быстрая)
        clients.add(
            HuggingFaceClient(
                HuggingFaceConfig(
                    apiKey = hfApiKey,
                    defaultModel = "meta-llama/Llama-3.1-8B-Instruct"
                )
            )
        )

        // Модель 2: Qwen (китайская модель, хорошая производительность)
        clients.add(
            HuggingFaceClient(
                HuggingFaceConfig(
                    apiKey = hfApiKey,
                    defaultModel = "Qwen/Qwen2.5-7B-Instruct"
                )
            )
        )
    }

    if (clients.isEmpty()) {
        throw IllegalStateException("Не установлены API ключи. Установите ANTHROPIC_API_KEY или HUGGINGFACE_API_KEY")
    }

    val comparison = ModelComparison()

    try {
        return comparison.compareModels(
            clients = clients,
            testPrompt = testPrompt,
            temperature = temperature
        )
    } finally {
        // Закрываем все клиенты
        clients.forEach { it.close() }
    }
}
