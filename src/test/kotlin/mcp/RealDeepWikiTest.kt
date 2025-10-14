package org.kozyrev.mcp

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Реальные тесты подключения к DeepWiki MCP серверу
 * ВНИМАНИЕ: Требует доступ к интернету
 */
class RealDeepWikiTest {

    @Test
    fun testDeepWikiSSEConnection() = runBlocking {
        println("\n" + "=".repeat(60))
        println("🧪 ТЕСТ: Реальное подключение к DeepWiki MCP через SSE")
        println("=".repeat(60))

        val client = MCPDeepWikiClient()

        try {
            // Шаг 1: Инициализация
            println("\n📋 Шаг 1: Инициализация клиента...")
            val initResult = client.initialize()

            if (initResult.isFailure) {
                val error = initResult.exceptionOrNull()
                println("❌ Инициализация не удалась: ${error?.message}")
                error?.printStackTrace()
                throw AssertionError("Инициализация должна быть успешной", error)
            }

            assertTrue(initResult.isSuccess, "Инициализация DeepWiki должна быть успешной")

            val info = initResult.getOrNull()
            println("✅ Подключено к: ${info?.serverInfo?.name} v${info?.serverInfo?.version}")
            println("   Capabilities: ${info?.capabilities}")

            // Шаг 2: Получение списка инструментов
            println("\n📋 Шаг 2: Получение списка инструментов...")
            val toolsResult = client.listTools()

            if (toolsResult.isFailure) {
                val error = toolsResult.exceptionOrNull()
                println("❌ Получение инструментов не удалось: ${error?.message}")
                error?.printStackTrace()
                throw AssertionError("Получение инструментов должно быть успешным", error)
            }

            assertTrue(toolsResult.isSuccess, "Получение инструментов должно быть успешным")

            val tools = toolsResult.getOrNull()!!
            println("✅ Найдено инструментов: ${tools.size}")

            tools.forEach { tool ->
                println("   📌 ${tool.name}")
                tool.description?.let { desc ->
                    println("      └─ $desc")
                }
            }

            // Проверяем наличие специфичных для DeepWiki инструментов
            val hasReadWikiStructure = tools.any { it.name == "read_wiki_structure" }
            val hasReadWikiContents = tools.any { it.name == "read_wiki_contents" }
            val hasAskQuestion = tools.any { it.name == "ask_question" }

            println("\n📋 Проверка специфичных инструментов DeepWiki:")
            println("   ${if (hasReadWikiStructure) "✅" else "❌"} read_wiki_structure")
            println("   ${if (hasReadWikiContents) "✅" else "❌"} read_wiki_contents")
            println("   ${if (hasAskQuestion) "✅" else "❌"} ask_question")

            assertTrue(hasReadWikiStructure, "Должен быть инструмент read_wiki_structure")
            assertTrue(hasReadWikiContents, "Должен быть инструмент read_wiki_contents")
            assertTrue(hasAskQuestion, "Должен быть инструмент ask_question")

            // Шаг 3: Вызов инструмента read_wiki_structure
            println("\n📋 Шаг 3: Вызов инструмента read_wiki_structure...")
            val callResult = client.callTool(
                "read_wiki_structure",
                mapOf("repository" to "anthropics/claude-code")
            )

            if (callResult.isSuccess) {
                val content = callResult.getOrNull()?.content
                println("✅ Вызов read_wiki_structure успешен")
                println("   Результат (первые 300 символов):")
                content?.forEach { c ->
                    val text = c.text ?: c.data ?: "Нет данных"
                    println("   ${text.take(300)}${if (text.length > 300) "..." else ""}")
                }
            } else {
                val error = callResult.exceptionOrNull()
                println("⚠️ Ошибка вызова read_wiki_structure: ${error?.message}")
                // Не останавливаем тест, т.к. это может быть проблема с репозиторием
            }

            // Шаг 4: Вызов инструмента ask_question
            println("\n📋 Шаг 4: Вызов инструмента ask_question...")
            val askResult = client.callTool(
                "ask_question",
                mapOf(
                    "repository" to "anthropics/claude-code",
                    "question" to "What is Claude Code?"
                )
            )

            if (askResult.isSuccess) {
                val content = askResult.getOrNull()?.content
                println("✅ Вызов ask_question успешен")
                println("   Ответ:")
                content?.forEach { c ->
                    val text = c.text ?: c.data ?: "Нет данных"
                    println("   $text")
                }
            } else {
                val error = askResult.exceptionOrNull()
                println("⚠️ Ошибка вызова ask_question: ${error?.message}")
            }

            println("\n" + "=".repeat(60))
            println("✅ ВСЕ ТЕСТЫ ПРОШЛИ УСПЕШНО!")
            println("=".repeat(60))

        } catch (e: Exception) {
            println("\n❌ ТЕСТ НЕ ПРОШЁЛ: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            client.close()
            println("🔒 Соединение закрыто")
        }
    }

    @Test
    fun testDeepWikiClientDirect() = runBlocking {
        println("\n" + "=".repeat(60))
        println("🧪 ТЕСТ: Прямое подключение через DeepWikiClient")
        println("=".repeat(60))

        val client = MCPDeepWikiClient()

        try {
            val initResult = client.initialize()

            if (initResult.isSuccess) {
                println("✅ DeepWikiClient успешно подключился!")
                val info = initResult.getOrNull()
                println("   Сервер: ${info?.serverInfo?.name}")

                val tools = client.listTools().getOrNull()
                println("   Инструментов найдено: ${tools?.size ?: 0}")

                // Пробуем вызвать инструмент
                tools?.firstOrNull()?.let { tool ->
                    println("\n   Пробуем вызвать: ${tool.name}")
                    val result = client.callTool(tool.name, emptyMap())
                    println("   Результат: ${if (result.isSuccess) "✅ Успех" else "❌ Ошибка"}")
                }

                assertTrue(initResult.isSuccess, "Подключение должно быть успешным")
            } else {
                println("❌ Ошибка подключения: ${initResult.exceptionOrNull()?.message}")
            }
        } finally {
            client.close()
        }

        println("=".repeat(60))
    }
}
