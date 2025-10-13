package org.kozyrev.claude.tokenization

import kotlinx.coroutines.runBlocking
import org.kozyrev.claude.ClaudeClient
import org.kozyrev.claude.ClaudeConfig
import org.kozyrev.claude.HuggingFaceClient
import org.kozyrev.claude.HuggingFaceConfig
import java.io.File
import java.util.logging.Logger

/**
 * Главный класс для запуска сравнения различных типов запросов
 */
class ComparisonRunner {
    private val logger = Logger.getLogger(ComparisonRunner::class.java.name)

    /**
     * Запускает сравнение для указанного клиента
     */
    fun runComparison(
        clientType: String,
        apiKey: String,
        outputDir: String = "reports"
    ) = runBlocking {
        logger.info("=".repeat(80))
        logger.info("Запуск сравнения для клиента: $clientType")
        logger.info("=".repeat(80))

        // Создаём директорию для отчётов если не существует
        File(outputDir).mkdirs()

        try {
            val (client, config) = createClientAndConfig(clientType, apiKey)

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
            logger.info("Начало выполнения тестов...")
            val report = comparisonRunner.runComparison() //

            // Сохраняем отчёты
            val timestamp = System.currentTimeMillis()
            val jsonFilename = "$outputDir/comparison_${clientType}_${timestamp}.json"
            val txtFilename = "$outputDir/comparison_${clientType}_${timestamp}.txt"

            report.saveToFile(jsonFilename)
            report.saveHumanReadableToFile(txtFilename)

            logger.info("Отчёты сохранены:")
            logger.info("  JSON: $jsonFilename")
            logger.info("  TXT: $txtFilename")

            // Выводим краткую сводку
            println()
            println(report.toHumanReadable())

            // Закрываем клиент
            client.close()

        } catch (e: Exception) {
            logger.severe("Ошибка при выполнении сравнения: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Создаёт клиент и конфигурацию на основе типа
     */
    private fun createClientAndConfig(
        clientType: String,
        apiKey: String
    ): Pair<org.kozyrev.claude.AIClient, TokenConfig> {
        return when (clientType.lowercase()) {
            "claude" -> {
                val config = ClaudeConfig(
                    apiKey = apiKey,
                    defaultModel = "claude-3-haiku-20240307",
                    defaultMaxTokens = 1024
                )
                val client = ClaudeClient(config)

                val tokenConfig = TokenConfig(
                    modelName = config.defaultModel,
                    maxContextTokens = 4000, // Claude 3 Haiku context limit
                    expectedResponseTokens = 1024,
                    compressionStrategy = TruncationStrategy(
                        TruncationStrategy.TruncationMode.KEEP_START
                    )
                )

                Pair(client, tokenConfig)
            }

            "huggingface", "hf" -> {
                val config = HuggingFaceConfig(
                    apiKey = apiKey,
                    defaultModel = "meta-llama/Llama-3.1-8B-Instruct",
                    defaultMaxTokens = 1024
                )
                val client = HuggingFaceClient(config)

                val tokenConfig = TokenConfig(
                    modelName = config.defaultModel,
                    maxContextTokens = 4000, // Llama 3.1 context limit
                    expectedResponseTokens = 1024,
                    compressionStrategy = TruncationStrategy(
                        TruncationStrategy.TruncationMode.KEEP_BOTH
                    )
                )

                Pair(client, tokenConfig)
            }

            "yandex", "yandexgpt" -> {
                // Для Yandex GPT apiKey содержит "apiKey:folderId"
                val parts = apiKey.split(":")
                if (parts.size != 2) {
                    throw IllegalArgumentException(
                        "Для Yandex GPT apiKey должен быть в формате 'apiKey:folderId'"
                    )
                }

                val config = org.kozyrev.claude.YandexGPTConfig(
                    apiKey = parts[0],
                    folderId = parts[1],
                    defaultModel = "yandexgpt-lite",
                    defaultMaxTokens = 1024
                )
                val client = org.kozyrev.claude.YandexGPTClient(config)

                val tokenConfig = TokenConfig(
                    modelName = config.defaultModel,
                    maxContextTokens = 4000, // Yandex GPT context limit
                    expectedResponseTokens = 1024,
                    compressionStrategy = TruncationStrategy(
                        TruncationStrategy.TruncationMode.KEEP_START
                    )
                )

                Pair(client, tokenConfig)
            }

            else -> throw IllegalArgumentException(
                "Неподдерживаемый тип клиента: $clientType. " +
                "Доступны: claude, huggingface, yandex"
            )
        }
    }
}

/**
 * Entry point для запуска из командной строки
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("""
            Использование: ComparisonRunner <client_type> <api_key> [output_dir]

            Параметры:
              client_type  - Тип клиента: claude, huggingface, yandex
              api_key      - API ключ для клиента
                            Для Yandex GPT: apiKey:folderId
              output_dir   - Директория для сохранения отчётов (по умолчанию: reports)

            Примеры:
              ./gradlew run --args="claude YOUR_CLAUDE_API_KEY"
              ./gradlew run --args="huggingface YOUR_HF_API_KEY custom_reports"
              ./gradlew run --args="yandex YOUR_API_KEY:YOUR_FOLDER_ID"
        """.trimIndent())
        return
    }

    val clientType = args[0]
    val apiKey = args[1]
    val outputDir = args.getOrNull(2) ?: "reports"

    val runner = ComparisonRunner()
    runner.runComparison(clientType, apiKey, outputDir)
}
