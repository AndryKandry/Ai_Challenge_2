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
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Упрощенный клиент специально для DeepWiki MCP
 * Использует упрощенный подход к SSE подключению
 */
/**
 * Клиент для DeepWiki MCP через HTTP (не SSE)
 * Использует endpoint /mcp вместо /sse для упрощения
 */
class MCPDeepWikiClient(
    private val baseUrl: String = "http://127.0.0.1:8000",//"https://mcp.deepwiki.com",
    private val useSSE: Boolean = false  // По умолчанию используем /mcp
) : MCPClientInterface {

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
            endpoint {
                connectTimeout = 30_000
                socketTimeout = 30_000
                requestTimeout = 0 // Без таймаута для длинных запросов
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
    private var sseChannel: ByteReadChannel? = null
    private val responseCallbacks = mutableMapOf<Int, CompletableDeferred<MCPResponse>>()

    /**
     * Получаем session endpoint через SSE
     * Использует streaming для чтения SSE потока
     */
    private suspend fun getSessionEndpoint(): Result<String> = withContext(Dispatchers.IO) {
        try {
            println("📡 Запрос MCP endpoint: $baseUrl/mcp")

            // Создаём отдельную корутину для streaming запроса
            val endpointDeferred = async {
                httpClient.prepareGet("$baseUrl/mcp") {
                    header("Accept", "text/event-stream")
                    header("Cache-Control", "no-cache")
                }.execute { response ->
                    if (response.status.value != 200) {
                        throw Exception("MCP вернул статус ${response.status.value}")
                    }
//
//                    println("✅ SSE соединение установлено (статус ${response.status.value})")
//
//                    val channel = response.bodyAsChannel()
//                    val buffer = StringBuilder()
//                    var currentEvent = ""
                    var foundEndpoint: String? = null
//                    val bytes = ByteArray(1)
//
//                    // Читаем поток побайтно до получения endpoint
//                    while (!channel.isClosedForRead && foundEndpoint == null) {
//                        val read = channel.readAvailable(bytes, 0, 1)
//                        if (read <= 0) continue
//
//                        val char = bytes[0].toInt().toChar()
//
//                        if (char == '\n') {
//                            val line = buffer.toString().trim()
//                            buffer.clear()
//
//                            println("📨 SSE: $line")
//
//                            when {
//                                line.startsWith("event:") -> {
//                                    currentEvent = line.substring(6).trim()
//                                    println("  → Событие: $currentEvent")
//                                }
//                                line.startsWith("data:") && currentEvent == "endpoint" -> {
//                                    foundEndpoint = line.substring(5).trim()
//                                    println("  → Найден endpoint: $foundEndpoint")
//                                    break
//                                }
//                            }
//                        } else if (char != '\r') {
//                            buffer.append(char)
//                        }
//                    }
//
                    foundEndpoint ?: throw Exception("Endpoint не найден в SSE потоке")
                }
            }

            // Ждём endpoint с таймаутом
            val endpoint = withTimeout(15_000) {
                endpointDeferred.await()
            }

            // Формируем полный URL
            val fullEndpoint = when {
                endpoint.startsWith("http://") || endpoint.startsWith("https://") -> endpoint
                endpoint.startsWith("/") -> "$baseUrl$endpoint"
                else -> "$baseUrl/$endpoint"
            }

            println("🔗 Session endpoint: $fullEndpoint")
            return@withContext Result.success(fullEndpoint)

        } catch (e: TimeoutCancellationException) {
            println("❌ Таймаут при получении endpoint (15 секунд)")
            return@withContext Result.failure(Exception("Таймаут при получении SSE endpoint"))
        } catch (e: Exception) {
            println("❌ Ошибка получения endpoint: ${e.message}")
            e.printStackTrace()
            return@withContext Result.failure(e)
        }
    }

    /**
     * Отправляет JSON-RPC запрос к session endpoint
     */
    private suspend fun sendRequest(request: MCPRequest): Result<MCPResponse> = withContext(Dispatchers.IO) {
        try {
            if (sessionEndpoint == null) {
                return@withContext Result.failure(
                    Exception("Session endpoint не установлен. Сначала вызовите initialize()")
                )
            }

            val requestBody = json.encodeToString(MCPRequest.serializer(), request)
            println("📤 Отправка запроса к $sessionEndpoint")
            println("   Метод: ${request.method}")
            println("   Тело запроса: $requestBody")

            val response = httpClient.post(sessionEndpoint!!) {
                contentType(ContentType.Application.Json)
                header("Accept", "application/json, text/event-stream")
                setBody(requestBody)
            }

            val responseText = response.bodyAsText()

            // Проверяем статус
            if (response.status.value == 202) {
                // 202 Accepted означает, что ответ будет через SSE
                // Для упрощения возвращаем ошибку и рекомендуем использовать HTTP endpoint
                println("⚠️ Получен статус 202 Accepted - ответ через SSE не поддерживается")
                println("   Используйте HTTP endpoint (/mcp) вместо SSE")
                return@withContext Result.failure(
                    Exception("Статус 202: Ответ через SSE не поддерживается. Используйте useSSE=false")
                )
            }

            if (response.status.value != 200) {
                println("❌ Ошибка от сервера (статус ${response.status.value}):")
                println("   $responseText")
                return@withContext Result.failure(
                    Exception("Запрос вернул статус ${response.status.value}: $responseText")
                )
            }

            println("📥 Получен ответ (статус ${response.status.value}, ${responseText.length} символов)")
            println("   Ответ: ${responseText.take(250)}")

            val mcpResponse = json.decodeFromString<MCPResponse>(responseText)
            Result.success(mcpResponse)

        } catch (e: Exception) {
            println("❌ Ошибка отправки запроса: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun initialize(): Result<MCPInitializeResult> {
        try {
            // Выбираем endpoint в зависимости от режима
            if (useSSE) {
                // Шаг 1: Получаем session endpoint через SSE
                val endpointResult = getSessionEndpoint()
                if (endpointResult.isFailure) {
                    return Result.failure(endpointResult.exceptionOrNull()!!)
                }
                sessionEndpoint = endpointResult.getOrNull()!!
                println("✅ Session endpoint получен через SSE")
            } else {
                // Используем прямой HTTP endpoint
                sessionEndpoint = "$baseUrl/mcp"
                println("✅ Используем HTTP endpoint: $sessionEndpoint")
            }

            // Шаг 2: Отправляем initialize
            val request = MCPRequest(
                id = requestIdCounter.getAndIncrement(),
                method = "initialize",
                params = JsonObject(mapOf(
                    "protocolVersion" to JsonPrimitive("2024-11-05"),
                    "capabilities" to buildJsonObject {
                        // Указываем минимальные capabilities
                    },
                    "clientInfo" to buildJsonObject {
                        put("name", "kotlin-deepwiki-client")
                        put("version", "1.0.0")
                    }
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

            // Отправляем notifications/initialized
            sendNotification("notifications/initialized")

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
            println("✅ Инструмент $toolName выполнен успешно")
            return Result.success(callResult)

        } catch (e: Exception) {
            println("❌ Ошибка вызова инструмента: ${e.message}")
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

            println("📨 Отправлено уведомление: $method")
        } catch (e: Exception) {
            println("⚠️ Ошибка отправки уведомления: ${e.message}")
        }
    }

    override fun close() {
        httpClient.close()
        println("🔒 DeepWiki клиент закрыт")
    }
}
