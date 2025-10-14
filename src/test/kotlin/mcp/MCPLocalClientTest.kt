package org.kozyrev.mcp

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

/**
 * Тесты для MCPLocalClient
 *
 * ВАЖНО: Перед запуском тестов убедитесь, что локальный MCP сервер запущен:
 * ```bash
 * python your_mcp_server.py
 * ```
 *
 * Сервер должен быть доступен по адресу http://127.0.0.1:8000/mcp
 * и предоставлять следующие инструменты:
 * - greet(name: str) -> str
 * - add(a: int, b: int) -> int
 * - ttools() -> json
 */
class MCPLocalClientTest {

    companion object {
        private const val LOCAL_SERVER_URL = "http://127.0.0.1:8000/mcp"
    }

    @Test
    fun `test initialization`() = runBlocking {
        MCPLocalClient(LOCAL_SERVER_URL).use { client ->
            val result = client.initialize()

            assertTrue(result.isSuccess, "Инициализация должна пройти успешно")

            val initResult = result.getOrNull()
            assertNotNull(initResult, "Результат инициализации не должен быть null")
            assertNotNull(initResult.serverInfo, "Информация о сервере должна присутствовать")
            assertEquals("2024-11-05", initResult.protocolVersion, "Версия протокола должна совпадать")

            println("✓ Тест инициализации пройден")
            println("  Сервер: ${initResult.serverInfo.name} v${initResult.serverInfo.version}")
        }
    }

    @Test
    fun `test list tools`() = runBlocking {
        MCPLocalClient(LOCAL_SERVER_URL).use { client ->
            val result = client.listTools()

            assertTrue(result.isSuccess, "Получение списка инструментов должно пройти успешно")

            val tools = result.getOrNull()
            assertNotNull(tools, "Список инструментов не должен быть null")
            assertTrue(tools.isNotEmpty(), "Должен быть хотя бы один инструмент")

            // Проверяем наличие ожидаемых инструментов
            val toolNames = tools.map { it.name }
            assertTrue(toolNames.contains("greet"), "Должен присутствовать инструмент 'greet'")
            assertTrue(toolNames.contains("add"), "Должен присутствовать инструмент 'add'")
            assertTrue(toolNames.contains("ttools"), "Должен присутствовать инструмент 'ttools'")

            println("✓ Тест получения списка инструментов пройден")
            println("  Найдено инструментов: ${tools.size}")
            tools.forEach { tool ->
                println("  - ${tool.name}")
            }
        }
    }

    @Test
    fun `test call greet tool`() = runBlocking {
        MCPLocalClient(LOCAL_SERVER_URL).use { client ->
            val result = client.callTool("greet", mapOf("name" to "Kotlin"))

            assertTrue(result.isSuccess, "Вызов инструмента 'greet' должен пройти успешно")

            val callResult = result.getOrNull()
            assertNotNull(callResult, "Результат вызова не должен быть null")
            assertTrue(callResult.content.isNotEmpty(), "Результат должен содержать контент")

            val textContent = callResult.content.firstOrNull { it.type == "text" }
            assertNotNull(textContent, "Должен быть текстовый контент")
            assertNotNull(textContent.text, "Текст не должен быть null")
            assertTrue(
                textContent.text.contains("Kotlin"),
                "Ответ должен содержать переданное имя"
            )

            println("✓ Тест вызова инструмента 'greet' пройден")
            println("  Ответ: ${textContent.text}")
        }
    }

    @Test
    fun `test call add tool`() = runBlocking {
        MCPLocalClient(LOCAL_SERVER_URL).use { client ->
            val a = 15
            val b = 27
            val expected = a + b

            val result = client.callTool("add", mapOf("a" to a, "b" to b))

            assertTrue(result.isSuccess, "Вызов инструмента 'add' должен пройти успешно")

            val callResult = result.getOrNull()
            assertNotNull(callResult, "Результат вызова не должен быть null")

            val textContent = callResult.content.firstOrNull { it.type == "text" }
            assertNotNull(textContent, "Должен быть текстовый контент")
            assertNotNull(textContent.text, "Текст не должен быть null")

            // Пробуем распарсить результат как число
            val resultValue = textContent.text.toIntOrNull()
            assertNotNull(resultValue, "Результат должен быть числом")
            assertEquals(expected, resultValue, "Результат сложения должен быть корректным")

            println("✓ Тест вызова инструмента 'add' пройден")
            println("  $a + $b = $resultValue")
        }
    }

    @Test
    fun `test call ttools`() = runBlocking {
        MCPLocalClient(LOCAL_SERVER_URL).use { client ->
            val result = client.callTool("ttools")

            assertTrue(result.isSuccess, "Вызов инструмента 'ttools' должен пройти успешно")

            val callResult = result.getOrNull()
            assertNotNull(callResult, "Результат вызова не должен быть null")
            assertTrue(callResult.content.isNotEmpty(), "Результат должен содержать контент")

            val textContent = callResult.content.firstOrNull { it.type == "text" }
            assertNotNull(textContent, "Должен быть текстовый контент")
            assertNotNull(textContent.text, "Текст не должен быть null")

            // Проверяем, что результат содержит информацию о инструментах
            assertTrue(
                textContent.text.contains("greet") || textContent.text.contains("add"),
                "Результат должен содержать информацию об инструментах"
            )

            println("✓ Тест вызова инструмента 'ttools' пройден")
            println("  Результат: ${textContent.text.take(100)}...")
        }
    }

    @Test
    fun `test call non-existent tool`() = runBlocking {
        MCPLocalClient(LOCAL_SERVER_URL).use { client ->
            val result = client.callTool("nonexistent")

            assertTrue(result.isFailure, "Вызов несуществующего инструмента должен завершиться с ошибкой")

            val exception = result.exceptionOrNull()
            assertNotNull(exception, "Должно быть исключение")

            println("✓ Тест вызова несуществующего инструмента пройден")
            println("  Ожидаемая ошибка: ${exception.message}")
        }
    }

    @Test
    fun `test multiple sequential calls`() = runBlocking {
        MCPLocalClient(LOCAL_SERVER_URL).use { client ->
            // Первый вызов
            val result1 = client.callTool("greet", mapOf("name" to "Alice"))
            assertTrue(result1.isSuccess, "Первый вызов должен быть успешным")

            // Второй вызов
            val result2 = client.callTool("add", mapOf("a" to 10, "b" to 20))
            assertTrue(result2.isSuccess, "Второй вызов должен быть успешным")

            // Третий вызов
            val result3 = client.callTool("greet", mapOf("name" to "Bob"))
            assertTrue(result3.isSuccess, "Третий вызов должен быть успешным")

            println("✓ Тест множественных последовательных вызовов пройден")
        }
    }

    @Test
    fun `test client reusability`() = runBlocking {
        val client = MCPLocalClient(LOCAL_SERVER_URL)

        try {
            // Инициализация
            val initResult = client.initialize()
            assertTrue(initResult.isSuccess, "Инициализация должна быть успешной")

            // Несколько вызовов
            repeat(3) { i ->
                val result = client.callTool("add", mapOf("a" to i, "b" to i + 1))
                assertTrue(result.isSuccess, "Вызов $i должен быть успешным")
            }

            println("✓ Тест переиспользования клиента пройден")
        } finally {
            client.close()
        }
    }
}
