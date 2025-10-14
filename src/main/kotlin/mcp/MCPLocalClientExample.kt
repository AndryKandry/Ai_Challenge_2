package org.kozyrev.mcp

import kotlinx.coroutines.runBlocking

/**
 * Пример использования MCPLocalClient для работы с локальным MCP сервером
 *
 * Этот пример демонстрирует:
 * 1. Подключение к локальному MCP серверу
 * 2. Получение списка доступных инструментов
 * 3. Вызов инструментов (greet, add, ttools)
 * 4. Корректное завершение сессии
 *
 * Перед запуском убедитесь, что локальный MCP сервер запущен:
 * ```bash
 * python your_mcp_server.py
 * ```
 */
fun main() = runBlocking {
    println("=== Пример работы с локальным MCP сервером ===\n")

    // Создаем клиент с использованием use блока для автоматического закрытия
    MCPLocalClient("http://127.0.0.1:8000/mcp").use { client ->

        // 1. Инициализация (происходит автоматически при первом запросе)
        println("1. Инициализация соединения...")
        val initResult = client.initialize()
        if (initResult.isSuccess) {
            val info = initResult.getOrNull()
            println("   ✓ Подключено к серверу: ${info?.serverInfo?.name} v${info?.serverInfo?.version}")
            println("   ✓ Версия протокола: ${info?.protocolVersion}\n")
        } else {
            println("   ✗ Ошибка инициализации: ${initResult.exceptionOrNull()?.message}\n")
            return@runBlocking
        }

        // 2. Получение списка инструментов
        println("2. Получение списка доступных инструментов...")
        val toolsResult = client.listTools()
        if (toolsResult.isSuccess) {
            val tools = toolsResult.getOrNull() ?: emptyList()
            println("   ✓ Найдено инструментов: ${tools.size}")
            tools.forEach { tool ->
                println("     - ${tool.name}: ${tool.description ?: "нет описания"}")
            }
            println()
        } else {
            println("   ✗ Ошибка получения списка: ${toolsResult.exceptionOrNull()?.message}\n")
        }

        // 3. Вызов инструмента ttools (получение информации о инструментах)
        println("3. Вызов инструмента 'ttools' (детальная информация)...")
        val ttoolsResult = client.callTool("ttools", emptyMap())
        if (ttoolsResult.isSuccess) {
            val result = ttoolsResult.getOrNull()
            result?.content?.forEach { content ->
                if (content.type == "text" && content.text != null) {
                    println("   ✓ Результат:")
                    println("   ${content.text}")
                }
            }
            println()
        } else {
            println("   ✗ Ошибка вызова: ${ttoolsResult.exceptionOrNull()?.message}\n")
        }

        // 4. Вызов инструмента greet с аргументом
        println("4. Вызов инструмента 'greet' с аргументом name='Kotlin'...")
        val greetResult = client.callTool("greet", mapOf("name" to "Kotlin"))
        if (greetResult.isSuccess) {
            val result = greetResult.getOrNull()
            result?.content?.forEach { content ->
                if (content.type == "text" && content.text != null) {
                    println("   ✓ Ответ сервера: ${content.text}")
                }
            }
            println()
        } else {
            println("   ✗ Ошибка вызова: ${greetResult.exceptionOrNull()?.message}\n")
        }

        // 5. Вызов инструмента add с двумя числами
        println("5. Вызов инструмента 'add' с аргументами a=15, b=27...")
        val addResult = client.callTool("add", mapOf("a" to 15, "b" to 27))
        if (addResult.isSuccess) {
            val result = addResult.getOrNull()
            result?.content?.forEach { content ->
                if (content.type == "text" && content.text != null) {
                    println("   ✓ Результат вычисления: ${content.text}")
                }
            }
            println()
        } else {
            println("   ✗ Ошибка вызова: ${addResult.exceptionOrNull()?.message}\n")
        }

        // 6. Демонстрация обработки ошибок - вызов несуществующего инструмента
        println("6. Попытка вызова несуществующего инструмента 'nonexistent'...")
        val errorResult = client.callTool("nonexistent", emptyMap())
        if (errorResult.isSuccess) {
            println("   ✓ Неожиданный успех")
        } else {
            println("   ✓ Ожидаемая ошибка: ${errorResult.exceptionOrNull()?.message}\n")
        }

        println("=== Завершение работы (соединение закрывается автоматически) ===")
    }
}
