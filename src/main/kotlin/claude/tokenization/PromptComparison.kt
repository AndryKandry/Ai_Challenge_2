package org.kozyrev.claude.tokenization

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kozyrev.claude.AIClient
import org.kozyrev.claude.Message
import java.io.File
import java.util.logging.Logger

/**
 * Тип запроса для сравнения
 */
enum class PromptType {
    SHORT,          // Короткий запрос < 50 токенов
    LONG,           // Длинный запрос 500-1500 токенов
    EXCEEDING_LIMIT // Запрос, превышающий лимит модели
}

/**
 * Результат выполнения одного типа запроса
 */
@Serializable
data class PromptTestResult(
    val promptType: String,
    val promptText: String,
    val promptTokens: Int,
    val responseText: String,
    val responseTokens: Int,
    val totalTokens: Int,
    val responseTimeMs: Long,
    val status: String, // "success", "truncated", "error"
    val errorMessage: String? = null,
    val qualityMetrics: QualityMetrics,
    val transformationApplied: String = "none",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Метрики качества ответа
 */
@Serializable
data class QualityMetrics(
    val responseLength: Int,
    val isTruncated: Boolean,
    val containsError: Boolean,
    val completenessScore: Double // 0.0 - 1.0
) {
    companion object {
        fun analyze(response: String, promptType: PromptType): QualityMetrics {
            val length = response.length
            val containsError = response.contains(Regex("error|truncated|exceeded", RegexOption.IGNORE_CASE))
            val isTruncated = response.length < 10 || containsError

            // Простая эвристика для оценки полноты
            val completenessScore = when {
                containsError -> 0.0
                isTruncated -> 0.3
                length < 50 -> 0.5
                length < 200 -> 0.7
                else -> 1.0
            }

            return QualityMetrics(
                responseLength = length,
                isTruncated = isTruncated,
                containsError = containsError,
                completenessScore = completenessScore
            )
        }
    }
}

/**
 * Сводный отчёт сравнения
 */
@Serializable
data class ComparisonReport(
    val modelName: String,
    val maxContextTokens: Int,
    val testResults: List<PromptTestResult>,
    val summary: ComparisonSummary,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }.encodeToString(this)
    }

    fun toHumanReadable(): String {
        return buildString {
            appendLine("=" .repeat(80))
            appendLine("СРАВНИТЕЛЬНЫЙ ОТЧЁТ ОБРАБОТКИ ЗАПРОСОВ")
            appendLine("=" .repeat(80))
            appendLine()
            appendLine("Модель: $modelName")
            appendLine("Максимальный контекст: $maxContextTokens токенов")
            appendLine("Время: ${java.time.Instant.ofEpochMilli(timestamp)}")
            appendLine()

            testResults.forEach { result ->
                appendLine("-" .repeat(80))
                appendLine("Тип запроса: ${result.promptType}")
                appendLine("Статус: ${result.status}")
                appendLine()
                appendLine("Токены:")
                appendLine("  Запрос: ${result.promptTokens}")
                appendLine("  Ответ: ${result.responseTokens}")
                appendLine("  Всего: ${result.totalTokens}")
                appendLine("  Трансформация: ${result.transformationApplied}")
                appendLine()
                appendLine("Метрики качества:")
                appendLine("  Длина ответа: ${result.qualityMetrics.responseLength} символов")
                appendLine("  Усечён: ${if (result.qualityMetrics.isTruncated) "Да" else "Нет"}")
                appendLine("  Ошибки: ${if (result.qualityMetrics.containsError) "Да" else "Нет"}")
                appendLine("  Оценка полноты: ${String.format("%.1f%%", result.qualityMetrics.completenessScore * 100)}")
                appendLine()
                appendLine("Время ответа: ${result.responseTimeMs}ms")
                result.errorMessage?.let {
                    appendLine("Ошибка: $it")
                }
                appendLine()
            }

            appendLine("=" .repeat(80))
            appendLine("СВОДКА")
            appendLine("=" .repeat(80))
            appendLine("Успешных запросов: ${summary.successCount}/${summary.totalTests}")
            appendLine("Усечённых ответов: ${summary.truncatedCount}")
            appendLine("Ошибок: ${summary.errorCount}")
            appendLine("Средняя оценка полноты: ${String.format("%.1f%%", summary.averageCompletenessScore * 100)}")
            appendLine("Среднее время ответа: ${summary.averageResponseTimeMs}ms")
            appendLine()
            appendLine("Рекомендации:")
            summary.recommendations.forEach { rec ->
                appendLine("  • $rec")
            }
            appendLine("=" .repeat(80))
        }
    }

    fun saveToFile(filename: String) {
        File(filename).writeText(toJson())
    }

    fun saveHumanReadableToFile(filename: String) {
        File(filename).writeText(toHumanReadable())
    }
}

/**
 * Сводка сравнения
 */
