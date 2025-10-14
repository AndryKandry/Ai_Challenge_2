package tokenization

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.kozyrev.claude.tokenization.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextCompressionTest {

    @Test
    fun testTruncationKeepStart() = runBlocking {
        val tokenizer = SimpleTokenizer()
        val strategy = TruncationStrategy(TruncationStrategy.TruncationMode.KEEP_START)

        val text = "This is a long text that should be truncated to fit within the token limit. " +
                "It contains multiple sentences and should be cut at some point. " +
                "We want to keep only the beginning of this text."

        val targetTokens = 10

        val result = strategy.process(text, tokenizer, targetTokens)

        assertEquals("truncation", result.transformationType)
        // Допускаем небольшую погрешность в 2 токена из-за приблизительной оценки
        assertTrue(result.processedTokens <= targetTokens + 2,
            "Processed tokens should be <= target + margin: ${result.processedTokens} > ${targetTokens + 2}")
        assertTrue(result.processedText.length < text.length,
            "Processed text should be shorter than original")
    }

    @Test
    fun testTruncationKeepEnd() = runBlocking {
        val tokenizer = SimpleTokenizer()
        val strategy = TruncationStrategy(TruncationStrategy.TruncationMode.KEEP_END)

        val text = "Beginning of text. Middle of text. End of text that we want to keep."
        val targetTokens = 5

        val result = strategy.process(text, tokenizer, targetTokens)

        assertEquals("truncation", result.transformationType)
        assertTrue(result.processedText.contains("keep") || result.processedText.contains("End"),
            "Should keep the end of the text")
    }

    @Test
    fun testTruncationKeepBoth() = runBlocking {
        val tokenizer = SimpleTokenizer()
        val strategy = TruncationStrategy(TruncationStrategy.TruncationMode.KEEP_BOTH)

        val text = "Start of document. " + "Middle content. ".repeat(10) + "End of document."
        val targetTokens = 10

        val result = strategy.process(text, tokenizer, targetTokens)

        assertEquals("truncation", result.transformationType)
        assertTrue(result.processedText.contains("..."), "Should contain ellipsis marker")
    }

    @Test
    fun testNoCompressionNeeded() = runBlocking {
        val tokenizer = SimpleTokenizer()
        val strategy = TruncationStrategy()

        val text = "Short text"
        val targetTokens = 100

        val result = strategy.process(text, tokenizer, targetTokens)

        assertEquals("none", result.transformationType)
        assertEquals(text, result.processedText, "Text should remain unchanged")
    }

    @Test
    fun testExtractiveSummarization() = runBlocking {
        val tokenizer = SimpleTokenizer()
        val strategy = ExtractiveSummarizationStrategy()

        val text = """
            This is the first sentence with important information.
            This is the second sentence.
            This is the third sentence with key details.
            This is the fourth sentence.
            This is the fifth sentence with critical data.
        """.trimIndent()

        val targetTokens = 15

        val result = strategy.process(text, tokenizer, targetTokens)

        // Должны выбрать предложения с ключевыми словами
        assertTrue(result.processedText.contains("important") ||
                   result.processedText.contains("key") ||
                   result.processedText.contains("critical"),
            "Should select sentences with keywords")
    }

    @Test
    fun testCompressionResultLogging() {
        val result = CompressionResult(
            originalText = "Original text",
            processedText = "Processed",
            originalTokens = 100,
            processedTokens = 50,
            strategy = "TestStrategy",
            transformationType = "truncation"
        )

        val logString = result.toLogString()

        assertTrue(logString.contains("100"))
        assertTrue(logString.contains("50"))
        assertTrue(logString.contains("truncation"))
    }

    @Test
    fun testCompressionStrategyNames() {
        val truncation = TruncationStrategy(TruncationStrategy.TruncationMode.KEEP_START)
        assertEquals("Truncation_KEEP_START", truncation.getName())

        val extractive = ExtractiveSummarizationStrategy()
        assertEquals("ExtractiveSummarization", extractive.getName())
    }
}
