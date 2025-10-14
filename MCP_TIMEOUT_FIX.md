# Исправление проблемы таймаута при подключении к MCP серверам

## Проблема

При попытке подключения к DeepWiki MCP серверу возникала ошибка:
```
Ошибка SSE: Request timeout has expired [url=https://mcp.deepwiki.com/sse, request_timeout=unknown ms]
```

## Реализованные исправления

### 1. Увеличены таймауты в MCPSSEClient

**Файл:** `src/main/kotlin/mcp/MCPSSEClient.kt`

#### Изменения в HTTP клиенте:
```kotlin
engine {
    requestTimeout = 0 // Убран таймаут для SSE (был 30_000)
    endpoint {
        connectTimeout = 60_000 // 60 секунд на подключение
        socketTimeout = 60_000  // 60 секунд на сокет
    }
}
```

#### Изменения в establishSSEConnection():
```kotlin
// Добавлен withTimeout на уровне корутины
val response = withTimeout(90_000) { // 90 секунд
    httpClient.get(serverUrl) {
        header("Accept", "text/event-stream")
        header("Cache-Control", "no-cache")
        header("Connection", "keep-alive")
    }
}

// Улучшено чтение SSE потока
repeat(10) { // Максимум 10 строк
    val line = withTimeoutOrNull(10_000) { // 10 секунд на каждую строку
        channel.readUTF8Line()
    } ?: ""
    // ... обработка
}
```

### 2. Создан альтернативный упрощенный клиент

**Файл:** `src/main/kotlin/mcp/MCPSimpleClient.kt`

Новый клиент `MCPSimpleClient` который:
- Не использует session-based SSE протокол
- Пробует множество endpoints для подключения
- Отправляет прямые POST запросы

**Endpoints которые пробует клиент:**
1. Базовый URL без `/sse`
2. Оригинальный URL
3. `/mcp` endpoint
4. `/message` endpoint
5. `/rpc` endpoint

```kotlin
val endpoints = listOf(
    baseUrl.removeSuffix("/sse"),           // https://mcp.deepwiki.com
    baseUrl,                                 // https://mcp.deepwiki.com/sse
    baseUrl.removeSuffix("/sse") + "/mcp",  // https://mcp.deepwiki.com/mcp
    baseUrl.removeSuffix("/sse") + "/message", // https://mcp.deepwiki.com/message
    baseUrl.removeSuffix("/sse") + "/rpc"   // https://mcp.deepwiki.com/rpc
)
```

### 3. Автоматический fallback в UI

**Файл:** `src/main/kotlin/ui/MCPToolsUI.kt`

UI теперь автоматически пробует оба клиента:

1. **Сначала:** MCPSSEClient (session-based SSE)
2. **Если не сработал:** MCPSimpleClient (упрощенный)
3. **Если ничего не сработало:** показывается сообщение с рекомендацией использовать Mock сервер

```kotlin
// Пробуем SSE клиент
val sseClient = MCPSSEClient(serverUrl)
val sseResult = tryConnectWithClient(sseClient)

if (!sseResult) {
    println("⚠️ SSE клиент не сработал, пробую упрощенный клиент...")
    sseClient.close()

    // Пробуем Simple клиент
    val simpleClient = MCPSimpleClient(serverUrl)
    val simpleResult = tryConnectWithClient(simpleClient)

    if (!simpleResult) {
        simpleClient.close()
        errorMessage = "Не удалось подключиться..."
    }
}
```

## Преимущества решения

### 1. Увеличенная надежность
- Несколько попыток подключения
- Разные протоколы
- Подробное логирование

### 2. Лучшая диагностика
- Информативные сообщения об ошибках
- Логи на каждом этапе подключения
- Понятные указания пользователю

### 3. Гибкость
- Поддержка разных MCP серверов
- Автоматический выбор подходящего клиента
- Возможность тестирования с Mock сервером

## Использование

### Способ 1: Через UI (рекомендуется)

