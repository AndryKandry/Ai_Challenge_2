package org.kozyrev.agents

import kotlinx.coroutines.runBlocking
import org.kozyrev.claude.ClaudeClientBuilder

/**
 * Пример использования системы агентов из командной строки
 */
fun main() = runBlocking {
    println("=".repeat(60))
    println("🤖 ДЕМОНСТРАЦИЯ ВЗАИМОДЕЙСТВИЯ AI АГЕНТОВ")
    println("=".repeat(60))
    println()

    // Инициализация AI клиента
    val apiKey = System.getenv("ANTHROPIC_API_KEY")
        ?: throw IllegalStateException("ANTHROPIC_API_KEY не найден")

    val aiClient = ClaudeClientBuilder()
        .apiKey(apiKey)
        .defaultMaxTokens(2048)
        .build()

    try {
        // Создание оркестратора
        val orchestrator = AgentOrchestrator(aiClient)

        // Настройка коллбэка для отслеживания прогресса
        orchestrator.onProgressUpdate = { message, status ->
            println("[$status] $message")
            println()
        }

        // Запрос темы у пользователя
        print("Введите тему для статьи: ")
        val topic = readlnOrNull()?.trim() ?: "Искусственный интеллект"

        println()
        println("Запуск агентов для темы: \"$topic\"")
        println("━".repeat(60))
        println()

        // Запуск полного цикла
        val result = orchestrator.runAgentWorkflow(topic)

        // Вывод результатов
        println()
        println(orchestrator.formatResult(result))
        println()

        // Дополнительная статистика
        if (result.success) {
            println("✅ Взаимодействие агентов успешно завершено!")
            result.article?.let {
                println("   • Статья: ${it.title}")
                println("   • Количество слов: ${it.wordCount}")
                println("   • Разделы: ${it.sections.size}")
            }
            result.review?.let {
                println("   • Общая оценка: ${it.overallScore}/10")
                println("   • Сильные стороны: ${it.strengths.size}")
                println("   • Рекомендации: ${it.recommendations.size}")
            }
        } else {
            println("❌ Произошла ошибка при выполнении")
        }

    } catch (e: Exception) {
        println("❌ Ошибка: ${e.message}")
        e.printStackTrace()
    } finally {
        aiClient.close()
        println()
        println("=".repeat(60))
    }
}
