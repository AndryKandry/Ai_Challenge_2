package org.kozyrev.mcp

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Клиент для работы с MCP (Model Context Protocol) серверами через SSE
 */
class MCPClient(private val serverUrl: String) {
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
            requestTimeout = 30_000 // 30 секунд для SSE
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val requestIdCounter = AtomicInteger(1)
    private var isInitialized = false
    private val pendingResponses = mutableMapOf<Int, CompletableDeferred<MCPResponse>>()

    /**
     * Инициализирует соединение с MCP сервером
     */
    suspend fun initialize(): Result<MCPInitializeResult> = withContext(Dispatchers.IO) {
        try {
            val request = MCPRequest(
                id = requestIdCounter.getAndIncrement(),
                method = "initialize",
                params = JsonObject(mapOf(
                    "protocolVersion" to JsonPrimitive("2024-11-05"),
                    "capabilities" to JsonObject(emptyMap()),
                    "clientInfo" to JsonObject(mapOf(
                        "name" to JsonPrimitive("kotlin-mcp-client"),
                        "version" to JsonPrimitive("1.0.0")
                    ))
                ))
            )

            val response = sendRequest(request)

            if (response.error != null) {
                return@withContext Result.failure(
                    Exception("MCP Error: ${response.error.message}")
                )
            }

            if (response.result == null) {
                return@withContext Result.failure(
                    Exception("No result in initialize response")
                )
            }

            val initResult = json.decodeFromJsonElement<MCPInitializeResult>(response.result)
            isInitialized = true

            // Отправляем уведомление об инициализации
            sendNotification("notifications/initialized")

            Result.success(initResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получает список доступных инструментов
     */
    suspend fun listTools(): Result<List<MCPTool>> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                initialize().getOrThrow()
            }

            val request = MCPRequest(
                id = requestIdCounter.getAndIncrement(),
                method = "tools/list",
                params = JsonObject(emptyMap())
            )

            val response = sendRequest(request)

            if (response.error != null) {
                return@withContext Result.failure(
                    Exception("MCP Error: ${response.error.message}")
                )
            }

            if (response.result == null) {
                return@withContext Result.failure(
                    Exception("No result in tools/list response")
                )
            }

            val toolsList = json.decodeFromJsonElement<MCPToolsList>(response.result)
            Result.success(toolsList.tools)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Отправляет запрос к MCP серверу через SSE
     */
    private suspend fun sendRequest(request: MCPRequest): MCPResponse = withContext(Dispatchers.IO) {
        val requestBody = json.encodeToString(MCPRequest.serializer(), request)

        try {
            // Пробуем разные методы отправки

            // Метод 1: POST с JSON-RPC телом
            try {
                val response = httpClient.post(serverUrl) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Text.EventStream)
                    setBody(requestBody)
                }

                if (response.status.value == 200) {
                    return@withContext parseSSEResponse(response)
                }
            } catch (e: Exception) {
                println("POST метод не сработал: ${e.message}")
            }

            // Метод 2: GET с query параметром
            try {
                val encodedRequest = java.net.URLEncoder.encode(requestBody, "UTF-8")
                val response = httpClient.get("$serverUrl?message=$encodedRequest") {
                    accept(ContentType.Text.EventStream)
                }

                if (response.status.value == 200) {
                    return@withContext parseSSEResponse(response)
                }
            } catch (e: Exception) {
                println("GET с query не сработал: ${e.message}")
            }

            // Метод 3: Обычный POST к /message endpoint
            try {
                val messageUrl = serverUrl.removeSuffix("/sse") + "/message"
                val response = httpClient.post(messageUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

                if (response.status.value == 200) {
                    val responseText = response.bodyAsText()
                    return@withContext json.decodeFromString<MCPResponse>(responseText)
                }
            } catch (e: Exception) {
                println("/message endpoint не сработал: ${e.message}")
            }

            throw Exception("Не удалось подключиться к MCP серверу. Попробуйте другой URL.")
        } catch (e: Exception) {
            throw Exception("Ошибка подключения: ${e.message}")
        }
    }

    /**
     * Парсит SSE ответ
     */
    private suspend fun parseSSEResponse(response: HttpResponse): MCPResponse {
        val responseText = response.bodyAsText()

        // Обработка SSE ответа
        val lines = responseText.lines().filter { it.isNotBlank() }
        var dataLine = ""

        for (line in lines) {
            when {
                line.startsWith("data: ") -> {
                    dataLine = line.removePrefix("data: ").trim()
                    // Если нашли data строку с JSON, пробуем распарсить
                    if (dataLine.startsWith("{")) {
                        try {
                            return json.decodeFromString<MCPResponse>(dataLine)
                        } catch (e: Exception) {
                            // Продолжаем искать
                        }
                    }
                }
                line.startsWith("event: ") -> {
                    // Игнорируем event строки
                }
                line.startsWith("{") -> {
                    // Прямой JSON без SSE префикса
                    try {
                        return json.decodeFromString<MCPResponse>(line)
                    } catch (e: Exception) {
                        // Игнорируем
                    }
                }
            }
        }

        if (dataLine.isNotBlank() && dataLine.startsWith("{")) {
            return json.decodeFromString<MCPResponse>(dataLine)
        }

        // Если нет SSE формата, пробуем обработать как обычный JSON
        if (responseText.trim().startsWith("{")) {
            return json.decodeFromString<MCPResponse>(responseText)
        }

        throw Exception("Не удалось распарсить ответ от сервера")
    }

    /**
     * Отправляет уведомление (notification) серверу
     */
    private suspend fun sendNotification(method: String) {
        try {
            val notification = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", method)
            }

            httpClient.post(serverUrl) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), notification))
            }
        } catch (e: Exception) {
            // Игнорируем ошибки уведомлений
            println("Notification error: ${e.message}")
        }
    }

    /**
     * Вызывает инструмент MCP сервера
     */
    suspend fun callTool(toolName: String, arguments: Map<String, Any>): Result<MCPToolCallResult> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                initialize().getOrThrow()
            }

            val request = MCPRequest(
                id = requestIdCounter.getAndIncrement(),
                method = "tools/call",
                params = JsonObject(mapOf(
                    "name" to JsonPrimitive(toolName),
                    "arguments" to json.encodeToJsonElement(arguments)
                ))
            )

            val response = sendRequest(request)

            if (response.error != null) {
                return@withContext Result.failure(
                    Exception("MCP Error: ${response.error.message}")
                )
            }

            if (response.result == null) {
                return@withContext Result.failure(
                    Exception("No result in tools/call response")
                )
            }

            val callResult = json.decodeFromJsonElement<MCPToolCallResult>(response.result)
            Result.success(callResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Закрывает клиент и освобождает ресурсы
     */
    fun close() {
        httpClient.close()
    }
}
