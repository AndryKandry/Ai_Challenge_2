package org.kozyrev.mcp

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

/**
 * Mock клиент для демонстрации работы MCP без реального сервера
 */
class MCPMockClient : MCPClientInterface {

    override suspend fun initialize(): Result<MCPInitializeResult> {
        delay(500) // Имитация задержки сети

        val serverInfo = MCPServerInfo(
            name = "Mock MCP Server",
            version = "1.0.0-demo"
        )

        val initResult = MCPInitializeResult(
            protocolVersion = "2024-11-05",
            serverInfo = serverInfo,
            capabilities = buildJsonObject {
                put("tools", true)
            }
        )

        return Result.success(initResult)
    }

    override suspend fun callTool(toolName: String, arguments: Map<String, Any>): Result<MCPToolCallResult> {
        delay(500) // Имитация задержки сети

        val result = MCPToolCallResult(
            content = listOf(
                MCPContent(
                    type = "text",
                    text = "Mock результат для инструмента: $toolName с параметрами: $arguments"
                )
            )
        )

        return Result.success(result)
    }

    override suspend fun listTools(): Result<List<MCPTool>> {
        delay(300) // Имитация задержки сети

        val tools = listOf(
            MCPTool(
                name = "search_web",
                description = "Поиск информации в интернете. Принимает поисковый запрос и возвращает релевантные результаты.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("query", buildJsonObject {
                            put("type", "string")
                            put("description", "Поисковый запрос")
                        })
                        put("max_results", buildJsonObject {
                            put("type", "integer")
                            put("description", "Максимальное количество результатов")
                            put("default", 10)
                        })
                    })
                    put("required", buildJsonArray { add("query") })
                }
            ),
            MCPTool(
                name = "read_file",
                description = "Чтение содержимого файла из файловой системы. Поддерживает текстовые и бинарные файлы.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Путь к файлу")
                        })
                        put("encoding", buildJsonObject {
                            put("type", "string")
                            put("description", "Кодировка файла")
                            put("default", "utf-8")
                        })
                    })
                    put("required", buildJsonArray { add("path") })
                }
            ),
            MCPTool(
                name = "execute_code",
                description = "Выполнение кода Python в изолированной среде. Возвращает stdout и stderr.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "Python код для выполнения")
                        })
                        put("timeout", buildJsonObject {
                            put("type", "integer")
                            put("description", "Таймаут выполнения в секундах")
                            put("default", 30)
                        })
                    })
                    put("required", buildJsonArray { add("code") })
                }
            ),
            MCPTool(
                name = "get_weather",
                description = "Получение текущей погоды для указанного города",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("city", buildJsonObject {
                            put("type", "string")
                            put("description", "Название города")
                        })
                        put("units", buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray {
                                add("celsius")
                                add("fahrenheit")
                            })
                            put("default", "celsius")
                        })
                    })
                    put("required", buildJsonArray { add("city") })
                }
            ),
            MCPTool(
                name = "translate_text",
                description = "Перевод текста с одного языка на другой",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "Текст для перевода")
                        })
                        put("source_lang", buildJsonObject {
                            put("type", "string")
                            put("description", "Исходный язык (auto для автоопределения)")
                            put("default", "auto")
                        })
                        put("target_lang", buildJsonObject {
                            put("type", "string")
                            put("description", "Целевой язык")
                        })
                    })
                    put("required", buildJsonArray {
                        add("text")
                        add("target_lang")
                    })
                }
            )
        )

        return Result.success(tools)
    }

    override fun close() {
        // Mock клиент не требует закрытия
    }
}

/**
 * Интерфейс для MCP клиентов
 */
interface MCPClientInterface {
    suspend fun initialize(): Result<MCPInitializeResult>
    suspend fun listTools(): Result<List<MCPTool>>
    suspend fun callTool(toolName: String, arguments: Map<String, Any>): Result<MCPToolCallResult>
    fun close()
}

/**
 * Расширение MCPClient для реализации интерфейса
 */
fun MCPClient.asInterface(): MCPClientInterface = object : MCPClientInterface {
    override suspend fun initialize() = this@asInterface.initialize()
    override suspend fun listTools() = this@asInterface.listTools()
    override suspend fun callTool(toolName: String, arguments: Map<String, Any>) =
        this@asInterface.callTool(toolName, arguments)
    override fun close() = this@asInterface.close()
}
