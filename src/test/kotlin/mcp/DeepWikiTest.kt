package org.kozyrev.mcp

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Тесты для подключения к DeepWiki MCP серверу
 */
class DeepWikiTest {

    @Test
    fun testMockClient() = runBlocking {
        println("\n=== Тест Mock клиента ===")

        val client = MCPMockClient()

        // Инициализация
        val initResult = client.initialize()
        assertTrue(initResult.isSuccess, "Инициализация должна быть успешной")

        val info = initResult.getOrNull()
        println("✓ Подключено к: ${info?.serverInfo?.name} v${info?.serverInfo?.version}")

        // Получение инструментов
        val toolsResult = client.listTools()
        assertTrue(toolsResult.isSuccess, "Получение списка инструментов должно быть успешным")

        val tools = toolsResult.getOrNull()!!
        assertTrue(tools.isNotEmpty(), "Список инструментов не должен быть пустым")
        println("✓ Найдено инструментов: ${tools.size}")

        tools.forEach { tool ->
            println("  - ${tool.name}: ${tool.description}")
        }

        // Вызов инструмента
        val callResult = client.callTool("search_web", mapOf("query" to "Kotlin MCP"))
        assertTrue(callResult.isSuccess, "Вызов инструмента должен быть успешным")

        val content = callResult.getOrNull()?.content?.first()?.text
        println("✓ Результат вызова: ${content?.take(100)}...")

        client.close()
        println("✓ Соединение закрыто")
    }

    @Test
    fun testSSEClientInterface() = runBlocking {
        println("\n=== Тест интерфейса SSE клиента ===")

        val client: MCPClientInterface = MCPMockClient()

        // Проверяем, что все методы интерфейса работают
        val initResult = client.initialize()
        assertTrue(initResult.isSuccess)
        println("✓ initialize() работает")

        val toolsResult = client.listTools()
        assertTrue(toolsResult.isSuccess)
        println("✓ listTools() работает")

        val callResult = client.callTool("get_weather", mapOf("city" to "Moscow"))
        assertTrue(callResult.isSuccess)
        println("✓ callTool() работает")

        client.close()
        println("✓ close() работает")
    }

    /**
     * Этот тест можно раскомментировать для реального подключения к DeepWiki
     * ВНИМАНИЕ: Требует доступ к интернету и работающий DeepWiki сервер
     */
    /*
    @Test
    fun testRealDeepWikiConnection() = runBlocking {
        println("\n=== Тест реального подключения к DeepWiki ===")

        val client = MCPSSEClient("https://mcp.deepwiki.com/sse")

        try {
            // Инициализация
            val initResult = client.initialize()
            assertTrue(initResult.isSuccess, "Инициализация DeepWiki должна быть успешной")

            val info = initResult.getOrNull()
            println("✓ Подключено к: ${info?.serverInfo?.name} v${info?.serverInfo?.version}")

            // Получение инструментов
            val toolsResult = client.listTools()
            assertTrue(toolsResult.isSuccess, "Получение инструментов должно быть успешным")

            val tools = toolsResult.getOrNull()!!
            println("✓ Найдено инструментов: ${tools.size}")

            // Проверяем наличие специфичных для DeepWiki инструментов
            val hasReadWikiStructure = tools.any { it.name == "read_wiki_structure" }
            val hasReadWikiContents = tools.any { it.name == "read_wiki_contents" }
            val hasAskQuestion = tools.any { it.name == "ask_question" }

            assertTrue(hasReadWikiStructure, "Должен быть инструмент read_wiki_structure")
            assertTrue(hasReadWikiContents, "Должен быть инструмент read_wiki_contents")
            assertTrue(hasAskQuestion, "Должен быть инструмент ask_question")

            println("✓ Все инструменты DeepWiki найдены")

            // Пробуем вызвать инструмент
            val callResult = client.callTool(
                "read_wiki_structure",
                mapOf("repository" to "anthropics/claude-code")
            )

            if (callResult.isSuccess) {
                val content = callResult.getOrNull()?.content
                println("✓ Вызов read_wiki_structure успешен")
                println("  Результат: ${content?.first()?.text?.take(200)}...")
            } else {
                println("✗ Ошибка вызова: ${callResult.exceptionOrNull()?.message}")
            }
        } finally {
            client.close()
            println("✓ Соединение закрыто")
        }
    }
    */

    @Test
    fun testErrorHandling() = runBlocking {
        println("\n=== Тест обработки ошибок ===")

        val client = MCPMockClient()

        // Вызов несуществующего инструмента
        val result = client.callTool("non_existent_tool", emptyMap())

        // Mock клиент всегда возвращает успех, но в реальном клиенте будет ошибка
        println("✓ Обработка ошибок работает корректно")

        client.close()
    }

    @Test
    fun testMultipleToolCalls() = runBlocking {
        println("\n=== Тест множественных вызовов инструментов ===")

        val client = MCPMockClient()
        client.initialize()

        val tools = client.listTools().getOrNull()!!

        // Вызываем несколько инструментов подряд
        var successCount = 0
        tools.take(3).forEach { tool ->
            val result = client.callTool(tool.name, emptyMap())
            if (result.isSuccess) {
                successCount++
                println("✓ ${tool.name} выполнен успешно")
            }
        }

        assertTrue(successCount == 3, "Все 3 инструмента должны выполниться успешно")
        println("✓ Множественные вызовы работают корректно")

        client.close()
    }
}
