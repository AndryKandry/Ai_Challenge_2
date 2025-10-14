package org.kozyrev.mcp

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Правильный клиент для MCP через SSE с поддержкой session-based протокола
 */
class MCPSSEClient(private val serverUrl: String) : MCPClientInterface {
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
            requestTimeout = 0 // Убираем таймаут для SSE
            endpoint {
                connectTimeout = 60_000 // 60 секунд на подключение
                socketTimeout = 60_000  // 60 секунд на сокет
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val requestIdCounter = AtomicInteger(1)
    private var sessionEndpoint: String? = null
    private var isInitialized = false

    /**
     * Шаг 1: Открыть SSE соединение и получить session endpoint
     */
    private suspend fun establishSSEConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            println("📡 Открываю SSE соединение к $serverUrl...")

            // Создаем GET запрос к SSE endpoint
            val response = httpClient.get(serverUrl) {
                header("Accept", "text/event-stream")
                header("Cache-Control", "no-cache")
                header("Connection", "keep-alive")
                timeout {
                    requestTimeoutMillis = 30_000 // 30 секунд на весь запрос
                }
            }

            if (response.status.value != 200) {
                val errorText = try { response.bodyAsText() } catch (e: Exception) { e.message }
                return@withContext Result.failure(
                    Exception("SSE соединение вернуло статус ${response.status.value}: $errorText")
                )
            }

            println("✅ SSE соединение установлено (статус 200), читаю endpoint...")

            // Читаем SSE поток для получения endpoint
            val channel = response.bodyAsChannel()
            var currentEvent = ""
            var endpoint = ""
            var lineCount = 0
            val maxLines = 50 // Увеличиваем лимит строк

            while (lineCount < maxLines && !channel.isClosedForRead) {
                try {
                    val line = withTimeoutOrNull(5_000) { // 5 секунд на строку
                        channel.readUTF8Line()
                    }

                    if (line == null) {
                        println("⚠️ Достигнут конец потока или таймаут")
                        break
                    }

                    lineCount++
                    println("📨 [$lineCount] $line")

                    when {
                        line.startsWith("event:") -> {
                            currentEvent = line.substring(6).trim()
                            println("  → Событие: $currentEvent")
                        }
                        line.startsWith("data:") -> {
                            val data = line.substring(5).trim()
                            println("  → Данные: $data")

                            // Если это событие endpoint, сохраняем данные
                            if (currentEvent == "endpoint" && data.isNotEmpty()) {
                                endpoint = data
                                println("✅ Получен endpoint из события: $endpoint")
                            }
                        }
                        line.isEmpty() -> {
                            // Пустая строка означает конец SSE сообщения
                            if (endpoint.isNotEmpty()) {
                                println("✅ Обнаружена пустая строка, endpoint готов: $endpoint")
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("⚠️ Ошибка при чтении строки: ${e.message}")
                    break
                }
            }

            // Проверяем, получили ли мы endpoint
            if (endpoint.isEmpty()) {
                return@withContext Result.failure(
                    Exception("Не удалось получить endpoint из SSE потока после $lineCount строк")
                )
            }

            // Формируем полный URL endpoint
            val fullEndpoint = if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
                endpoint
            } else {
                // Endpoint относительный, добавляем базовый URL
                val baseUrl = serverUrl.substringBeforeLast("/")
                if (endpoint.startsWith("/")) {
                    "$baseUrl$endpoint"
                } else {
                    "$baseUrl/$endpoint"
                }
            }

            println("🔗 Полный session endpoint: $fullEndpoint")
            return@withContext Result.success(fullEndpoint)

        } catch (e: Exception) {
            println("❌ Ошибка SSE подключения: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Шаг 2: Отправить JSON-RPC запрос к session endpoint
     */
    private suspend fun sendToSessionEndpoint(
        endpoint: String,
        request: MCPRequest
    ): Result<MCPResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = json.encodeToString(MCPRequest.serializer(), request)
            println("📤 Отправляю запрос к $endpoint")
            println("📄 Тело: $requestBody")

            val response = httpClient.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            val responseText = response.bodyAsText()
            println("📥 Получен ответ: ${responseText.take(200)}...")

            if (response.status.value != 200) {
                return@withContext Result.failure(
                    Exception("Запрос вернул статус ${response.status.value}: $responseText")
                )
            }

            val mcpResponse = json.decodeFromString<MCPResponse>(responseText)
            Result.success(mcpResponse)
        } catch (e: Exception) {
            println("❌ Ошибка запроса: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun initialize(): Result<MCPInitializeResult> {
        try {
            // Шаг 1: Получаем session endpoint
            val endpointResult = establishSSEConnection()
            if (endpointResult.isFailure) {
                return Result.failure(endpointResult.exceptionOrNull()!!)
            }

            sessionEndpoint = endpointResult.getOrNull()!!
            println("🔗 Session endpoint установлен: $sessionEndpoint")

            // Шаг 2: Отправляем initialize запрос
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

            val responseResult = sendToSessionEndpoint(sessionEndpoint!!, request)
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

            // Отправляем уведомление
            sendNotification("notifications/initialized")

            return Result.success(initResult)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun listTools(): Result<List<MCPTool>> {
        try {
            if (!isInitialized || sessionEndpoint == null) {
                val initResult = initialize()
                if (initResult.isFailure) {
                    return Result.failure(initResult.exceptionOrNull()!!)
                }
            }

            val request = MCPRequest(
                id = requestIdCounter.getAndIncrement(),
                method = "tools/list",
                params = JsonObject(emptyMap())
            )

            val responseResult = sendToSessionEndpoint(sessionEndpoint!!, request)
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
            return Result.success(toolsList.tools)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun callTool(toolName: String, arguments: Map<String, Any>): Result<MCPToolCallResult> {
        try {
            if (!isInitialized || sessionEndpoint == null) {
                val initResult = initialize()
                if (initResult.isFailure) {
                    return Result.failure(initResult.exceptionOrNull()!!)
                }
            }

            val request = MCPRequest(
                id = requestIdCounter.getAndIncrement(),
                method = "tools/call",
                params = JsonObject(mapOf(
                    "name" to JsonPrimitive(toolName),
                    "arguments" to json.encodeToJsonElement(arguments)
                ))
            )

            val responseResult = sendToSessionEndpoint(sessionEndpoint!!, request)
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
            return Result.success(callResult)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private suspend fun sendNotification(method: String) {
        try {
            if (sessionEndpoint == null) return

            val notification = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", method)
            }

            httpClient.post(sessionEndpoint!!) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), notification))
            }
        } catch (e: Exception) {
            println("Notification error: ${e.message}")
        }
    }

    override fun close() {
        httpClient.close()
    }
}
