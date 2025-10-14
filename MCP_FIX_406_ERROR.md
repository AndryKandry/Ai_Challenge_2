# Исправление ошибки 406 Not Acceptable

## Проблема

При попытке подключения к локальному FastMCP серверу возникала ошибка:

```
🔄 Локальный сервер обнаружен, используем MCPLocalClient...
REQUEST: http://127.0.0.1:8000/mcp
METHOD: POST
RESPONSE: 406 Not Acceptable
```

## Причина

HTTP код 406 Not Acceptable означает, что сервер не может вернуть ответ в формате, который указан в заголовке `Accept` клиента.

FastMCP сервер требует специфичный заголовок `Accept`, который включает оба типа:
- `application/json` - для JSON-RPC ответов
- `text/event-stream` - для SSE (Server-Sent Events)

## Решение

Добавлен корректный заголовок `Accept` во все HTTP запросы к серверу.

### Изменения в MCPLocalClient.kt

#### 1. Метод sendRequest()

**Было:**
```kotlin
val response = httpClient.post(serverUrl) {
    contentType(ContentType.Application.Json)
    accept(ContentType.Application.Json)  // ❌ Только JSON
    setBody(requestBody)
}
```

**Стало:**
```kotlin
val response = httpClient.post(serverUrl) {
    contentType(ContentType.Application.Json)
    // FastMCP требует оба типа в Accept
    header("Accept", "application/json, text/event-stream")  // ✅ JSON + SSE
    setBody(requestBody)
}
```

#### 2. Метод sendNotification()

**Было:**
```kotlin
httpClient.post(serverUrl) {
    contentType(ContentType.Application.Json)
    setBody(json.encodeToString(JsonObject.serializer(), notification))
}
```

**Стало:**
```kotlin
httpClient.post(serverUrl) {
    contentType(ContentType.Application.Json)
    header("Accept", "application/json, text/event-stream")  // ✅ Добавлен Accept
    setBody(json.encodeToString(JsonObject.serializer(), notification))
}
```

## Дополнительные улучшения

Добавлено подробное логирование для отладки:

```kotlin
private suspend fun sendRequest(request: MCPRequest): MCPResponse {
    return try {
        val requestBody = json.encodeToString(MCPRequest.serializer(), request)
        println("📤 Отправка запроса: $requestBody")

        val response = httpClient.post(serverUrl) {
            contentType(ContentType.Application.Json)
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
            // ... обработка ошибки
        }
    } catch (e: Exception) {
        println("❌ Исключение при отправке запроса: ${e.message}")
        e.printStackTrace()
        // ... обработка исключения
    }
}
```

## Корректные заголовки для FastMCP

Теперь клиент отправляет следующие заголовки:

```
Content-Type: application/json
Accept: application/json, text/event-stream
```

Это соответствует требованиям FastMCP сервера.

## Эквивалент curl

Теперь наш Kotlin клиент отправляет запросы, эквивалентные:

```bash
curl -X POST http://127.0.0.1:8000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {
        "name": "kotlin-mcp-local-client",
        "version": "1.0.0"
      }
    }
  }'
```

## Тестирование

После исправления вы должны увидеть:

### Успешная инициализация
```
🔄 Локальный сервер обнаружен, используем MCPLocalClient...
📤 Отправка запроса: {"jsonrpc":"2.0","id":1,"method":"initialize",...}
📥 Получен ответ: 200 OK
📄 Тело ответа: {"jsonrpc":"2.0","id":1,"result":{...}}
```

### Успешный вызов ttools
```
📤 Отправка запроса: {"jsonrpc":"2.0","id":3,"method":"tools/call",...}
📥 Получен ответ: 200 OK
📄 Тело ответа: {"jsonrpc":"2.0","id":3,"result":{"content":[...]}}
✓ ttools вызван успешно
```

## Как проверить исправление

### 1. Запустите Python MCP сервер
```bash
python your_mcp_server.py
```

### 2. Запустите Kotlin приложение
```bash
./gradlew run
```

### 3. Подключитесь к серверу
1. Откройте вкладку "MCP Tools"
2. Введите URL: `http://127.0.0.1:8000/mcp`
3. Нажмите "Подключиться"

### 4. Проверьте логи

**Консоль Kotlin приложения:**
```
🔄 Локальный сервер обнаружен, используем MCPLocalClient...
📤 Отправка запроса: {"jsonrpc":"2.0","id":1,...}
📥 Получен ответ: 200 OK
📄 Тело ответа: {"jsonrpc":"2.0",...}
✓ ttools вызван успешно
```

**Консоль Python сервера:**
```
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 202 Accepted
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
```

## Troubleshooting

### Всё ещё получаете 406?

1. **Проверьте версию FastMCP:**
   ```bash
   pip show fastmcp
   ```

2. **Убедитесь, что сервер запущен правильно:**
   ```python
   if __name__ == "__main__":
       mcp.run(transport="http", port=8000)
   ```

3. **Проверьте, что порт не занят:**
   ```bash
   lsof -i :8000
   ```

4. **Попробуйте curl:**
   ```bash
   curl -X POST http://127.0.0.1:8000/mcp \
     -H "Content-Type: application/json" \
     -H "Accept: application/json, text/event-stream" \
     -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
   ```

### Получаете другие ошибки?

- **Connection refused** - сервер не запущен или недоступен
- **Timeout** - увеличьте таймаут в `MCPLocalClient`
- **JSON parse error** - проверьте формат ответа сервера

## Заключение

Ошибка 406 была вызвана отсутствием корректного заголовка `Accept`. FastMCP требует:
```
Accept: application/json, text/event-stream
```

После добавления этого заголовка клиент корректно работает с локальным MCP сервером.

## Файлы, затронутые изменениями

- `src/main/kotlin/mcp/MCPLocalClient.kt` - добавлены корректные заголовки

## Компиляция

```bash
./gradlew compileKotlin
# BUILD SUCCESSFUL
```

Всё готово к использованию! ✅
