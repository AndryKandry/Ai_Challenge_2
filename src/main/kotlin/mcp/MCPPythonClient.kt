package org.kozyrev.mcp

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * MCP клиент, который использует Python FastMCP библиотеку для подключения
 *
 * Этот клиент запускает Python скрипт для взаимодействия с MCP сервером,
 * гарантируя 100% совместимость с FastMCP серверами.
 *
 * Требования:
 * - Python 3.7+
 * - fastmcp библиотека установлена: pip install fastmcp
 * - mcp_python_client.py в корне проекта
 */
class MCPPythonClient(
    private val serverUrl: String
) : MCPClientInterface, AutoCloseable {

    private val pythonScript = File("mcp_python_client.py").absolutePath
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Проверяет наличие Python и необходимых зависимостей
     */
    private fun checkPythonAvailability(): Result<String> {
        return try {
            // Пробуем разные команды Python
            val pythonCommands = listOf("python3", "python")

            for (cmd in pythonCommands) {
                try {
                    val process = ProcessBuilder(cmd, "--version")
                        .redirectErrorStream(true)
                        .start()

                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        return Result.success(cmd)
                    }
                } catch (e: Exception) {
                    // Пробуем следующую команду
                }
            }

            Result.failure(Exception("Python не найден. Установите Python 3.7+"))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка проверки Python: ${e.message}"))
        }
    }

    /**
     * Выполняет Python скрипт с заданными аргументами
     */
    private suspend fun executePythonScript(vararg args: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            // Проверяем наличие Python
            val pythonCmd = checkPythonAvailability().getOrElse {
                return@withContext Result.failure(it)
            }

            // Проверяем наличие скрипта
            if (!File(pythonScript).exists()) {
                return@withContext Result.failure(
                    Exception("Python скрипт не найден: $pythonScript")
                )
            }

            println("🐍 Запуск Python клиента: $pythonCmd $pythonScript ${args.joinToString(" ")}")

            // Запускаем Python скрипт
            val processBuilder = ProcessBuilder(pythonCmd, pythonScript, *args)
                .redirectErrorStream(false)

            val process = processBuilder.start()

            // Читаем stdout
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }

            // Читаем stderr
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                reader.readText()
            }

            val exitCode = process.waitFor()

            println("📊 Python вывод: $output")
            if (error.isNotBlank()) {
                println("⚠️ Python stderr: $error")

                // Проверяем специфичные ошибки
                if (error.contains("No module named 'fastmcp'")) {
                    println("💡 Подсказка: Установите fastmcp: pip install fastmcp")
                    println("💡 Или позвольте скрипту установить его автоматически")
                }
            }

            if (exitCode != 0) {
                val errorMsg = if (error.contains("No module named 'fastmcp'")) {
                    "fastmcp не установлен. Установите командой: pip install fastmcp\n\nПолная ошибка: $error"
                } else {
                    "Python скрипт завершился с ошибкой (код $exitCode): $error"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            // Парсим JSON результат
            try {
                val result = json.decodeFromString<JsonObject>(output.trim())
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(
                    Exception("Не удалось распарсить ответ Python: $output. Ошибка: ${e.message}")
                )
            }

        } catch (e: Exception) {
            Result.failure(Exception("Ошибка выполнения Python скрипта: ${e.message}", e))
        }
    }

    override suspend fun initialize(): Result<MCPInitializeResult> = withContext(Dispatchers.IO) {
        try {
            val result = executePythonScript("initialize", serverUrl).getOrElse {
                return@withContext Result.failure(it)
            }

            val success = result["success"]?.jsonPrimitive?.boolean ?: false
            if (!success) {
                val error = result["error"]?.jsonPrimitive?.content ?: "Неизвестная ошибка"
                return@withContext Result.failure(Exception("Python initialize error: $error"))
            }

            // Создаем MCPInitializeResult из ответа
            val initResult = MCPInitializeResult(
                protocolVersion = result["protocolVersion"]?.jsonPrimitive?.content ?: "2024-11-05",
                serverInfo = MCPServerInfo(
                    name = result["serverInfo"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "MCP Server",
                    version = result["serverInfo"]?.jsonObject?.get("version")?.jsonPrimitive?.content ?: "1.0.0"
                ),
                capabilities = JsonObject(emptyMap())
            )

            Result.success(initResult)
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка инициализации через Python: ${e.message}", e))
        }
    }

    override suspend fun listTools(): Result<List<MCPTool>> = withContext(Dispatchers.IO) {
        try {
            // Для получения списка инструментов вызываем ttools
            val ttoolsResult = callTool("ttools", emptyMap()).getOrElse {
                // Если ttools не доступен, возвращаем пустой список
                return@withContext Result.success(emptyList())
            }

            // Парсим результат ttools
            val ttoolsText = ttoolsResult.content.firstOrNull()?.text ?: "[]"

            try {
                val toolsArray = json.decodeFromString<JsonArray>(ttoolsText)
                val tools = toolsArray.mapNotNull { element ->
                    try {
                        val obj = element.jsonObject
                        val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val signature = obj["signature"]?.jsonPrimitive?.content ?: ""
                        val doc = obj["doc"]?.jsonPrimitive?.content ?: ""

                        MCPTool(
                            name = name,
                            description = "$signature${if (doc.isNotEmpty()) " - $doc" else ""}",
                            inputSchema = null
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                Result.success(tools)
            } catch (e: Exception) {
                println("⚠️ Не удалось распарсить ttools: ${e.message}")
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка получения списка инструментов: ${e.message}", e))
        }
    }

    override suspend fun callTool(toolName: String, arguments: Map<String, Any>): Result<MCPToolCallResult> = withContext(Dispatchers.IO) {
        try {
            // Простое преобразование Map в JSON строку
            val argsJson = buildJsonObject {
                arguments.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, value)
                        is Number -> put(key, value)
                        is Boolean -> put(key, value)
                        else -> put(key, value.toString())
                    }
                }
            }.toString()

            val result = executePythonScript("call_tool", serverUrl, toolName, argsJson).getOrElse {
                return@withContext Result.failure(it)
            }

            val success = result["success"]?.jsonPrimitive?.boolean ?: false
            if (!success) {
                val error = result["error"]?.jsonPrimitive?.content ?: "Неизвестная ошибка"
                return@withContext Result.failure(Exception("Python call_tool error: $error"))
            }

            // Преобразуем результат в MCPToolCallResult
            val content = result["content"]?.jsonArray?.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    MCPContent(
                        type = obj["type"]?.jsonPrimitive?.content ?: "text",
                        text = obj["text"]?.jsonPrimitive?.content,
                        data = obj["data"]?.jsonPrimitive?.content,
                        mimeType = obj["mimeType"]?.jsonPrimitive?.content
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()

            Result.success(MCPToolCallResult(content = content, isError = false))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка вызова инструмента '$toolName': ${e.message}", e))
        }
    }

    override fun close() {
        // Python клиент не требует закрытия
    }
}
