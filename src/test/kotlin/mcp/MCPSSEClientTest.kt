package org.kozyrev.mcp

import kotlinx.coroutines.runBlocking
import org.junit.Test

class MCPSSEClientTest {

    @Test
    fun testDeepWikiConnection() = runBlocking {
        val serverUrl = "https://mcp.deepwiki.com/sse"
        val client = MCPSSEClient(serverUrl)

        try {
            println("=== Тест подключения к DeepWiki MCP Server ===")

            // Инициализация
            val initResult = client.initialize()

            if (initResult.isSuccess) {
                val info = initResult.getOrNull()
                println("✅ Успешная инициализация!")
                println("   Сервер: ${info?.serverInfo?.name}")
                println("   Версия: ${info?.serverInfo?.version}")
                println("   Протокол: ${info?.protocolVersion}")

                // Получение инструментов
                val toolsResult = client.listTools()

                if (toolsResult.isSuccess) {
                    val tools = toolsResult.getOrNull() ?: emptyList()
                    println("✅ Получено инструментов: ${tools.size}")
                    println()

                    tools.forEach { tool ->
                        println("📦 ${tool.name}")
                        if (tool.description != null) {
                            println("   Описание: ${tool.description}")
                        }
                        if (tool.inputSchema != null) {
                            println("   Schema: ${tool.inputSchema}")
                        }
                        println()
                    }
                } else {
                    println("❌ Ошибка получения инструментов:")
                    println("   ${toolsResult.exceptionOrNull()?.message}")
                }
            } else {
                println("❌ Ошибка инициализации:")
                println("   ${initResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            println("❌ Исключение:")
            println("   ${e.message}")
            e.printStackTrace()
        } finally {
            client.close()
        }
    }
}