@Serializable
data class ComparisonSummary(
    val totalTests: Int,
    val successCount: Int,
    val truncatedCount: Int,
    val errorCount: Int,
    val averageCompletenessScore: Double,
    val averageResponseTimeMs: Long,
    val recommendations: List<String>
) {
    companion object {
        fun generate(results: List<PromptTestResult>): ComparisonSummary {
            val successCount = results.count { it.status == "success" }
            val truncatedCount = results.count { it.qualityMetrics.isTruncated }
            val errorCount = results.count { it.status == "error" }
            val avgCompleteness = results.map { it.qualityMetrics.completenessScore }.average()
            val avgResponseTime = results.map { it.responseTimeMs }.average().toLong()

            val recommendations = mutableListOf<String>()

            if (errorCount > 0) {
                recommendations.add("Обнаружены ошибки в ${errorCount} запросах. Рекомендуется проверить лимиты модели.")
            }

            if (truncatedCount > 0) {
                recommendations.add("${truncatedCount} ответов были усечены. Рассмотрите использование стратегии сжатия.")
            }

            if (avgCompleteness < 0.7) {
                recommendations.add("Средняя оценка полноты ниже 70%. Рекомендуется оптимизировать промпты.")
            }

            if (avgResponseTime > 5000) {
                recommendations.add("Среднее время ответа превышает 5 секунд. Рассмотрите использование более быстрой модели.")
            }

            if (recommendations.isEmpty()) {
                recommendations.add("Все тесты прошли успешно! Модель обрабатывает запросы корректно.")
            }

            return ComparisonSummary(
                totalTests = results.size,
                successCount = successCount,
                truncatedCount = truncatedCount,
                errorCount = errorCount,
                averageCompletenessScore = avgCompleteness,
                averageResponseTimeMs = avgResponseTime,
                recommendations = recommendations
            )
        }
    }
}

/**
 * Система сравнения различных типов запросов
 */
class PromptComparisonRunner(
    private val client: TokenAwareClient,
    private val config: TokenConfig
) {
    private val logger = Logger.getLogger(PromptComparisonRunner::class.java.name)

    /**
     * Запускает полное сравнение трёх типов запросов
     */
    suspend fun runComparison(): ComparisonReport {
        logger.info("Начало сравнения запросов для модели: ${config.modelName}")

        val results = mutableListOf<PromptTestResult>()

        // 1. Короткий запрос
        results.add(runTest(PromptType.SHORT, generateShortPrompt()))

        delay(3000L)

        // 2. Длинный запрос
        results.add(runTest(PromptType.LONG, generateLongPrompt()))

        delay(3000L)

        // 3. Запрос, превышающий лимит
        results.add(runTest(PromptType.EXCEEDING_LIMIT, generateExceedingPrompt()))

        val summary = ComparisonSummary.generate(results)

        return ComparisonReport(
            modelName = config.modelName,
            maxContextTokens = config.maxContextTokens,
            testResults = results,
            summary = summary
        )
    }

    /**
     * Выполняет один тест
     */
    private suspend fun runTest(promptType: PromptType, promptText: String): PromptTestResult {
        logger.info("Тестирование: $promptType")

        val startTime = System.currentTimeMillis()
        val promptTokens = client.countTokens(promptText)

        return try {
            val messages = listOf(Message(role = "user", content = promptText))
            val (response, metrics) = client.sendConversationWithEnhancedMetrics(messages = messages)

            val endTime = System.currentTimeMillis()

            PromptTestResult(
                promptType = promptType.name,
                promptText = promptText.take(200) + if (promptText.length > 200) "..." else "",
                promptTokens = promptTokens,
                responseText = response.content,
                responseTokens = response.metrics.outputTokens,
                totalTokens = metrics.totalTokens,
                responseTimeMs = endTime - startTime,
                status = determineStatus(response.content, promptType),
                qualityMetrics = QualityMetrics.analyze(response.content, promptType),
                transformationApplied = metrics.transformationApplied
            )
        } catch (e: Exception) {
            logger.severe("Ошибка при выполнении теста $promptType: ${e.message}")

            val endTime = System.currentTimeMillis()

            PromptTestResult(
                promptType = promptType.name,
                promptText = promptText.take(200) + if (promptText.length > 200) "..." else "",
                promptTokens = promptTokens,
                responseText = "",
                responseTokens = 0,
                totalTokens = promptTokens,
                responseTimeMs = endTime - startTime,
                status = "error",
                errorMessage = e.message,
                qualityMetrics = QualityMetrics(0, true, true, 0.0),
                transformationApplied = "none"
            )
        }
    }

    private fun determineStatus(response: String, promptType: PromptType): String {
        return when {
            response.isEmpty() -> "error"
            response.contains(Regex("error|exceeded", RegexOption.IGNORE_CASE)) -> "error"
            response.contains(Regex("truncated", RegexOption.IGNORE_CASE)) -> "truncated"
            else -> "success"
        }
    }

    private fun generateShortPrompt(): String {
        return "What is the capital of France?"
    }

    private fun generateLongPrompt(): String {
        return """
            Please provide a comprehensive analysis of the following topic:

            The impact of artificial intelligence on modern software development practices.

            Include the following aspects in your analysis:
            1. How AI is changing the way developers write code
            2. The role of AI-powered tools like code completion and generation
            3. Impact on testing and quality assurance
            4. Changes in project management and planning
            5. The future of software development with AI integration

            For each aspect, provide:
            - Current state of adoption
            - Benefits and advantages
            - Challenges and limitations
            - Real-world examples
            - Future predictions

            Additionally, discuss:
            - Ethical considerations
            - Impact on developer skills and education
            - Economic implications for the software industry
            - Potential risks and how to mitigate them

            Please structure your response with clear sections and provide concrete examples
            where applicable. Aim for a detailed analysis that covers both technical and
            strategic perspectives.
        """.trimIndent()
    }

    private fun generateExceedingPrompt(): String {
        // Генерируем текст, который намеренно превышает лимит
        val baseText = generateLongPrompt()
        val repetitions = (config.maxContextTokens / client.countTokens(baseText)) + 2

        return buildString {
            appendLine("This is an intentionally long prompt designed to exceed the model's context limit.")
            appendLine()
            repeat(repetitions) {
                appendLine("=== Section $it ===")
                appendLine(baseText)
                appendLine()
            }
            appendLine("Please summarize all the information above.")
        }
    }
}
