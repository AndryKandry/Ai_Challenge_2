package org.kozyrev.mcp

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("\n=== Тестирование подключения к DeepWiki MCP ===\n")

    val client = MCPDeepWikiClient()

    try {
        println("Шаг 1: Инициализация...")
        val initResult = client.initialize()

        if (initResult.isSuccess) {
            val info = initResult.getOrNull()
            println("✅ УСПЕХ! Подключено к: ${info?.serverInfo?.name} v${info?.serverInfo?.version}")

            println("\nШаг 2: Получение списка инструментов...")
            val toolsResult = client.listTools()

            if (toolsResult.isSuccess) {
                val tools = toolsResult.getOrNull()
                println("✅ УСПЕХ! Найдено инструментов: ${tools?.size}")
                tools?.forEach { tool ->
                    println("  - ${tool.name}: ${tool.description}")
                }
            } else {
                println("❌ Ошибка получения инструментов: ${toolsResult.exceptionOrNull()?.message}")
            }
        } else {
            println("❌ Ошибка инициализации: ${initResult.exceptionOrNull()?.message}")
            initResult.exceptionOrNull()?.printStackTrace()
        }
    } finally {
        client.close()
        println("\n=== Тест завершен ===")
    }
}
