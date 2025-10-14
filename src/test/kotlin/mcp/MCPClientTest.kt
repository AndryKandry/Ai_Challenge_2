package org.kozyrev.mcp

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

class MCPClientTest {

    @Test
    fun testMCPConnection() = runBlocking {
        // Этот тест можно запустить вручную, если MCP сервер доступен
        val serverUrl = "https://mcp.deepwiki.com/sse"

        val client = MCPClient(serverUrl)

        try {
            // Попытка инициализации
            val initResult = client.initialize()

            if (initResult.isSuccess) {
                println("✅ Успешно подключено к MCP серверу")
                println("Информация о сервере: ${initResult.getOrNull()?.serverInfo}")

                // Получение списка инструментов
                val toolsResult = client.listTools()

                if (toolsResult.isSuccess) {
                    val tools = toolsResult.getOrNull() ?: emptyList()
                    println("✅ Получено инструментов: ${tools.size}")

                    tools.forEach { tool ->
                        println("  - ${tool.name}: ${tool.description}")
                    }

                    assertTrue(tools.isNotEmpty(), "Список инструментов не должен быть пустым")
                } else {
                    println("❌ Ошибка получения инструментов: ${toolsResult.exceptionOrNull()?.message}")
                }
            } else {
                println("❌ Ошибка инициализации: ${initResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            println("❌ Исключение: ${e.message}")
            e.printStackTrace()
        } finally {
            client.close()
        }
    }

    @Test
    fun testAlternativeUrl() = runBlocking {
        // Тест альтернативного URL без /sse
        val serverUrl = "https://mcp.deepwiki.com/"

        val client = MCPClient(serverUrl)

        try {
            val initResult = client.initialize()

            if (initResult.isSuccess) {
                println("✅ Альтернативный URL работает")
                println("Информация о сервере: ${initResult.getOrNull()?.serverInfo}")

                val toolsResult = client.listTools()
                if (toolsResult.isSuccess) {
                    val tools = toolsResult.getOrNull() ?: emptyList()
                    println("✅ Получено инструментов: ${tools.size}")
                }
            } else {
                println("❌ Альтернативный URL не работает: ${initResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            println("❌ Исключение при попытке альтернативного URL: ${e.message}")
        } finally {
            client.close()
        }
    }
}
