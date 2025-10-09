package org.kozyrev.claude

import kotlinx.coroutines.runBlocking
import java.util.logging.Logger

enum class LLMProvider {
    CLAUDE,
    YANDEX_GPT
}

class ChatManager(
    private val claudeClient: AIClient,
    private val yandexClient: AIClient? = null,
    private val systemPrompt: String? = null
) {
    private val logger = Logger.getLogger(ChatManager::class.java.name)
    private val conversationManager = ConversationManager()
    private var temperature: Double = 1.0
    private var currentProvider = LLMProvider.CLAUDE

    private val client: AIClient
        get() = when (currentProvider) {
            LLMProvider.CLAUDE -> claudeClient
            LLMProvider.YANDEX_GPT -> yandexClient ?: claudeClient
        }

    private val defaultSystemPrompt = """
        Ты - дружелюбный AI помощник.

        Твоя роль:
        1. Помогать пользователям с их вопросами
        2. Быть вежливым и конструктивным
        3. Давать чёткие и понятные ответы
        4. Отвечать на русском языке, если вопрос на русском
    """.trimIndent()

    /**
     * Отправляет сообщение пользователя и получает ответ от AI
     */
    suspend fun sendMessage(userMessage: String): AIResponse {
        // Добавляем сообщение пользователя в историю
        conversationManager.addUserMessage(userMessage)

        try {
            // Используем заданный системный промпт или дефолтный
            val systemPromptToUse = systemPrompt ?: defaultSystemPrompt

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
        } catch (e: Exception) {
            // При ошибке удаляем последнее сообщение пользователя, чтобы не нарушать чередование ролей
            conversationManager.removeLastMessage()
            logger.severe("Ошибка при отправке сообщения, откат последнего сообщения: ${e.message}")
            throw e
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
     * Переключает LLM провайдера
     */
    fun setLLMProvider(provider: LLMProvider) {
        if (provider == LLMProvider.YANDEX_GPT && yandexClient == null) {
            logger.warning("Yandex GPT клиент не инициализирован, остаюсь на Claude")
            return
        }
        currentProvider = provider
        logger.info("LLM провайдер изменён на: $provider")
    }

    /**
     * Получает текущего провайдера
     */
    fun getLLMProvider(): LLMProvider = currentProvider

    /**
     * Проверяет, доступен ли Yandex GPT
     */
    fun isYandexGPTAvailable(): Boolean = yandexClient != null

    /**
     * Запускает интерактивный чат с пользователем
     */
    fun startChat() {
        println("=".repeat(60))
        println("Чат с AI")
        println("Введите 'выход' для завершения")
        println("=".repeat(60))

        while (true) {
            print("\nВы: ")
            val userInput = readlnOrNull()?.trim() ?: break

            if (userInput.isEmpty()) continue

            if (userInput.equals("выход", ignoreCase = true)) {
                println("\nДо свидания!")
                break
            }

            try {
                val response = sendMessageSync(userInput)
                println("\n${response.providerName}: ${response.content}")
            } catch (e: Exception) {
                logger.severe("Ошибка: ${e.message}")
                println("\nОшибка: ${e.message}")
            }
        }
    }
}