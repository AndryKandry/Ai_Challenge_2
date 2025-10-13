package org.kozyrev.claude.tokenization

import kotlinx.serialization.Serializable
import org.kozyrev.claude.AIClient
import org.kozyrev.claude.Message
import java.util.logging.Logger

/**
 * Результат обработки текста (обрезка/сжатие)
 */
@Serializable
data class CompressionResult(
    val originalText: String,
    val processedText: String,
    val originalTokens: Int,
    val processedTokens: Int,
    val strategy: String,
    val transformationType: String, // "truncation", "summarization", "none"
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toLogString(): String {
        return """
            Transformation: $transformationType ($strategy)
            Original tokens: $originalTokens
            Processed tokens: $processedTokens
            Reduction: ${originalTokens - processedTokens} tokens (${getReductionPercentage()}%)
            Timestamp: $timestamp
        """.trimIndent()
    }

    private fun getReductionPercentage(): Int {
        if (originalTokens == 0) return 0
        return ((originalTokens - processedTokens) * 100 / originalTokens)
    }
}

/**
 * Интерфейс стратегии сжатия/обрезки текста
 */
interface CompressionStrategy {
    /**
     * Обрабатывает текст для уменьшения количества токенов
     * @param text исходный текст
     * @param tokenizer токенизатор для подсчёта
     * @param targetTokens целевое количество токенов
     * @return результат обработки
     */
    suspend fun process(
        text: String,
        tokenizer: Tokenizer,
        targetTokens: Int
    ): CompressionResult

    /**
     * Имя стратегии
     */
    fun getName(): String
}

/**
 * Стратегия усечения текста
 * Режимы: сохранение начала, сохранение конца, сохранение начала и конца
 */
class TruncationStrategy(
    private val mode: TruncationMode = TruncationMode.KEEP_START
) : CompressionStrategy {

    enum class TruncationMode {
        KEEP_START,   // Сохранить начало
        KEEP_END,     // Сохранить конец
        KEEP_BOTH     // Сохранить начало и конец
    }

    override suspend fun process(
        text: String,
        tokenizer: Tokenizer,
        targetTokens: Int
    ): CompressionResult {
        val originalTokens = tokenizer.countTokens(text)

        if (originalTokens <= targetTokens) {
            return CompressionResult(
                originalText = text,
                processedText = text,
                originalTokens = originalTokens,
                processedTokens = originalTokens,
                strategy = getName(),
                transformationType = "none"
            )
        }

        val processedText = when (mode) {
            TruncationMode.KEEP_START -> truncateKeepStart(text, tokenizer, targetTokens)
            TruncationMode.KEEP_END -> truncateKeepEnd(text, tokenizer, targetTokens)
            TruncationMode.KEEP_BOTH -> truncateKeepBoth(text, tokenizer, targetTokens)
        }

        val processedTokens = tokenizer.countTokens(processedText)

        return CompressionResult(
            originalText = text,
            processedText = processedText,
            originalTokens = originalTokens,
            processedTokens = processedTokens,
            strategy = getName(),
            transformationType = "truncation"
        )
    }

    private fun truncateKeepStart(text: String, tokenizer: Tokenizer, targetTokens: Int): String {
        // Приблизительная оценка: берём пропорциональную часть текста
        val ratio = targetTokens.toDouble() / tokenizer.countTokens(text)
        val targetLength = (text.length * ratio).toInt()
        return text.take(targetLength)
    }

    private fun truncateKeepEnd(text: String, tokenizer: Tokenizer, targetTokens: Int): String {
        val ratio = targetTokens.toDouble() / tokenizer.countTokens(text)
        val targetLength = (text.length * ratio).toInt()
        return text.takeLast(targetLength)
    }

    private fun truncateKeepBoth(text: String, tokenizer: Tokenizer, targetTokens: Int): String {
        // Делим целевое количество пополам
        val tokensPerPart = targetTokens / 2
        val ratio = tokensPerPart.toDouble() / tokenizer.countTokens(text)
        val lengthPerPart = (text.length * ratio).toInt()

        val start = text.take(lengthPerPart)
        val end = text.takeLast(lengthPerPart)
        return "$start\n...\n$end"
    }

    override fun getName(): String = "Truncation_${mode.name}"
}

/**
 * Стратегия суммаризации с использованием AI модели
 */
