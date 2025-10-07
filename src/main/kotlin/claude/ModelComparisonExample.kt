package org.kozyrev.claude

import kotlinx.coroutines.runBlocking

/**
 * Пример использования сервиса сравнения моделей через командную строку
 */
object ModelComparisonExample {

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("Запуск сравнения AI моделей")
        println()

        // Проверяем наличие API ключей
        val claudeApiKey = System.getenv("ANTHROPIC_API_KEY")
        val hfApiKey = System.getenv("HUGGINGFACE_API_KEY")

        if (claudeApiKey.isNullOrBlank()) {
            println("⚠️  ПРЕДУПРЕЖДЕНИЕ: Не установлен ANTHROPIC_API_KEY")
        }

        if (hfApiKey.isNullOrBlank()) {
            println("⚠️  ПРЕДУПРЕЖДЕНИЕ: Не установлен HUGGINGFACE_API_KEY")
        }

        if (claudeApiKey.isNullOrBlank() && hfApiKey.isNullOrBlank()) {
            println("❌ Необходим хотя бы один API ключ для продолжения")
            return@runBlocking
        }

        println()

        // Создаем клиентов для различных моделей
        val clients = mutableListOf<AIClient>()

        // Claude (если доступен)
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

        // HuggingFace модели (если доступны)
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

            // Модель 3: Mistral (французская модель)
            clients.add(
                HuggingFaceClient(
                    HuggingFaceConfig(
                        apiKey = hfApiKey,
                        defaultModel = "mistralai/Mistral-7B-Instruct-v0.3"
                    )
                )
            )
        }

        if (clients.isEmpty()) {
            println("❌ Не удалось создать ни одного клиента")
            return@runBlocking
        }

        println("Инициализировано клиентов: ${clients.size}")
        clients.forEach { client ->
            println("  - ${client.getProviderName()}: ${client.getDefaultModel()}")
        }
        println()

        // Создаем сервис сравнения
        val comparison = ModelComparison()

        // Тестовый промпт
        val testPrompt = "Объясни простыми словами, что такое квантовая запутанность. Ответ должен быть не более 3 предложений."

        println("Начало сравнения моделей...")
        println("Тестовый запрос: $testPrompt")
        println()

        try {
            // Запускаем сравнение
            val result = comparison.compareModels(
                clients = clients,
                testPrompt = testPrompt,
                temperature = 0.7
            )

            // Выводим результаты
            result.printSummary()

            // Анализ качества ответов
            println("\nАНАЛИЗ КАЧЕСТВА ОТВЕТОВ:")
            println("=".repeat(80))

            result.results.forEachIndexed { index, modelResult ->
                if (modelResult.error == null) {
                    println("${index + 1}. ${modelResult.providerName} - ${modelResult.modelName}")
                    println("   Длина ответа: ${modelResult.response.length} символов")
                    println("   Скорость: ${modelResult.metrics.responseTimeMs}мс")
                    println("   Эффективность: ${String.format("%.2f",
                        modelResult.metrics.outputTokens.toDouble() / (modelResult.metrics.responseTimeMs / 1000.0))} токенов/сек")
                    println()
                }
            }

            // Определяем победителей в разных категориях
            println("ПОБЕДИТЕЛИ:")
            println("=".repeat(80))

            val successfulResults = result.results.filter { it.error == null }

            if (successfulResults.isNotEmpty()) {
                val fastest = successfulResults.minByOrNull { it.metrics.responseTimeMs }
                println("⚡ Самая быстрая: ${fastest?.providerName}/${fastest?.modelName} " +
                        "(${fastest?.metrics?.responseTimeMs}мс)")

                val cheapest = successfulResults.minByOrNull {
                    it.metrics.estimatedCostUsd ?: 0.0
                }
                val cheapestCost = cheapest?.metrics?.estimatedCostUsd?.let {
                    String.format("%.6f USD", it)
                } ?: "Бесплатно"
                println("💰 Самая дешевая: ${cheapest?.providerName}/${cheapest?.modelName} ($cheapestCost)")

                val mostTokens = successfulResults.maxByOrNull { it.metrics.outputTokens }
                println("📝 Самый подробный ответ: ${mostTokens?.providerName}/${mostTokens?.modelName} " +
                        "(${mostTokens?.metrics?.outputTokens} токенов)")
            }

            println("=".repeat(80))

        } catch (e: Exception) {
            println("❌ Ошибка при сравнении: ${e.message}")
            e.printStackTrace()
        } finally {
            // Закрываем всех клиентов
            clients.forEach { it.close() }
        }
    }

    /**
     * Пример бенчмарка одной модели
     */
    fun runBenchmark() = runBlocking {
        val apiKey = System.getenv("HUGGINGFACE_API_KEY") ?: return@runBlocking

        val client = HuggingFaceClient(
            HuggingFaceConfig(
                apiKey = apiKey,
                defaultModel = "meta-llama/Llama-3.2-3B-Instruct"
            )
        )

        val comparison = ModelComparison()

        val testPrompts = listOf(
            "Что такое искусственный интеллект?",
            "Объясни принцип работы нейронной сети",
            "Какие существуют типы машинного обучения?",
            "Что такое обучение с подкреплением?",
            "Назови основные преимущества и недостатки ИИ"
        )

        try {
            val result = comparison.benchmarkModel(
                client = client,
                testPrompts = testPrompts,
                temperature = 0.7
            )

            result.printSummary()
        } finally {
            client.close()
        }
    }
}
