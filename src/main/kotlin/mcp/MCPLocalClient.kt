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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Клиент для работы с локальным MCP сервером через HTTP транспорт (FastMCP)
 *
 * Этот клиент предназначен для взаимодействия с MCP серверами, которые используют
 * HTTP транспорт вместо SSE или stdio. Он следует спецификации MCP и выполняет
 * последовательность запросов:
 * 1. POST /mcp - initialize (инициализация)
 * 2. POST /mcp - notifications/initialized (уведомление)
 * 3. GET /mcp - чтение сообщений (опционально)
 * 4. POST /mcp - tools/list или tools/call (основные операции)
 * 5. DELETE /mcp - завершение сессии
 *
 * Пример использования:
 * ```kotlin
 * val client = MCPLocalClient("http://127.0.0.1:8000/mcp")
 * client.use { mcp ->
 *     // Инициализация происходит автоматически
 *     val tools = mcp.listTools().getOrThrow()
 *     println("Доступные инструменты: $tools")
 *
 *     // Вызов инструмента
 *     val result = mcp.callTool("greet", mapOf("name" to "World"))
 *     println("Результат: ${result.getOrNull()}")
 * }
 * ```
 */
class MCPLocalClient(
    private val serverUrl: String,
    private val timeout: Long = 10_000 // 10 секунд по умолчанию
) : MCPClientInterface, AutoCloseable {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
                encodeDefaults = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        engine {
            requestTimeout = timeout
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val requestIdCounter = AtomicInteger(1)
    private val isInitialized = AtomicBoolean(false)
    private var sessionId: String? = null

    /**
     * Инициализирует соединение с MCP сервером
     *
     * Отправляет запрос initialize и затем уведомление initialized
     * в соответствии со спецификацией MCP.
     *
     * @return Result с информацией об инициализации или ошибкой
     */
    override suspend fun initialize(): Result<MCPInitializeResult> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized.get()) {
                return@withContext Result.failure(
                    IllegalStateException("Клиент уже инициализирован")
                )
            }

            // Шаг 1: POST /mcp - initialize
            val initRequest = MCPRequest(
                id = requestIdCounter.getAndIncrement(),
                method = "initialize",
                params = JsonObject(mapOf(
                    "protocolVersion" to JsonPrimitive("2024-11-05"),
                    "capabilities" to JsonObject(emptyMap()),
                    "clientInfo" to JsonObject(mapOf(
                        "name" to JsonPrimitive("kotlin-mcp-local-client"),
                        "version" to JsonPrimitive("1.0.0")
                    ))
                ))
            )

            val initResponse = sendRequest(initRequest)

            if (initResponse.error != null) {
                return@withContext Result.failure(
                    Exception("MCP Initialize Error: ${initResponse.error.message}")
                )
            }

            if (initResponse.result == null) {
                return@withContext Result.failure(
                    Exception("Нет результата в initialize ответе")
                )
            }

            val initResult = json.decodeFromJsonElement<MCPInitializeResult>(initResponse.result)

            // Шаг 2: POST /mcp - notifications/initialized (уведомление)
            sendNotification("notifications/initialized")

            isInitialized.set(true)

            Result.success(initResult)
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка инициализации: ${e.message}", e))
        }
    }

    /**
     * Получает список доступных инструментов
     *
     * @return Result со списком инструментов или ошибкой
     */
    override suspend fun listTools(): Result<List<MCPTool>> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()

            // POST /mcp - tools/list
            val request = MCPRequest(
                id = requestIdCounter.getAndIncrement(),
                method = "tools/list",
                params = JsonObject(emptyMap())
            )

            val response = sendRequest(request)

            if (response.error != null) {
                return@withContext Result.failure(
                    Exception("MCP Tools/List Error: ${response.error.message}")
                )
            }

            if (response.result == null) {
                return@withContext Result.failure(
                    Exception("Нет результата в tools/list ответе")
                )
            }

            val toolsList = json.decodeFromJsonElement<MCPToolsList>(response.result)
            Result.success(toolsList.tools)
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка получения списка инструментов: ${e.message}", e))
        }
    }

    /**
     * Вызывает инструмент MCP сервера
     *
     * @param toolName имя инструмента для вызова
     * @param arguments аргументы для передачи инструменту
     * @return Result с результатом вызова или ошибкой
     */
    override suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any>
    ): Result<MCPToolCallResult> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()

            // POST /mcp - tools/call
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
                    Exception("MCP Tools/Call Error: ${response.error.message}")
                )
            }

            if (response.result == null) {
                return@withContext Result.failure(
                    Exception("Нет результата в tools/call ответе")
                )
            }

            val callResult = json.decodeFromJsonElement<MCPToolCallResult>(response.result)
            Result.success(callResult)
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка вызова инструмента '$toolName': ${e.message}", e))
        }
    }

    /**
     * Отправляет запрос к MCP серверу
     *
     * Внутренний метод для отправки JSON-RPC запросов
     */
    private suspend fun sendRequest(request: MCPRequest): MCPResponse {
        return try {
            val requestBody = json.encodeToString(MCPRequest.serializer(), request)
            println("📤 Отправка запроса: $requestBody")

            val response = httpClient.post(serverUrl) {
                contentType(ContentType.Application.Json)
                // FastMCP требует оба типа в Accept
                header("Accept", "application/json, text/event-stream")
                setBody(requestBody)
            }

            println("📥 Получен ответ: ${response.status}")

            if (response.status == HttpStatusCode.OK ||
                response.status == HttpStatusCode.Accepted) {
                val responseText = response.bodyAsText()
                println("📄 Тело ответа: $responseText")
                json.decodeFromString<MCPResponse>(responseText)
            } else {
                val errorBody = try { response.bodyAsText() } catch (e: Exception) { "N/A" }
                println("❌ Ошибка HTTP: ${response.status}, тело: $errorBody")
                MCPResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    error = MCPError(
                        code = response.status.value,
                        message = "HTTP ошибка: ${response.status}"
                    )
                )
            }
        } catch (e: Exception) {
            println("❌ Исключение при отправке запроса: ${e.message}")
            e.printStackTrace()
            MCPResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = MCPError(
                    code = -1,
                    message = "Ошибка подключения: ${e.message}"
                )
            )
        }
    }

    /**
     * Отправляет уведомление (notification) серверу
     *
     * Уведомления - это JSON-RPC сообщения без id, на которые не ожидается ответ
     */
    private suspend fun sendNotification(method: String, params: JsonObject? = null) {
        try {
            val notification = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", method)
                if (params != null) {
                    put("params", params)
                }
            }

            httpClient.post(serverUrl) {
                contentType(ContentType.Application.Json)
                header("Accept", "application/json, text/event-stream")
                setBody(json.encodeToString(JsonObject.serializer(), notification))
            }
        } catch (e: Exception) {
            // Игнорируем ошибки уведомлений - они не критичны
            println("Предупреждение: не удалось отправить уведомление '$method': ${e.message}")
        }
    }

    /**
     * Проверяет, что клиент инициализирован, и инициализирует при необходимости
     */
    private suspend fun ensureInitialized() {
        if (!isInitialized.get()) {
            val result = initialize()
            if (result.isFailure) {
                throw result.exceptionOrNull()
                    ?: IllegalStateException("Не удалось инициализировать клиент")
            }
        }
    }

    /**
     * Завершает сессию с сервером
     *
     * Отправляет DELETE запрос для корректного завершения сессии
     */
    private suspend fun closeSession() {
        if (!isInitialized.get()) {
            return
        }

        try {
            // DELETE /mcp - завершение сессии
            httpClient.delete(serverUrl) {
                contentType(ContentType.Application.Json)
            }
        } catch (e: Exception) {
            println("Предупреждение: ошибка при закрытии сессии: ${e.message}")
        }
    }

    /**
     * Закрывает клиент и освобождает ресурсы
     *
     * Реализует AutoCloseable для использования с use блоком
     */
    override fun close() {
        runBlocking {
            closeSession()
        }
        httpClient.close()
        isInitialized.set(false)
    }
}