class SummarizationStrategy(
    private val client: AIClient,
    private val compressionRatio: Double = 0.5 // Целевое сжатие (50% по умолчанию)
) : CompressionStrategy {

    private val logger = Logger.getLogger(SummarizationStrategy::class.java.name)

    override suspend fun process(
        text: String,
        tokenizer: Tokenizer,
        targetTokens: Int
    ): CompressionResult {
        val originalTokens = tokenizer.countTokens(text)

        if (originalTokens <= targetTokens) {
            return CompressionResult(
                originalText = text,
                processedText = text,
                originalTokens = originalTokens,
                processedTokens = originalTokens,
                strategy = getName(),
                transformationType = "none"
            )
        }

        logger.info("Суммаризация текста: $originalTokens -> $targetTokens токенов")

        val summary = try {
            summarizeText(text, targetTokens)
        } catch (e: Exception) {
            logger.warning("Ошибка суммаризации, применяем truncation: ${e.message}")
            // Fallback к truncation при ошибке
            return TruncationStrategy(TruncationStrategy.TruncationMode.KEEP_START)
                .process(text, tokenizer, targetTokens)
        }

        val processedTokens = tokenizer.countTokens(summary)

        return CompressionResult(
            originalText = text,
            processedText = summary,
            originalTokens = originalTokens,
            processedTokens = processedTokens,
            strategy = getName(),
            transformationType = "summarization"
        )
    }

    private suspend fun summarizeText(text: String, targetTokens: Int): String {
        val systemPrompt = """
            You are a text summarization expert.
            Summarize the following text concisely while preserving key information.
            Target length: approximately $targetTokens tokens.
            Focus on main ideas and important details.
        """.trimIndent()

        val messages = listOf(
            Message(
                role = "user",
                content = "Summarize this text:\n\n$text"
            )
        )

        val response = client.sendConversation(
            messages = messages,
            systemPrompt = systemPrompt,
            maxTokens = targetTokens,
            temperature = 0.3 // Низкая температура для более детерминированных результатов
        )

        return response.content
    }

    override fun getName(): String = "Summarization_${(compressionRatio * 100).toInt()}%"
}

/**
 * Экстрактивная суммаризация (без использования AI)
 * Выбирает наиболее важные предложения на основе эвристик
 */
class ExtractiveSummarizationStrategy : CompressionStrategy {

    override suspend fun process(
        text: String,
        tokenizer: Tokenizer,
        targetTokens: Int
    ): CompressionResult {
        val originalTokens = tokenizer.countTokens(text)

        if (originalTokens <= targetTokens) {
            return CompressionResult(
                originalText = text,
                processedText = text,
                originalTokens = originalTokens,
                processedTokens = originalTokens,
                strategy = getName(),
                transformationType = "none"
            )
        }

        // Разбиваем текст на предложения
        val sentences = text.split(Regex("[.!?]\\s+"))
            .filter { it.isNotBlank() }

        // Простая эвристика: выбираем предложения с наибольшим количеством слов
        val scoredSentences = sentences.map { sentence ->
            val score = calculateSentenceScore(sentence)
            Pair(sentence, score)
        }.sortedByDescending { it.second }

        // Набираем предложения до достижения целевого лимита
        val selectedSentences = mutableListOf<String>()
        var currentTokens = 0

        for ((sentence, _) in scoredSentences) {
            val sentenceTokens = tokenizer.countTokens(sentence)
            if (currentTokens + sentenceTokens <= targetTokens) {
                selectedSentences.add(sentence)
                currentTokens += sentenceTokens
            }
        }

        // Восстанавливаем порядок предложений как в оригинале
        val processedText = selectedSentences
            .sortedBy { sentences.indexOf(it) }
            .joinToString(". ") + "."

        val processedTokens = tokenizer.countTokens(processedText)

        return CompressionResult(
            originalText = text,
            processedText = processedText,
            originalTokens = originalTokens,
            processedTokens = processedTokens,
            strategy = getName(),
            transformationType = "summarization"
        )
    }

    private fun calculateSentenceScore(sentence: String): Double {
        // Простая эвристика: количество слов * наличие ключевых слов
        val words = sentence.split(Regex("\\s+")).filter { it.isNotBlank() }
        val keywordBonus = if (sentence.contains(Regex("important|key|main|significant|critical", RegexOption.IGNORE_CASE))) {
            1.5
        } else {
            1.0
        }
        return words.size * keywordBonus
    }

    override fun getName(): String = "ExtractiveSummarization"
}
