package org.kozyrev.agents

import org.kozyrev.claude.AIClient
import org.kozyrev.claude.AIResponse
import org.kozyrev.claude.Message
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Модель данных для оценки статьи
 */
@Serializable
data class ArticleReview(
    val overallScore: Int,  // Общая оценка от 1 до 10
    val qualityScore: Int,  // Оценка качества содержания
    val structureScore: Int, // Оценка структуры
    val readabilityScore: Int, // Оценка читаемости
    val strengths: List<String>, // Сильные стороны
    val weaknesses: List<String>, // Слабые стороны
    val recommendations: List<String>, // Рекомендации по улучшению
    val verdict: String // Итоговый вердикт
)

/**
 * Агент 2: Проверяет качество статьи и дает оценку
 */
class ReviewerAgent(private val aiClient: AIClient) {

    private val systemPrompt = """
        Ты - профессиональный редактор и критик статей.

        Твоя задача:
        1. Проанализировать предоставленную статью
        2. Оценить качество содержания, структуру и читаемость
        3. Выявить сильные и слабые стороны
        4. Дать конструктивные рекомендации

        Формат ответа должен быть строго JSON:
        {
            "overallScore": оценка_от_1_до_10,
            "qualityScore": оценка_качества_от_1_до_10,
            "structureScore": оценка_структуры_от_1_до_10,
            "readabilityScore": оценка_читаемости_от_1_до_10,
            "strengths": ["Сильная сторона 1", "Сильная сторона 2"],
            "weaknesses": ["Слабая сторона 1", "Слабая сторона 2"],
            "recommendations": ["Рекомендация 1", "Рекомендация 2"],
            "verdict": "Общий вывод о статье"
        }

        Важно: Возвращай ТОЛЬКО JSON, без дополнительных объяснений.
    """.trimIndent()

    /**
     * Проверяет статью и возвращает оценку
     */
    suspend fun reviewArticle(formattedArticle: String, temperature: Double = 0.3): ArticleReview {
        val userPrompt = """
            Проанализируй следующую статью и дай ей оценку:

            $formattedArticle

            Оцени статью по критериям:
            - Качество содержания (информативность, точность, глубина)
            - Структура (логичность, связность, разделение на части)
            - Читаемость (ясность, стиль, доступность)

            Укажи сильные стороны, слабые стороны и рекомендации по улучшению.
            Верни результат в формате JSON как указано в системном промпте.
        """.trimIndent()

        val response = aiClient.sendConversation(
            messages = listOf(Message(role = "user", content = userPrompt)),
            systemPrompt = systemPrompt,
            temperature = temperature
        )

        return parseReview(response)
    }

    /**
     * Парсит оценку из ответа AI
     */
    private fun parseReview(response: AIResponse): ArticleReview {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        // Извлекаем JSON из ответа
        val content = response.content
        val jsonString = if (content.contains("```json")) {
            content.substringAfter("```json").substringBefore("```").trim()
        } else if (content.contains("```")) {
            content.substringAfter("```").substringBefore("```").trim()
        } else {
            content.trim()
        }

        return json.decodeFromString<ArticleReview>(jsonString)
    }

    /**
     * Форматирует оценку в читаемый текст
     */
    fun formatReview(review: ArticleReview): String {
        return """
            |╔════════════════════════════════════════════╗
            |║       РЕЗУЛЬТАТЫ ПРОВЕРКИ СТАТЬИ          ║
            |╚════════════════════════════════════════════╝
            |
            |📊 ОЦЕНКИ:
            |   • Общая оценка: ${review.overallScore}/10 ${getScoreEmoji(review.overallScore)}
            |   • Качество содержания: ${review.qualityScore}/10
            |   • Структура: ${review.structureScore}/10
            |   • Читаемость: ${review.readabilityScore}/10
            |
            |✅ СИЛЬНЫЕ СТОРОНЫ:
            |${review.strengths.mapIndexed { i, s -> "   ${i + 1}. $s" }.joinToString("\n")}
            |
            |⚠️ СЛАБЫЕ СТОРОНЫ:
            |${review.weaknesses.mapIndexed { i, w -> "   ${i + 1}. $w" }.joinToString("\n")}
            |
            |💡 РЕКОМЕНДАЦИИ:
            |${review.recommendations.mapIndexed { i, r -> "   ${i + 1}. $r" }.joinToString("\n")}
            |
            |📝 ВЕРДИКТ:
            |   ${review.verdict}
            |
            |═══════════════════════════════════════════════
        """.trimMargin()
    }

    private fun getScoreEmoji(score: Int): String = when {
        score >= 9 -> "🌟 Отлично!"
        score >= 7 -> "👍 Хорошо"
        score >= 5 -> "😐 Приемлемо"
        else -> "❌ Требует доработки"
    }
}
