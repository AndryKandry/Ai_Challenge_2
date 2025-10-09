package org.kozyrev.agents

import org.kozyrev.claude.AIClient
import org.kozyrev.claude.AIResponse
import org.kozyrev.claude.Message
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Модель данных для статьи
 */
@Serializable
data class Article(
    val title: String,
    val topic: String,
    val content: String,
    val wordCount: Int,
    val sections: List<String>
)

/**
 * Агент 1: Генерирует статью по заданной теме
 */
class WriterAgent(private val aiClient: AIClient) {

    private val systemPrompt = """
        Ты - профессиональный писатель статей.

        Твоя задача:
        1. Написать качественную статью на заданную тему
        2. Структурировать статью на разделы
        3. Использовать понятный и информативный стиль
        4. Статья должна содержать 300-500 слов

        Формат ответа должен быть строго JSON:
        {
            "title": "Название статьи",
            "topic": "Тема статьи",
            "content": "Полный текст статьи с разделами",
            "wordCount": количество_слов,
            "sections": ["Раздел 1", "Раздел 2", "Раздел 3"]
        }

        Важно: Возвращай ТОЛЬКО JSON, без дополнительных объяснений.
    """.trimIndent()

    /**
     * Генерирует статью по теме
     */
    suspend fun writeArticle(topic: String, temperature: Double = 0.7): Article {
        val userPrompt = """
            Напиши статью на тему: "$topic"

            Требования:
            - Интересное название
            - 3-5 логических разделов
            - 300-500 слов
            - Информативный контент

            Верни результат в формате JSON как указано в системном промпте.
        """.trimIndent()

        val response = aiClient.sendConversation(
            messages = listOf(Message(role = "user", content = userPrompt)),
            systemPrompt = systemPrompt,
            temperature = temperature
        )

        // Парсим JSON из ответа
        return parseArticle(response)
    }

    /**
     * Парсит статью из ответа AI
     */
    private fun parseArticle(response: AIResponse): Article {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        // Извлекаем JSON из ответа (может быть обёрнут в ```json ... ```)
        val content = response.content
        val jsonString = if (content.contains("```json")) {
            content.substringAfter("```json").substringBefore("```").trim()
        } else if (content.contains("```")) {
            content.substringAfter("```").substringBefore("```").trim()
        } else {
            content.trim()
        }

        return json.decodeFromString<Article>(jsonString)
    }

    /**
     * Форматирует статью в читаемый текст для передачи следующему агенту
     */
    fun formatArticleForReview(article: Article): String {
        return """
            |=== СТАТЬЯ ДЛЯ ПРОВЕРКИ ===
            |
            |Название: ${article.title}
            |Тема: ${article.topic}
            |Количество слов: ${article.wordCount}
            |Разделы: ${article.sections.joinToString(", ")}
            |
            |--- СОДЕРЖАНИЕ ---
            |${article.content}
            |
            |=== КОНЕЦ СТАТЬИ ===
        """.trimMargin()
    }
}
