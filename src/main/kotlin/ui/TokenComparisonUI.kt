package org.kozyrev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kozyrev.claude.*
import org.kozyrev.claude.tokenization.*

/**
 * Информация о доступности провайдера
 */
data class ProviderInfo(
    val name: String,
    val isAvailable: Boolean,
    val missingVars: List<String>
)

/**
 * Проверяет доступные провайдеры на основе переменных окружения
 */
fun checkAvailableProviders(): Map<String, ProviderInfo> {
    return mapOf(
        "claude" to ProviderInfo(
            name = "Claude",
            isAvailable = System.getenv("ANTHROPIC_API_KEY") != null,
            missingVars = listOfNotNull(
                if (System.getenv("ANTHROPIC_API_KEY") == null) "ANTHROPIC_API_KEY" else null
            )
        ),
        "huggingface" to ProviderInfo(
            name = "HuggingFace",
            isAvailable = System.getenv("HUGGINGFACE_API_KEY") != null,
            missingVars = listOfNotNull(
                if (System.getenv("HUGGINGFACE_API_KEY") == null) "HUGGINGFACE_API_KEY" else null
            )
        ),
        "yandex" to ProviderInfo(
            name = "Yandex GPT",
            isAvailable = System.getenv("YANDEX_API_KEY") != null && System.getenv("YANDEX_FOLDER_ID") != null,
            missingVars = listOfNotNull(
                if (System.getenv("YANDEX_API_KEY") == null) "YANDEX_API_KEY" else null,
                if (System.getenv("YANDEX_FOLDER_ID") == null) "YANDEX_FOLDER_ID" else null
            )
        )
    )
}

/**
 * UI для сравнения обработки токенов разными моделями
 */
@Composable
fun TokenComparisonScreen() {
    var selectedProvider by remember { mutableStateOf("claude") }
    var isRunning by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ComparisonReport?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Проверяем наличие необходимых переменных окружения
    val availableProviders by remember {
        mutableStateOf(checkAvailableProviders())
    }

    val scope = rememberCoroutineScope()

    // Автоматически выбираем первый доступный провайдер
    LaunchedEffect(Unit) {
        val firstAvailable = availableProviders.entries.firstOrNull { it.value.isAvailable }?.key
        if (firstAvailable != null) {
            selectedProvider = firstAvailable
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
                text = "🔢 Сравнение обработки токенов",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Сравнение коротких, длинных и превышающих лимит запросов",
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
                        text = "Настройки",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Выбор провайдера
                    Text(
                        text = "Провайдер AI",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableProviders.forEach { (key, info) ->
                            ProviderButton(
                                label = info.name,
                                value = key,
                                selectedValue = selectedProvider,
                                enabled = !isRunning && info.isAvailable,
                                onSelect = { selectedProvider = it }
                            )
                        }
                    }

                    // Статус выбранного провайдера
                    val currentProvider = availableProviders[selectedProvider]
                    if (currentProvider != null) {
                        if (currentProvider.isAvailable) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "✓",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${currentProvider.name} настроен и готов к использованию",
                                        fontSize = 12.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFF3E0),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "⚠",
                                            color = Color(0xFFFF9800),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${currentProvider.name} не настроен",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Отсутствуют переменные окружения:",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6D4C41)
                                    )
                                    currentProvider.missingVars.forEach { varName ->
                                        Text(
                                            text = "  • $varName",
                                            fontSize = 11.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = Color(0xFF6D4C41),
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Кнопка запуска
                    val canRun = availableProviders[selectedProvider]?.isAvailable == true
                    Button(
                        onClick = {
                            scope.launch {
                                isRunning = true
                                errorMessage = null
                                report = null

                                try {
                                    report = runComparisonFromEnv(selectedProvider)
                                } catch (e: Exception) {
                                    errorMessage = "Ошибка: ${e.message}"
                                } finally {
                                    isRunning = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isRunning && canRun,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1976D2)
                        )
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Выполняется...")
                        } else {
                            Text("▶ Запустить сравнение", fontSize = 14.sp)
                        }
                    }
                }
            }

            // Сообщение об ошибке
            errorMessage?.let { error ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = error,
                        color = Color(0xFFD32F2F),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Отчёт
            report?.let { reportData ->
                ComparisonReportView(reportData)
            }
        }
    }
}

