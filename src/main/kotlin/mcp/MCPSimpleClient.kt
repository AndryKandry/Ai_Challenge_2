package org.kozyrev.mcp

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Упрощенный MCP клиент без session-based SSE протокола
 * Пытается подключиться напрямую через POST запросы
 */
class MCPSimpleClient(private val baseUrl: String) : MCPClientInterface {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        engine {
            requestTimeout = 60_000 // 60 секунд
            endpoint {
                connectTimeout = 30_000
                socketTimeout = 60_000
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val requestIdCounter = AtomicInteger(1)
    private var isInitialized = false

    /**
     * Пробует разные endpoints для отправки запроса
     */
    private suspend fun sendRequest(request: MCPRequest): Result<MCPResponse> = withContext(Dispatchers.IO) {
        val requestBody = json.encodeToString(MCPRequest.serializer(), request)
        println("📤 Отправляю запрос: ${request.method}")

        // Пробуем разные endpoints
        val endpoints = mutableListOf<String>()

        // Если URL заканчивается на /mcp - это remote.mcpservers.org формат
        if (baseUrl.endsWith("/mcp")) {
            endpoints.add(baseUrl)  // Прямой URL
            endpoints.add(baseUrl.removeSuffix("/mcp"))  // Без /mcp
        }
        // Если URL заканчивается на /sse - это обычный SSE формат
        else if (baseUrl.endsWith("/sse")) {
            endpoints.add(baseUrl.removeSuffix("/sse"))  // Без /sse
            endpoints.add(baseUrl)  // С /sse
            endpoints.add(baseUrl.removeSuffix("/sse") + "/mcp")
            endpoints.add(baseUrl.removeSuffix("/sse") + "/message")
            endpoints.add(baseUrl.removeSuffix("/sse") + "/rpc")
        }
        // Для остальных случаев
        else {
            endpoints.add(baseUrl)
            endpoints.add("$baseUrl/mcp")
            endpoints.add("$baseUrl/sse")
            endpoints.add("$baseUrl/message")
            endpoints.add("$baseUrl/rpc")
        }

        for (endpoint in endpoints) {
            try {
                println("🔍 Пробую endpoint: $endpoint")

                val response = httpClient.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

                if (response.status.value == 200) {
                    val responseText = response.bodyAsText()
                    println("✅ Успешный ответ от: $endpoint")
                    println("📥 Ответ: ${responseText.take(200)}...")

                    val mcpResponse = json.decodeFromString<MCPResponse>(responseText)
                    return@withContext Result.success(mcpResponse)
                } else {
                    println("⚠️ Статус ${response.status.value} от: $endpoint")
                }
            } catch (e: Exception) {
                println("❌ Ошибка на $endpoint: ${e.message}")
            }
        }

        Result.failure(Exception("Не удалось подключиться ни к одному endpoint"))
    }

    override suspend fun initialize(): Result<MCPInitializeResult> {
        try {
            println("🔧 Инициализация упрощенного клиента...")

            val request = MCPRequest(
                id = requestIdCounter.getAndIncrement(),
                method = "initialize",
                params = JsonObject(mapOf(
                    "protocolVersion" to JsonPrimitive("2024-11-05"),
                    "capabilities" to JsonObject(emptyMap()),
                    "clientInfo" to JsonObject(mapOf(
                        "name" to JsonPrimitive("kotlin-mcp-simple-client"),
                        "version" to JsonPrimitive("1.0.0")
                    ))
                ))
            )

            val responseResult = sendRequest(request)
            if (responseResult.isFailure) {
                return Result.failure(responseResult.exceptionOrNull()!!)
            }

            val response = responseResult.getOrNull()!!

            if (response.error != null) {
                return Result.failure(
                    Exception("MCP Error: ${response.error.message}")
                )
            }

            if (response.result == null) {
                return Result.failure(
                    Exception("No result in initialize response")
                )
            }

            val initResult = json.decodeFromJsonElement<MCPInitializeResult>(response.result)
            isInitialized = true

            println("✅ Инициализация успешна: ${initResult.serverInfo.name}")

            return Result.success(initResult)
        } catch (e: Exception) {
            println("❌ Ошибка инициализации: ${e.message}")
            return Result.failure(e)
        }
    }

    override suspend fun listTools(): Result<List<MCPTool>> {
        try {
            if (!isInitialized) {
                val initResult = initialize()
                if (initResult.isFailure) {
                    return Result.failure(initResult.exceptionOrNull()!!)
                }
            }

            println("📋 Запрашиваю список инструментов...")

            val request = MCPRequest(
                id = requestIdCounter.getAndIncrement(),
                method = "tools/list",
                params = JsonObject(emptyMap())
            )

            val responseResult = sendRequest(request)
            if (responseResult.isFailure) {
                return Result.failure(responseResult.exceptionOrNull()!!)
            }

            val response = responseResult.getOrNull()!!

            if (response.error != null) {
                return Result.failure(
                    Exception("MCP Error: ${response.error.message}")
                )
            }

            if (response.result == null) {
                return Result.failure(
                    Exception("No result in tools/list response")
                )
            }

            val toolsList = json.decodeFromJsonElement<MCPToolsList>(response.result)
            println("✅ Получено инструментов: ${toolsList.tools.size}")

            return Result.success(toolsList.tools)
        } catch (e: Exception) {
            println("❌ Ошибка получения инструментов: ${e.message}")
            return Result.failure(e)
        }
    }

    override suspend fun callTool(toolName: String, arguments: Map<String, Any>): Result<MCPToolCallResult> {
        try {
            if (!isInitialized) {
                val initResult = initialize()
                if (initResult.isFailure) {
                    return Result.failure(initResult.exceptionOrNull()!!)
                }
            }

            println("🔧 Вызываю инструмент: $toolName")

            val request = MCPRequest(
                id = requestIdCounter.getAndIncrement(),
                method = "tools/call",
                params = JsonObject(mapOf(
                    "name" to JsonPrimitive(toolName),
                    "arguments" to json.encodeToJsonElement(arguments)
                ))
            )

            val responseResult = sendRequest(request)
            if (responseResult.isFailure) {
                return Result.failure(responseResult.exceptionOrNull()!!)
            }

            val response = responseResult.getOrNull()!!

            if (response.error != null) {
                return Result.failure(
                    Exception("MCP Error: ${response.error.message}")
                )
            }

            if (response.result == null) {
                return Result.failure(
                    Exception("No result in tools/call response")
                )
            }

            val callResult = json.decodeFromJsonElement<MCPToolCallResult>(response.result)
            println("✅ Инструмент выполнен успешно")

            return Result.success(callResult)
        } catch (e: Exception) {
            println("❌ Ошибка вызова инструмента: ${e.message}")
            return Result.failure(e)
        }
    }

    override fun close() {
        httpClient.close()
        println("🔒 Клиент закрыт")
    }
}
