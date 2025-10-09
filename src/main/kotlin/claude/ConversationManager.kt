package org.kozyrev.claude

import java.util.logging.Logger

class ConversationManager() {

    private val logger = Logger.getLogger(ConversationManager::class.java.name)
    private val messages = mutableListOf<Message>()

    /**
     * Добавляет сообщение в историю с проверкой чередования ролей
     */
    fun addMessage(role: String, content: String) {
        require(role == "user" || role == "assistant") {
            "Role must be 'user' or 'assistant', got: '$role'"
        }

        // Проверка правильного чередования ролей
        if (messages.isNotEmpty()) {
            val lastRole = messages.last().role
            if (lastRole == role) {
                throw ClaudeException.CommonException(
                    "Role alternation violation: cannot add '$role' after '$lastRole'. " +
                            "Roles must alternate between 'user' and 'assistant'"
                )
            }
        }

        messages.add(Message(role, content))
        logger.fine("Added message (role: $role), total messages: ${messages.size}")
    }

    /**
     * Добавляет сообщение пользователя
     */
    fun addUserMessage(content: String) {
        addMessage("user", content)
    }

    /**
     * Добавляет сообщение ассистента
     */
    fun addAssistantMessage(content: String) {
        addMessage("assistant", content)
    }

    /**
     * Получает всю историю сообщений
     */
    fun getMessages(): List<Message> = messages.toList()

    /**
     * Проверяет, есть ли сообщения в истории
     */
    fun hasMessages(): Boolean = messages.isNotEmpty()

    /**
     * Очищает историю
     */
    fun clear() {
        messages.clear()
        logger.info("Conversation history cleared")
    }

    /**
     * Возвращает количество сообщений
     */
    fun size(): Int = messages.size

    /**
     * Удаляет последнее сообщение из истории
     * Используется для отката при ошибках
     */
    fun removeLastMessage() {
        if (messages.isNotEmpty()) {
            val removed = messages.removeAt(messages.size - 1)
            logger.info("Removed last message (role: ${removed.role}), remaining messages: ${messages.size}")
        } else {
            logger.warning("Attempted to remove last message, but history is empty")
        }
    }
}