@Composable
fun ProviderButton(
    label: String,
    value: String,
    selectedValue: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Button(
        onClick = { onSelect(value) },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selectedValue == value) Color(0xFF1976D2) else Color(0xFFE0E0E0),
            contentColor = if (selectedValue == value) Color.White else Color.Black,
            disabledContainerColor = Color(0xFFBDBDBD),
            disabledContentColor = Color.White
        ),
        modifier = Modifier.height(40.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
fun ComparisonReportView(report: ComparisonReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Заголовок отчёта
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
                    text = "📊 Отчёт о сравнении",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                InfoRow("Модель:", report.modelName)
                InfoRow("Макс. контекст:", "${report.maxContextTokens} токенов")
                InfoRow("Время:", java.time.Instant.ofEpochMilli(report.timestamp).toString())
            }
        }

        // Сводка
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFE3F2FD),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📈 Сводка",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                SummaryRow("Всего тестов:", report.summary.totalTests.toString())
                SummaryRow("Успешно:", report.summary.successCount.toString(), Color(0xFF4CAF50))
                SummaryRow("Усечено:", report.summary.truncatedCount.toString(), Color(0xFFFF9800))
                SummaryRow("Ошибок:", report.summary.errorCount.toString(), Color(0xFFF44336))
                SummaryRow(
                    "Средняя полнота:",
                    "${String.format("%.1f", report.summary.averageCompletenessScore * 100)}%"
                )
                SummaryRow(
                    "Среднее время:",
                    "${report.summary.averageResponseTimeMs}ms"
                )

                if (report.summary.recommendations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "💡 Рекомендации:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    report.summary.recommendations.forEach { rec ->
                        Text(
                            text = "• $rec",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                        )
                    }
                }
            }
        }

        // Результаты тестов
        Text(
            text = "🧪 Детальные результаты",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        report.testResults.forEach { result ->
            TestResultCard(result)
        }
    }
}

