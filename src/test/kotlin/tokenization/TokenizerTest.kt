package tokenization

import org.junit.Test
import org.kozyrev.claude.tokenization.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenizerTest {

    @Test
    fun testSimpleTokenizer() {
        val tokenizer = SimpleTokenizer()
        val text = "Hello world this is a test"
        val tokens = tokenizer.countTokens(text)

        // Ожидаем примерно 6 слов * 1.3 = ~7-8 токенов
        assertTrue(tokens in 6..9, "Expected 6-9 tokens, got $tokens")
    }

    @Test
    fun testClaudeTokenizer() {
        val tokenizer = ClaudeTokenizer()
        val text = "Hello world"

        val tokens = tokenizer.countTokens(text)

        // "Hello world" = 11 символов / 4 = ~2-3 токена
        assertTrue(tokens in 2..4, "Expected 2-4 tokens, got $tokens")
    }

    @Test
    fun testGPTTokenizer() {
        val tokenizer = GPTTokenizer()
        val text = "The quick brown fox jumps over the lazy dog"

        val tokens = tokenizer.countTokens(text)

        // Комбинированная оценка для 9 слов и 44 символов
        assertTrue(tokens > 0, "Expected positive token count")
        assertTrue(tokens < 20, "Expected less than 20 tokens for short sentence")
    }

    @Test
    fun testTokenizerFactory() {
        val claudeTokenizer = TokenizerFactory.forModel("claude-3-haiku-20240307")
        assertEquals("ClaudeTokenizer", claudeTokenizer.getName())

        val gptTokenizer = TokenizerFactory.forModel("gpt-4")
        assertEquals("GPTTokenizer", gptTokenizer.getName())

        val llamaTokenizer = TokenizerFactory.forModel("meta-llama/Llama-3.1-8B-Instruct")
        assertEquals("GPTTokenizer", llamaTokenizer.getName())

        val defaultTokenizer = TokenizerFactory.forModel("unknown-model")
        assertEquals("SimpleTokenizer", defaultTokenizer.getName())
    }

    @Test
    fun testEncodeAndDecode() {
        val tokenizer = GPTTokenizer()
        val text = "Test encoding"

        val tokens = tokenizer.encode(text)
        assertTrue(tokens.isNotEmpty(), "Encoded tokens should not be empty")

        val decoded = tokenizer.decode(tokens)
        assertTrue(decoded.contains("${tokens.size}"), "Decoded text should mention token count")
    }

    @Test
    fun testEmptyString() {
        val tokenizer = ClaudeTokenizer()
        val tokens = tokenizer.countTokens("")

        assertEquals(1, tokens, "Empty string should result in at least 1 token")
    }

    @Test
    fun testLongText() {
        val tokenizer = GPTTokenizer()
        val longText = "word ".repeat(1000)

        val tokens = tokenizer.countTokens(longText)
        assertTrue(tokens > 100, "Long text should have many tokens")
    }
}
