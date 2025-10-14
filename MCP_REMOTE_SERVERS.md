# Подключение к Remote MCP Servers

## Обзор

Добавлена поддержка подключения к MCP серверам на `remote.mcpservers.org`, которые используют другой формат URL (заканчиваются на `/mcp` вместо `/sse`).

## Новые поддерживаемые серверы

### 1. Remote Fetch Server
```
URL: https://remote.mcpservers.org/fetch/mcp
```

**Инструменты:**
- Загрузка веб-страниц
- Получение HTML контента
- Обработка URL

**Пример использования:**
```kotlin
val client = MCPSimpleClient("https://remote.mcpservers.org/fetch/mcp")
client.initialize()

val result = client.callTool(
    "fetch",
    mapOf("url" to "https://example.com")
)
```

### 2. Remote Time Server
```
URL: https://remote.mcpservers.org/time/mcp
```

**Инструменты:**
- Получение текущего времени
- Работа с часовыми поясами
- Форматирование даты/времени

### 3. Remote Weather Server
```
URL: https://remote.mcpservers.org/weather/mcp
```

**Инструменты:**
- Получение прогноза погоды
- Текущие погодные условия
- Погода по геолокации

## Отличия от SSE серверов

### SSE формат (DeepWiki, Demo серверы)
```
URL: https://mcp.deepwiki.com/sse
URL: https://mcp.demo.modelcontextprotocol.io/time/sse
```

**Особенности:**
- Используют Server-Sent Events (SSE)
- Session-based протокол
- Требуют MCPSSEClient

### MCP формат (Remote серверы)
```
URL: https://remote.mcpservers.org/fetch/mcp
URL: https://remote.mcpservers.org/time/mcp
```

**Особенности:**
- Прямые POST запросы
- Без session
- Работают с MCPSimpleClient

## Автоматическое определение формата

`MCPSimpleClient` теперь автоматически определяет формат URL и пробует правильные endpoints:

### Для `/mcp` URL:
```kotlin
if (baseUrl.endsWith("/mcp")) {
    // Пробует:
    // 1. https://remote.mcpservers.org/fetch/mcp
    // 2. https://remote.mcpservers.org/fetch
}
```

### Для `/sse` URL:
```kotlin
if (baseUrl.endsWith("/sse")) {
    // Пробует:
    // 1. https://mcp.deepwiki.com (без /sse)
    // 2. https://mcp.deepwiki.com/sse
    // 3. https://mcp.deepwiki.com/mcp
    // 4. https://mcp.deepwiki.com/message
    // 5. https://mcp.deepwiki.com/rpc
}
```

### Для других URL:
```kotlin
else {
    // Пробует все возможные варианты
    // базовый URL, /mcp, /sse, /message, /rpc
}
```

## Использование через UI

### Шаг 1: Откройте приложение
```bash
./gradlew run
```

### Шаг 2: Перейдите на вкладку MCP Tools

### Шаг 3: Введите URL Remote сервера

Например:
```
https://remote.mcpservers.org/fetch/mcp
```

### Шаг 4: Нажмите "Подключиться"

UI автоматически:
1. Попробует SSE клиент
2. Если не работает → попробует Simple клиент
3. Simple клиент автоматически определит правильный endpoint

### Шаг 5: Используйте инструменты

После подключения вы увидите список доступных инструментов сервера.

## Использование в коде

### Вариант 1: Явное указание Simple клиента

```kotlin
val client = MCPSimpleClient("https://remote.mcpservers.org/fetch/mcp")

try {
    // Инициализация
    val initResult = client.initialize()
    if (initResult.isSuccess) {
        println("Подключено к Remote Fetch Server")
    }

    // Получение инструментов
    val tools = client.listTools().getOrNull()
    tools?.forEach { tool ->
        println("Инструмент: ${tool.name}")
    }

    // Вызов инструмента
    val result = client.callTool(
        "fetch",
        mapOf("url" to "https://example.com")
    )

    println(result.getOrNull()?.content?.first()?.text)
} finally {
    client.close()
}
```

### Вариант 2: Автоматический выбор через UI

UI автоматически выберет правильный клиент - вам нужно только ввести URL!

### Вариант 3: Универсальная функция

