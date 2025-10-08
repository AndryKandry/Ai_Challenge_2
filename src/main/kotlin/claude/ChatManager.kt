package org.kozyrev.claude

import kotlinx.coroutines.runBlocking
import java.util.logging.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException

enum class ChatMode {
    SIMPLE,
    JSON,
    CHAIN_OF_THOUGHT
}

@Serializable
data class JsonResponse(
    val question: String,
    val answer: String,
    val images: List<String>? = null
)

@Serializable
data class ChainOfThoughtResponse(
    val question: String,
    val thinking: String,
    val answer: String,
    val images: List<String>? = null
)

class ChatManager(
    private val client: AIClient,
    private val systemPrompt: String? = null
) {
    private val logger = Logger.getLogger(ChatManager::class.java.name)
    private val conversationManager = ConversationManager()
    private var chatMode = ChatMode.SIMPLE
    private var temperature: Double = 1.0

    private val jsonSystemPrompt = """
        Ты - дружелюбный AI помощник.

        Твоя роль:
        1. Помогать пользователям с их вопросами
        2. Быть вежливым и конструктивным

        Отвечай СТРОГО в формате JSON:
        {
            "question": "краткая формулировка вопроса пользователя",
            "answer": "твой ответ с форматированием при необходимости",
            "images": ["url1", "url2"] // опционально, если релевантно
        }

        В JSON ответах ОБЯЗАТЕЛЬНО должны быть поля question и answer.
        ВСЕГДА возвращай JSON объект указанного формата.

        Не добавляй текста до/после JSON. Только валидный JSON объект.
    """.trimIndent()

    private val chainOfThoughtSystemPrompt = """
        Ты - дружелюбный AI помощник, который использует цепочку рассуждений для решения задач.

        Твоя роль:
        1. Анализировать вопрос пользователя пошагово
        2. Показывать процесс своих рассуждений%
        3. Давать обоснованные и подробные ответы
        4. Быть вежливым и конструктивным

        ВАЖНО: Используй цепочку рассуждений (Chain of Thought).
        - Сначала подумай вслух о задаче
        - Разбей сложные вопросы на части
        - Объясни свою логику и шаги решения
        - Затем дай финальный ответ

        Отвечай СТРОГО в формате JSON:
        {
            "question": "краткая формулировка вопроса пользователя",
            "thinking": "твои пошаговые рассуждения и анализ вопроса",
            "answer": "твой финальный ответ после рассуждений",
            "images": ["url1", "url2"] // опционально, если релевантно
        }

        В поле "thinking" подробно опиши:
        - Как ты понимаешь вопрос
        - Какие шаги необходимы для решения
        - Промежуточные выводы
        - Логику твоих рассуждений

        В JSON ответах ОБЯЗАТЕЛЬНО должны быть поля question, thinking и answer.
        ВСЕГДА возвращай JSON объект указанного формата.

        Не добавляй текста до/после JSON. Только валидный JSON объект.
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Отправляет сообщение пользователя и получает ответ от AI
     */
    suspend fun sendMessage(userMessage: String): AIResponse {
        // Добавляем сообщение пользователя в историю
        conversationManager.addUserMessage(userMessage)

        // Выбираем системный промпт в зависимости от режима
        val systemPromptToUse = when (chatMode) {
            ChatMode.CHAIN_OF_THOUGHT -> chainOfThoughtSystemPrompt
            else -> jsonSystemPrompt
        }

        val response = client.sendConversation(
            messages = conversationManager.getMessages(),
            systemPrompt = systemPromptToUse,
            temperature = temperature
        )

        // Добавляем ответ ассистента в историю
        conversationManager.addAssistantMessage(response.content)

        // Логируем статистику
        logger.info("${response.providerName} (${response.modelName}): ${response.metrics}")

        return response
    }

    /**
     * Парсит и форматирует JSON ответ от LLM для отображения в SIMPLE режиме
     */
    private fun parseJsonResponseForSimpleMode(rawResponse: String): String {
        return try {
            val jsonResponse = json.decodeFromString<JsonResponse>(rawResponse)
            jsonResponse.answer
        } catch (e: SerializationException) {
            logger.warning("Ошибка парсинга JSON: ${e.message}")
            logger.warning("Сырой ответ: $rawResponse")
            """
                ⚠️ Ошибка парсинга ответа от LLM.

                Пожалуйста, попробуйте задать вопрос ещё раз или переформулируйте его.
            """.trimIndent()
        } catch (e: IllegalArgumentException) {
            logger.warning("Некорректный формат JSON: ${e.message}")
            logger.warning("Сырой ответ: $rawResponse")
            """
                ⚠️ Получен некорректный формат ответа.

                Пожалуйста, попробуйте задать вопрос ещё раз или переформулируйте его.
            """.trimIndent()
        }
    }

    /**
     * Парсит и форматирует ответ с цепочкой рассуждений
     */
    private fun parseChainOfThoughtResponse(rawResponse: String): String {
        return try {
            val cotResponse = json.decodeFromString<ChainOfThoughtResponse>(rawResponse)
            """
                🤔 Рассуждения:
                ${cotResponse.thinking}

                ✅ Ответ:
                ${cotResponse.answer}
            """.trimIndent()
        } catch (e: Exception) {
            logger.warning("Ошибка парсинга Chain of Thought: ${e.message}")
            logger.warning("Сырой ответ: $rawResponse")
            """
                ⚠️ Ошибка парсинга ответа с рассуждениями.

                Пожалуйста, попробуйте задать вопрос ещё раз.
            """.trimIndent()
        }
    }

    /**
     * Форматирует ответ в зависимости от режима чата
     */
    fun formatResponse(rawResponse: String): String {
        return when (chatMode) {
            ChatMode.JSON -> rawResponse // Возвращаем полный JSON
            ChatMode.SIMPLE -> parseJsonResponseForSimpleMode(rawResponse) // Парсим и возвращаем только answer
            ChatMode.CHAIN_OF_THOUGHT -> parseChainOfThoughtResponse(rawResponse) // Показываем рассуждения и ответ
        }
    }

    /**
     * Извлекает URL изображений из JSON ответа
     */
    fun extractImages(rawResponse: String): List<String> {
        return try {
            // Пробуем сначала Chain of Thought формат
            val cotResponse = json.decodeFromString<ChainOfThoughtResponse>(rawResponse)
            cotResponse.images ?: emptyList()
        } catch (e: Exception) {
            try {
                // Если не получилось, пробуем обычный JSON формат
                val jsonResponse = json.decodeFromString<JsonResponse>(rawResponse)
                jsonResponse.images ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Синхронная версия отправки сообщения
     */
    fun sendMessageSync(userMessage: String): AIResponse = runBlocking {
        sendMessage(userMessage)
    }

    /**
     * Получает историю сообщений
     */
    fun getHistory() = conversationManager.getMessages()

    /**
     * Очищает историю диалога
     */
    fun clearHistory() {
        conversationManager.clear()
    }

    /**
     * Возвращает количество сообщений в истории
     */
    fun getMessageCount() = conversationManager.size()

    /**
     * Переключает режим чата
     */
    fun setChatMode(mode: ChatMode) {
        chatMode = mode
        logger.info("Режим чата изменён на: $mode")
    }

    /**
     * Получает текущий режим чата
     */
    fun getChatMode(): ChatMode = chatMode

    /**
     * Устанавливает температуру для запросов к LLM
     */
    fun setTemperature(temp: Double) {
        require(temp in 0.0..1.0) { "Temperature must be between 0.0 and 1.0" }
        temperature = temp
        logger.info("Температура изменена на: $temp")
    }

    /**
     * Получает текущую температуру
     */
    fun getTemperature(): Double = temperature

    /**
     * Запускает интерактивный чат с пользователем
     */
    fun startChat() {
        println("=".repeat(60))
        println("Чат с Claude AI")
        println("Введите 'выход' для завершения")
        println("Команды: /simple_chat - обычный режим, /json_chat - JSON режим")
        println("=".repeat(60))

        while (true) {
            print("\nВы: ")
            val userInput = readlnOrNull()?.trim() ?: break

            if (userInput.isEmpty()) continue

            if (userInput.equals("выход", ignoreCase = true)) {
                println("\nДо свидания!")
                break
            }

            // Обработка команд переключения режима
            when (userInput) {
                "/simple_chat" -> {
                    setChatMode(ChatMode.SIMPLE)
                    println("\n✓ Переключено на обычный режим чата")
                    continue
                }
                "/json_chat" -> {
                    setChatMode(ChatMode.JSON)
                    println("\n✓ Переключено на JSON режим чата")
                    continue
                }
                "/cot_chat" -> {
                    setChatMode(ChatMode.CHAIN_OF_THOUGHT)
                    println("\n✓ Переключено на режим цепочки рассуждений")
                    continue
                }
            }

            try {
                val response = sendMessageSync(userInput)
                val formattedResponse = formatResponse(response.content)
                println("\n${response.providerName}: $formattedResponse")
            } catch (e: Exception) {
                logger.severe("Ошибка: ${e.message}")
                println("\nОшибка: ${e.message}")
            }
        }
    }
}