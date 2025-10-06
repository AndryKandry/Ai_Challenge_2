package org.kozyrev.claude

import kotlinx.coroutines.runBlocking
import java.util.logging.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException

enum class ChatMode {
    SIMPLE,
    JSON
}

@Serializable
data class JsonResponse(
    val question: String,
    val answer: String,
    val images: List<String>? = null
)

class ChatManager(
    private val client: ClaudeClient,
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
        2. Задавать уточняющие вопросы при необходимости
        3. Давать подробные и полезные ответы
        4. Быть вежливым и конструктивным

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

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Отправляет сообщение пользователя и получает ответ от AI
     */
    suspend fun sendMessage(userMessage: String): String {
        // Добавляем сообщение пользователя в историю
        conversationManager.addUserMessage(userMessage)

        // Всегда используем jsonSystemPrompt для получения структурированного ответа
        val response = client.sendConversation(
            messages = conversationManager.getMessages(),
            systemPrompt = jsonSystemPrompt,
            temperature = temperature
        )

        // Извлекаем текст ответа
        val assistantMessage = response.content.firstOrNull()?.text
            ?: throw IllegalStateException("Пустой ответ от API")

        // Добавляем ответ ассистента в историю
        conversationManager.addAssistantMessage(assistantMessage)

        // Логируем статистику токенов
        response.usage?.let { usage ->
            logger.info("Токены - Вход: ${usage.inputTokens}, Выход: ${usage.outputTokens}")
        }

        return assistantMessage
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
     * Форматирует ответ в зависимости от режима чата
     */
    fun formatResponse(rawResponse: String): String {
        return when (chatMode) {
            ChatMode.JSON -> rawResponse // Возвращаем полный JSON
            ChatMode.SIMPLE -> parseJsonResponseForSimpleMode(rawResponse) // Парсим и возвращаем только answer
        }
    }

    /**
     * Извлекает URL изображений из JSON ответа
     */
    fun extractImages(rawResponse: String): List<String> {
        return try {
            val jsonResponse = json.decodeFromString<JsonResponse>(rawResponse)
            jsonResponse.images ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Синхронная версия отправки сообщения
     */
    fun sendMessageSync(userMessage: String): String = runBlocking {
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
            }

            try {
                val response = sendMessageSync(userInput)
                val formattedResponse = formatResponse(response)
                println("\nClaude: $formattedResponse")
            } catch (e: Exception) {
                logger.severe("Ошибка: ${e.message}")
                println("\nОшибка: ${e.message}")
            }
        }
    }
}