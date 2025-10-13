package org.kozyrev.claude.tokenization

/**
 * Интерфейс токенизатора для подсчёта токенов
 */
interface Tokenizer {
    /**
     * Подсчитывает количество токенов в тексте
     * @param text текст для анализа
     * @return количество токенов
     */
    fun countTokens(text: String): Int

    /**
     * Кодирует текст в токены
     * @param text текст для кодирования
     * @return список ID токенов
     */
    fun encode(text: String): List<Int>

    /**
     * Декодирует токены обратно в текст
     * @param tokens список ID токенов
     * @return декодированный текст
     */
    fun decode(tokens: List<Int>): String

    /**
     * Возвращает имя токенизатора
     */
    fun getName(): String
}

/**
 * Простой токенизатор на основе пробелов (fallback)
 * Приближённая оценка: ~1.3 токена на слово для английского текста
 */
class SimpleTokenizer : Tokenizer {
    private val tokensPerWord = 1.3

    override fun countTokens(text: String): Int {
        // Простая эвристика: считаем слова и применяем коэффициент
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        return (words.size * tokensPerWord).toInt()
    }

    override fun encode(text: String): List<Int> {
        // Простая кодировка: каждое слово = один токен с хешем
        return text.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.hashCode() }
    }

    override fun decode(tokens: List<Int>): String {
        // Декодирование невозможно для простого токенизатора
        return "[Decoded tokens: ${tokens.size}]"
    }

    override fun getName(): String = "SimpleTokenizer"
}

/**
 * Токенизатор для моделей Claude (приближённая оценка)
 * Claude использует собственный токенизатор, похожий на GPT
 */
class ClaudeTokenizer : Tokenizer {
    private val avgCharsPerToken = 4.0 // В среднем 4 символа на токен

    override fun countTokens(text: String): Int {
        // Приближённая оценка на основе количества символов
        return (text.length / avgCharsPerToken).toInt().coerceAtLeast(1)
    }

    override fun encode(text: String): List<Int> {
        // Простая эмуляция: разбиваем на чанки по avgCharsPerToken символов
        val chunks = text.chunked(avgCharsPerToken.toInt())
        return chunks.map { it.hashCode() }
    }

    override fun decode(tokens: List<Int>): String {
        return "[Claude tokens: ${tokens.size}]"
    }

    override fun getName(): String = "ClaudeTokenizer"
}

/**
 * Токенизатор для моделей GPT (OpenAI-совместимых)
 * Использует приближённую оценку, аналогичную tiktoken
 */
class GPTTokenizer : Tokenizer {
    private val avgCharsPerToken = 4.0 // В среднем 4 символа на токен для GPT

    override fun countTokens(text: String): Int {
        // Более точная эвристика для GPT моделей
        val chars = text.length
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size

        // Комбинированная оценка
        val charBasedEstimate = (chars / avgCharsPerToken).toInt()
        val wordBasedEstimate = (words * 1.3).toInt()

        // Берём среднее между двумя оценками
        return ((charBasedEstimate + wordBasedEstimate) / 2).coerceAtLeast(1)
    }

    override fun encode(text: String): List<Int> {
        // Эмуляция BPE токенизации
        val tokens = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            val chunkSize = minOf(avgCharsPerToken.toInt(), text.length - i)
            val chunk = text.substring(i, i + chunkSize)
            tokens.add(chunk.hashCode())
            i += chunkSize
        }
        return tokens
    }

    override fun decode(tokens: List<Int>): String {
        return "[GPT tokens: ${tokens.size}]"
    }

    override fun getName(): String = "GPTTokenizer"
}

/**
 * Фабрика токенизаторов
 */
object TokenizerFactory {
    /**
     * Создаёт токенизатор на основе имени модели
     */
    fun forModel(modelName: String): Tokenizer {
        return when {
            modelName.contains("claude", ignoreCase = true) -> ClaudeTokenizer()
            modelName.contains("gpt", ignoreCase = true) -> GPTTokenizer()
            modelName.contains("llama", ignoreCase = true) -> GPTTokenizer() // Llama похож на GPT
            else -> SimpleTokenizer()
        }
    }

    /**
     * Создаёт токенизатор по имени
     */
    fun byName(name: String): Tokenizer {
        return when (name.lowercase()) {
            "claude" -> ClaudeTokenizer()
            "gpt", "openai" -> GPTTokenizer()
            "simple" -> SimpleTokenizer()
            else -> SimpleTokenizer()
        }
    }
}
