package org.kozyrev

import kotlinx.coroutines.runBlocking
import org.kozyrev.claude.ClaudeClient
import org.kozyrev.claude.ConversationManager
import java.util.logging.Logger

class ChatManager(
    private val client: ClaudeClient,
    private val systemPrompt: String? = null
) {
    private val logger = Logger.getLogger(ChatManager::class.java.name)
    private val conversationManager = ConversationManager()

    /**
     * Отправляет сообщение пользователя и получает ответ от AI
     */
    suspend fun sendMessage(userMessage: String): String {
        // Добавляем сообщение пользователя в историю
        conversationManager.addUserMessage(userMessage)

        // Отправляем диалог в Claude API
        val response = client.sendConversation(
            messages = conversationManager.getMessages(),
            systemPrompt = systemPrompt
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
     * Запускает интерактивный чат с пользователем
     */
    fun startChat() {
        println("=".repeat(60))
        println("Чат с Claude AI")
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
                println("\nClaude: $response")
            } catch (e: Exception) {
                logger.severe("Ошибка: ${e.message}")
                println("\nОшибка: ${e.message}")
            }
        }
    }
}