@Composable
fun TestResultCard(result: PromptTestResult) {
    val statusColor = when (result.status) {
        "success" -> Color(0xFF4CAF50)
        "truncated" -> Color(0xFFFF9800)
        "error" -> Color(0xFFF44336)
        else -> Color.Gray
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Заголовок с типом и статусом
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getPromptTypeLabel(result.promptType),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = getStatusLabel(result.status),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Метрики токенов
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TokenMetric("Запрос", result.promptTokens)
                TokenMetric("Ответ", result.responseTokens)
                TokenMetric("Всего", result.totalTokens)
            }

            // Текст запроса
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFF5F5F5),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📝 Запрос:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF424242),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = result.promptText,
                        fontSize = 11.sp,
                        color = Color(0xFF616161),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Текст ответа
            if (result.responseText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "💬 Ответ модели:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = result.responseText.take(500) +
                                  if (result.responseText.length > 500) "..." else "",
                            fontSize = 11.sp,
                            color = Color(0xFF1B5E20),
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            // Трансформация
            if (result.transformationApplied != "none") {
                Text(
                    text = "🔄 Трансформация: ${result.transformationApplied}",
                    fontSize = 11.sp,
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Метрики качества
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QualityMetric(
                    "Полнота",
                    "${String.format("%.0f", result.qualityMetrics.completenessScore * 100)}%"
                )
                QualityMetric("Длина", "${result.qualityMetrics.responseLength} симв.")
                QualityMetric("Время", "${result.responseTimeMs}ms")
            }

            // Ошибка если есть
            result.errorMessage?.let { error ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "❌ $error",
                        fontSize = 11.sp,
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(text = value, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun SummaryRow(label: String, value: String, color: Color = Color.Black) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun TokenMetric(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun QualityMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}

fun getPromptTypeLabel(type: String): String {
    return when (type) {
        "SHORT" -> "📝 Короткий запрос"
        "LONG" -> "📄 Длинный запрос"
        "EXCEEDING_LIMIT" -> "⚠️ Превышение лимита"
        else -> type
    }
}

fun getStatusLabel(status: String): String {
    return when (status) {
        "success" -> "✓ Успех"
        "truncated" -> "✂ Усечён"
        "error" -> "✗ Ошибка"
        else -> status
    }
}

/**
 * Запускает сравнение используя переменные окружения
 */
suspend fun runComparisonFromEnv(
    clientType: String
): ComparisonReport = withContext(Dispatchers.IO) {
    // Получаем API ключи из переменных окружения
    val (client, config) = when (clientType.lowercase()) {
        "claude" -> {
            val apiKey = System.getenv("ANTHROPIC_API_KEY")
                ?: throw IllegalStateException("ANTHROPIC_API_KEY не установлен")

            val claudeConfig = ClaudeConfig(
                apiKey = apiKey,
                defaultModel = "claude-3-haiku-20240307",
                defaultMaxTokens = 1024
            )
            val claudeClient = ClaudeClient(claudeConfig)

            val tokenConfig = TokenConfig(
                modelName = claudeConfig.defaultModel,
                maxContextTokens = 4000,
                expectedResponseTokens = 1024,
                compressionStrategy = TruncationStrategy(
                    TruncationStrategy.TruncationMode.KEEP_START
                )
            )

            Pair(claudeClient, tokenConfig)
        }

        "huggingface", "hf" -> {
            val apiKey = System.getenv("HUGGINGFACE_API_KEY")
                ?: throw IllegalStateException("HUGGINGFACE_API_KEY не установлен")

            val hfConfig = HuggingFaceConfig(
                apiKey = apiKey,
                defaultModel = "meta-llama/Llama-3.1-8B-Instruct",
                defaultMaxTokens = 1024
            )
            val hfClient = HuggingFaceClient(hfConfig)

            val tokenConfig = TokenConfig(
                modelName = hfConfig.defaultModel,
                maxContextTokens = 4000,
                expectedResponseTokens = 1024,
                compressionStrategy = TruncationStrategy(
                    TruncationStrategy.TruncationMode.KEEP_BOTH
                )
            )

            Pair(hfClient, tokenConfig)
        }

        "yandex", "yandexgpt" -> {
            val apiKey = System.getenv("YANDEX_API_KEY")
                ?: throw IllegalStateException("YANDEX_API_KEY не установлен")
            val folderId = System.getenv("YANDEX_FOLDER_ID")
                ?: throw IllegalStateException("YANDEX_FOLDER_ID не установлен")

            val yandexConfig = YandexGPTConfig(
                apiKey = apiKey,
                folderId = folderId,
                defaultModel = "yandexgpt-lite",
                defaultMaxTokens = 1024
            )
            val yandexClient = YandexGPTClient(yandexConfig)

            val tokenConfig = TokenConfig(
                modelName = yandexConfig.defaultModel,
                maxContextTokens = 3000,
                expectedResponseTokens = 1024,
                compressionStrategy = TruncationStrategy(
                    TruncationStrategy.TruncationMode.KEEP_START
                )
            )

            Pair(yandexClient, tokenConfig)
        }

        else -> throw IllegalArgumentException("Неподдерживаемый тип клиента: $clientType")
    }

    try {
        // Создаём токен-aware клиент
        val tokenAwareClient = TokenAwareClient(
            underlyingClient = client,
            config = config
        )

        // Создаём runner для сравнения
        val comparisonRunner = PromptComparisonRunner(
            client = tokenAwareClient,
            config = config
        )

        // Запускаем сравнение
        comparisonRunner.runComparison()
    } finally {
        client.close()
    }
}