```kotlin
suspend fun connectToAnyMCP(url: String): MCPClientInterface? {
    // Пробуем SSE клиент
    if (url.contains("/sse")) {
        val sseClient = MCPSSEClient(url)
        if (sseClient.initialize().isSuccess) {
            return sseClient
        }
        sseClient.close()
    }

    // Пробуем Simple клиент (работает для /mcp и других)
    val simpleClient = MCPSimpleClient(url)
    if (simpleClient.initialize().isSuccess) {
        return simpleClient
    }
    simpleClient.close()

    return null
}

// Использование
val client = connectToAnyMCP("https://remote.mcpservers.org/fetch/mcp")
if (client != null) {
    // Работаем с клиентом
    client.close()
}
```

## Список всех поддерживаемых серверов

### 📚 DeepWiki (документация)
```
https://mcp.deepwiki.com/sse
```
- Клиент: MCPSSEClient (затем fallback на MCPSimpleClient)
- Инструменты: read_wiki_structure, read_wiki_contents, ask_question

### 🌐 Remote MCP Servers
```
https://remote.mcpservers.org/fetch/mcp
https://remote.mcpservers.org/time/mcp
https://remote.mcpservers.org/weather/mcp
```
- Клиент: MCPSimpleClient
- Инструменты: зависят от сервера

### ⏰ Demo серверы
```
https://mcp.demo.modelcontextprotocol.io/time/sse
https://mcp.demo.modelcontextprotocol.io/fetch/sse
```
- Клиент: MCPSSEClient (затем fallback на MCPSimpleClient)
- Инструменты: зависят от сервера

## Примеры реальных запросов

### Fetch Server - загрузка веб-страницы

```kotlin
val client = MCPSimpleClient("https://remote.mcpservers.org/fetch/mcp")
client.initialize()

val result = client.callTool(
    "fetch",
    mapOf("url" to "https://anthropic.com")
)

val html = result.getOrNull()?.content?.first()?.text
println("HTML: ${html?.take(500)}")

client.close()
```

### Time Server - текущее время

```kotlin
val client = MCPSimpleClient("https://remote.mcpservers.org/time/mcp")
client.initialize()

val result = client.callTool(
    "get_current_time",
    mapOf("timezone" to "America/New_York")
)

val time = result.getOrNull()?.content?.first()?.text
println("Время в NY: $time")

client.close()
```

### Weather Server - погода

```kotlin
val client = MCPSimpleClient("https://remote.mcpservers.org/weather/mcp")
client.initialize()

val result = client.callTool(
    "get_weather",
    mapOf(
        "city" to "London",
        "units" to "metric"
    )
)

val weather = result.getOrNull()?.content?.first()?.text
println("Погода в Лондоне: $weather")

client.close()
```

## Отладка подключения

### Просмотр логов

Все клиенты выводят подробные логи:

```
🔍 Пробую endpoint: https://remote.mcpservers.org/fetch/mcp
✅ Успешный ответ от: https://remote.mcpservers.org/fetch/mcp
📥 Ответ: {"jsonrpc":"2.0","id":1,"result":{...
✅ Инициализация успешна: Remote Fetch Server
📋 Запрашиваю список инструментов...
✅ Получено инструментов: 3
```

### Если подключение не работает

1. **Проверьте URL** - он должен точно совпадать
2. **Посмотрите логи** - они покажут какие endpoints пробовались
3. **Попробуйте Mock сервер** - для тестирования UI
4. **Проверьте доступность** сервера:
   ```bash
   curl -X POST https://remote.mcpservers.org/fetch/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","method":"initialize","id":1,"params":{}}'
   ```

## Преимущества новой реализации

### ✅ Универсальность
- Работает с разными форматами URL
- Автоматическое определение типа сервера
- Fallback между клиентами

### ✅ Надежность
- Пробует множество endpoints
- Подробное логирование
- Обработка ошибок

### ✅ Простота использования
- Один интерфейс для всех серверов
- UI автоматически выбирает клиент
- Примеры в подсказках

## Итог

Теперь приложение поддерживает:
- ✅ SSE серверы (DeepWiki, Demo) - через MCPSSEClient
- ✅ MCP серверы (remote.mcpservers.org) - через MCPSimpleClient
- ✅ Автоматический выбор правильного клиента в UI
- ✅ Более 6 различных MCP серверов из коробки

Просто введите URL любого MCP сервера и нажмите "Подключиться"! 🚀
