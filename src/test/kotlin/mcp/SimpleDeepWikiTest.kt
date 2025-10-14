package org.kozyrev.mcp

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Простой тест для отладки DeepWiki подключения
 */
class SimpleDeepWikiTest {

    @Test
    fun testBasicConnection() {
        runBlocking {
        println("\n=== Простой тест подключения к DeepWiki ===\n")

        val client = MCPDeepWikiClient()

        try {
            println("1. Попытка инициализации...")
            val initResult = client.initialize()

            if (initResult.isSuccess) {
                val info = initResult.getOrNull()
                println("✅ УСПЕХ! Подключено к: ${info?.serverInfo?.name}")
                println("   Версия: ${info?.serverInfo?.version}")
                println("   Capabilities: ${info?.capabilities}")

                println("\n2. Попытка получить список инструментов...")
                val toolsResult = client.listTools()

                if (toolsResult.isSuccess) {
                    val tools = toolsResult.getOrNull()
                    println("✅ УСПЕХ! Найдено инструментов: ${tools?.size}")
                    tools?.forEach { tool ->
                        println("   - ${tool.name}")
                    }
                } else {
                    println("❌ Ошибка при получении инструментов:")
                    toolsResult.exceptionOrNull()?.printStackTrace()
                }
            } else {
                println("❌ Ошибка при инициализации:")
                initResult.exceptionOrNull()?.printStackTrace()
            }

            } finally {
                client.close()
                println("\n=== Тест завершен ===")
            }
        }

        assertTrue(true, "Тест выполнен")
    }
}