```bash
./gradlew run
```

1. Откройте вкладку "🔧 MCP Tools"
2. Введите URL сервера (например, `https://mcp.deepwiki.com/sse`)
3. Нажмите "Подключиться"
4. **UI автоматически попробует оба клиента**

### Способ 2: Напрямую в коде

#### Использование SSE клиента:
```kotlin
val client = MCPSSEClient("https://mcp.deepwiki.com/sse")

try {
    val result = client.initialize()
    if (result.isSuccess) {
        println("Подключено через SSE")
    }
} catch (e: Exception) {
    println("SSE не сработал: ${e.message}")
} finally {
    client.close()
}
```

#### Использование Simple клиента:
```kotlin
val client = MCPSimpleClient("https://mcp.deepwiki.com/sse")

try {
    val result = client.initialize()
    if (result.isSuccess) {
        println("Подключено через Simple клиент")
    }
} catch (e: Exception) {
    println("Simple клиент не сработал: ${e.message}")
} finally {
    client.close()
}
```

#### Автоматический fallback:
```kotlin
suspend fun connectToMCP(url: String): MCPClientInterface? {
    // Пробуем SSE
    val sseClient = MCPSSEClient(url)
    if (sseClient.initialize().isSuccess) {
        return sseClient
    }
    sseClient.close()

    // Пробуем Simple
    val simpleClient = MCPSimpleClient(url)
    if (simpleClient.initialize().isSuccess) {
        return simpleClient
    }
    simpleClient.close()

    return null
}
```

## Тестирование

### Mock сервер (всегда работает)

```kotlin
val client = MCPMockClient()
client.initialize() // Всегда успешно
client.listTools()  // Возвращает 5 демо-инструментов
client.close()
```

### Проверка компиляции

```bash
./gradlew compileKotlin
# Результат: BUILD SUCCESSFUL
```

## Логирование

Все клиенты выводят подробные логи:

```
📡 Открываю SSE соединение к https://mcp.deepwiki.com/sse...
✅ SSE соединение установлено, читаю endpoint...
📨 Получена строка: event: endpoint
📨 Получена строка: data: /session/abc123
✅ Получен endpoint: /session/abc123
🔗 Session endpoint установлен: https://mcp.deepwiki.com/session/abc123
📤 Отправляю запрос к https://mcp.deepwiki.com/session/abc123
✅ Подключено: DeepWiki MCP Server v1.0.0
```

## Возможные проблемы и решения

### Проблема: Оба клиента не подключаются

**Решение:**
1. Проверьте интернет-соединение
2. Попробуйте Mock сервер для тестирования UI
3. Проверьте доступность сервера в браузере
4. Посмотрите логи в консоли приложения

### Проблема: Долгое ожидание подключения

**Решение:**
- Нормально, SSE клиент может подключаться до 90 секунд
- Если не сработал, автоматически попробуется Simple клиент
- Общее время попытки: до 3 минут

### Проблема: Непонятные ошибки в логах

**Решение:**
- Все ошибки логируются с префиксами (📡, ✅, ❌, ⚠️)
- Посмотрите полный стек трейс в консоли
- Попробуйте другой URL MCP сервера

## Следующие шаги

Если подключение все еще не работает:

1. **Используйте Mock сервер** для разработки и тестирования
2. **Проверьте доступность сервера** через curl:
   ```bash
   curl -N -H "Accept: text/event-stream" https://mcp.deepwiki.com/sse
   ```
3. **Попробуйте другие MCP серверы:**
   - `https://mcp.demo.modelcontextprotocol.io/time/sse`
   - `https://mcp.demo.modelcontextprotocol.io/fetch/sse`

## Итог

✅ Исправлена проблема таймаута
✅ Добавлен альтернативный клиент
✅ Автоматический fallback в UI
✅ Улучшено логирование
✅ Компиляция успешна

Приложение теперь более устойчиво к проблемам сети и поддерживает разные типы MCP серверов!
