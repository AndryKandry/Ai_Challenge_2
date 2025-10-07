package org.kozyrev.claude

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.logging.Logger

/**
 * Сервис для сравнения различных AI моделей
 */
class ModelComparison {
    private val logger = Logger.getLogger(ModelComparison::class.java.name)

    /**
     * Сравнивает несколько моделей на одном и том же запросе
     */
    suspend fun compareModels(
        clients: List<AIClient>,
        testPrompt: String,
        systemPrompt: String? = null,
        temperature: Double? = null
    ): ComparisonResult {
        logger.info("Начало сравнения ${clients.size} моделей")

        val results = coroutineScope {
            clients.map { client ->
                async {
                    try {
                        logger.info("Тестирование ${client.getProviderName()} - ${client.getDefaultModel()}")

                        val message = Message(role = "user", content = testPrompt)
                        val response = client.sendConversation(
                            messages = listOf(message),
                            systemPrompt = systemPrompt,
                            temperature = temperature
                        )

                        ModelResult(
                            providerName = response.providerName,
                            modelName = response.modelName,
                            response = response.content,
                            metrics = response.metrics,
                            error = null
                        )
                    } catch (e: Exception) {
                        logger.severe("Ошибка при тестировании ${client.getProviderName()}: ${e.message}")
                        ModelResult(
                            providerName = client.getProviderName(),
                            modelName = client.getDefaultModel(),
                            response = "",
                            metrics = ResponseMetrics(0, 0, 0, 0, null),
                            error = e.message
                        )
                    }
                }
            }.map { it.await() }
        }

        return ComparisonResult(
            prompt = testPrompt,
            results = results
        )
    }

    /**
     * Тестирует одну модель несколько раз для оценки стабильности
     */
    suspend fun benchmarkModel(
        client: AIClient,
        testPrompts: List<String>,
        systemPrompt: String? = null,
        temperature: Double? = null
    ): BenchmarkResult {
        logger.info("Начало бенчмарка для ${client.getProviderName()} - ${client.getDefaultModel()}")

        val results = mutableListOf<ModelResult>()

        testPrompts.forEach { prompt ->
            try {
                val message = Message(role = "user", content = prompt)
                val response = client.sendConversation(
                    messages = listOf(message),
                    systemPrompt = systemPrompt,
                    temperature = temperature
                )

                results.add(
                    ModelResult(
                        providerName = response.providerName,
                        modelName = response.modelName,
                        response = response.content,
                        metrics = response.metrics,
                        error = null
                    )
                )
            } catch (e: Exception) {
                logger.severe("Ошибка при тестировании: ${e.message}")
                results.add(
                    ModelResult(
                        providerName = client.getProviderName(),
                        modelName = client.getDefaultModel(),
                        response = "",
                        metrics = ResponseMetrics(0, 0, 0, 0, null),
                        error = e.message
                    )
                )
            }
        }

        // Вычисляем средние метрики
        val avgResponseTime = results.mapNotNull {
            if (it.error == null) it.metrics.responseTimeMs else null
        }.average()

        val avgInputTokens = results.mapNotNull {
            if (it.error == null) it.metrics.inputTokens else null
        }.average()

        val avgOutputTokens = results.mapNotNull {
            if (it.error == null) it.metrics.outputTokens else null
        }.average()

        val totalCost = results.mapNotNull {
            it.metrics.estimatedCostUsd
        }.sum()

        return BenchmarkResult(
            providerName = client.getProviderName(),
            modelName = client.getDefaultModel(),
            testCount = testPrompts.size,
            successCount = results.count { it.error == null },
            averageResponseTimeMs = avgResponseTime,
            averageInputTokens = avgInputTokens,
            averageOutputTokens = avgOutputTokens,
            totalCostUsd = if (totalCost > 0) totalCost else null,
            results = results
        )
    }
}

/**
 * Результат сравнения моделей
 */
data class ComparisonResult(
    val prompt: String,
    val results: List<ModelResult>
) {
    fun printSummary() {
        println("\n" + "=".repeat(80))
        println("СРАВНЕНИЕ МОДЕЛЕЙ")
        println("=".repeat(80))
        println("Запрос: $prompt")
        println()

        results.forEachIndexed { index, result ->
            println("${index + 1}. ${result.providerName} - ${result.modelName}")
            println("-".repeat(80))

            if (result.error != null) {
                println("❌ ОШИБКА: ${result.error}")
            } else {
                println("Ответ: ${result.response.take(200)}${if (result.response.length > 200) "..." else ""}")
                println()
                println("Метрики:")
                println(result.metrics.toString().prependIndent("  "))
            }
            println()
        }

        // Сравнительная таблица
        println("=".repeat(80))
        println("СРАВНИТЕЛЬНАЯ ТАБЛИЦА")
        println("=".repeat(80))
        println(String.format("%-30s | %10s | %10s | %10s | %15s",
            "Модель", "Время (мс)", "Вход tok", "Выход tok", "Стоимость"))
        println("-".repeat(80))

        results.forEach { result ->
            if (result.error == null) {
                val costStr = result.metrics.estimatedCostUsd?.let {
                    String.format("%.6f USD", it)
                } ?: "Бесплатно"

                println(String.format("%-30s | %10d | %10d | %10d | %15s",
                    "${result.providerName}/${result.modelName}".take(30),
                    result.metrics.responseTimeMs,
                    result.metrics.inputTokens,
                    result.metrics.outputTokens,
                    costStr
                ))
            }
        }
        println("=".repeat(80))
    }
}

/**
 * Результат тестирования одной модели
 */
data class ModelResult(
    val providerName: String,
    val modelName: String,
    val response: String,
    val metrics: ResponseMetrics,
    val error: String?
)

/**
 * Результат бенчмарка модели
 */
data class BenchmarkResult(
    val providerName: String,
    val modelName: String,
    val testCount: Int,
    val successCount: Int,
    val averageResponseTimeMs: Double,
    val averageInputTokens: Double,
    val averageOutputTokens: Double,
    val totalCostUsd: Double?,
    val results: List<ModelResult>
) {
    fun printSummary() {
        println("\n" + "=".repeat(80))
        println("БЕНЧМАРК: $providerName - $modelName")
        println("=".repeat(80))
        println("Тестов выполнено: $testCount")
        println("Успешных: $successCount")
        println("Провалено: ${testCount - successCount}")
        println()
        println("Средние метрики:")
        println("  Время ответа: ${String.format("%.2f", averageResponseTimeMs)} мс")
        println("  Входных токенов: ${String.format("%.2f", averageInputTokens)}")
        println("  Выходных токенов: ${String.format("%.2f", averageOutputTokens)}")

        val costStr = totalCostUsd?.let { String.format("%.6f USD", it) } ?: "Бесплатно"
        println("  Общая стоимость: $costStr")
        println("=".repeat(80))
    }
}
