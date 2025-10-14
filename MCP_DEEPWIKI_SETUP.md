# Подключение к DeepWiki MCP и другим MCP серверам

## Обзор

В приложение добавлена полная поддержка Model Context Protocol (MCP) с возможностью подключения к различным MCP серверам через SSE (Server-Sent Events), включая DeepWiki, Time и Fetch серверы.

## Что реализовано

### 1. Модели данных для вызова инструментов

Добавлены новые модели в `src/main/kotlin/mcp/MCPModels.kt`:

```kotlin
@Serializable
data class MCPToolCallResult(
    val content: List<MCPContent>,
    val isError: Boolean? = null
)

@Serializable
data class MCPContent(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)
```

### 2. Расширенный интерфейс MCPClientInterface

Добавлен метод для вызова инструментов:

```kotlin
interface MCPClientInterface {
    suspend fun initialize(): Result<MCPInitializeResult>
    suspend fun listTools(): Result<List<MCPTool>>
    suspend fun callTool(toolName: String, arguments: Map<String, Any>): Result<MCPToolCallResult>
    fun close()
}
```

### 3. Обновленные клиенты

Все три клиента (`MCPClient`, `MCPSSEClient`, `MCPMockClient`) теперь поддерживают:
- Инициализацию соединения
- Получение списка инструментов
- **Вызов инструментов с параметрами**
- Корректное закрытие соединения

### 4. Улучшенный UI

Новый интерфейс `MCPToolsUI.kt` включает:
- Подключение к различным MCP серверам
- Отображение списка инструментов
- **Интерактивный вызов инструментов с формами ввода параметров**
- Отображение результатов вызова
- Кнопка отключения от сервера
- Примеры доступных серверов

## Доступные MCP серверы

### 1. DeepWiki (Документация репозиториев)

**URL:** `https://mcp.deepwiki.com/sse`

**Инструменты:**
- `read_wiki_structure` - Получить структуру документации репозитория
- `read_wiki_contents` - Прочитать содержимое документации
- `ask_question` - AI-запросы к документации репозитория

**Пример использования:**
```kotlin
val client = MCPSSEClient("https://mcp.deepwiki.com/sse")
client.initialize()
val tools = client.listTools()

// Вызов инструмента
val result = client.callTool(
    "read_wiki_structure",
    mapOf("repository" to "anthropics/claude-code")
)
```

### 2. Time Server (Текущее время)

**URL:** `https://mcp.demo.modelcontextprotocol.io/time/sse`

**Инструменты:**
- Получение текущего времени в различных форматах

### 3. Fetch Server (Получение веб-страниц)

**URL:** `https://mcp.demo.modelcontextprotocol.io/fetch/sse`

**Инструменты:**
- Загрузка и обработка веб-страниц

## Использование через UI

### Шаг 1: Запуск приложения

```bash
./gradlew run
```

### Шаг 2: Открыть вкладку MCP Tools

Перейдите на вкладку "🔧 MCP Tools" в главном окне приложения.

### Шаг 3: Подключение к серверу

1. Введите URL сервера (например, `https://mcp.deepwiki.com/sse`)
2. Или выберите "Использовать демо-сервер (Mock)" для тестирования
3. Нажмите кнопку "Подключиться"

### Шаг 4: Вызов инструментов

1. После успешного подключения отобразится список инструментов
2. Нажмите кнопку "Вызвать" на нужном инструменте
3. Заполните необходимые параметры в форме
4. Нажмите "Выполнить"
5. Результат отобразится в верхней части экрана

### Шаг 5: Отключение

Нажмите кнопку "Отключиться" для закрытия соединения с сервером.

## Использование в коде

### Простое подключение к DeepWiki

```kotlin
suspend fun connectToDeepWiki() {
    val client = MCPSSEClient("https://mcp.deepwiki.com/sse")

    // Инициализация
    val initResult = client.initialize()
    if (initResult.isSuccess) {
        println("Подключено: ${initResult.getOrNull()?.serverInfo?.name}")
    }

    // Получение инструментов
    val toolsResult = client.listTools()
    if (toolsResult.isSuccess) {
        val tools = toolsResult.getOrNull() ?: emptyList()
        tools.forEach { tool ->
            println("Инструмент: ${tool.name} - ${tool.description}")
        }
    }

    // Вызов инструмента
    val callResult = client.callTool(
        "read_wiki_structure",
        mapOf("repository" to "anthropics/claude-code")
    )

    if (callResult.isSuccess) {
        val content = callResult.getOrNull()?.content
        content?.forEach { c ->
            println(c.text ?: c.data)
        }
    }

    // Закрытие
    client.close()
}
```

### Использование Mock клиента для тестирования

```kotlin
suspend fun testWithMock() {
    val client = MCPMockClient()

    client.initialize()
    val tools = client.listTools().getOrNull() ?: emptyList()

    // Вызов любого инструмента
    val result = client.callTool(
        "search_web",
        mapOf("query" to "Kotlin MCP")
    )

    println(result.getOrNull()?.content?.first()?.text)

    client.close()
}
```

### Использование через интерфейс

```kotlin
suspend fun useWithInterface(useRealServer: Boolean) {
    val client: MCPClientInterface = if (useRealServer) {
        MCPSSEClient("https://mcp.deepwiki.com/sse")
    } else {
        MCPMockClient()
    }

    client.initialize()
    val tools = client.listTools().getOrNull() ?: emptyList()

    // Работаем одинаково независимо от типа клиента
    tools.forEach { tool ->
        println("Инструмент: ${tool.name}")
    }

    client.close()
}
```

## Архитектура SSE клиента

`MCPSSEClient` работает в два этапа:

### Этап 1: Установка SSE соединения

```
Клиент -> GET /sse -> Сервер
         <- event: endpoint
         <- data: /session/abc123
```

Сервер возвращает уникальный session endpoint для дальнейших запросов.

### Этап 2: JSON-RPC запросы к session endpoint

```
Клиент -> POST /session/abc123
          {
            "jsonrpc": "2.0",
            "method": "initialize",
            "id": 1,
            "params": {...}
          }
       <- {
            "jsonrpc": "2.0",
            "result": {...},
            "id": 1
          }
```

## Обработка ошибок

Все методы клиентов возвращают `Result<T>`:

```kotlin
val result = client.callTool("tool_name", args)

if (result.isSuccess) {
    val data = result.getOrNull()
    // Обработка успешного результата
} else {
    val error = result.exceptionOrNull()
    println("Ошибка: ${error?.message}")
}
```

## Особенности реализации

### 1. Session-based протокол

`MCPSSEClient` правильно реализует session-based протокол MCP:
- Открывает SSE соединение
- Получает session endpoint
- Использует его для всех последующих запросов

### 2. Автоматическая инициализация

Клиенты автоматически инициализируются при первом запросе:

```kotlin
val client = MCPSSEClient(url)
// initialize() вызывается автоматически при listTools() или callTool()
val tools = client.listTools()
```

### 3. Корректное управление ресурсами

Всегда закрывайте клиент после использования:

```kotlin
try {
    // Работа с клиентом
} finally {
    client.close()
}
```

### 4. JSON-RPC 2.0

Полная поддержка спецификации JSON-RPC 2.0:
- Уникальные ID для каждого запроса
- Обработка `result` и `error`
- Поддержка notifications

## Тестирование

### Тест с Mock сервером

```kotlin
@Test
fun testMockServer() = runBlocking {
    val client = MCPMockClient()

    val initResult = client.initialize()
    assertTrue(initResult.isSuccess)

    val toolsResult = client.listTools()
    assertTrue(toolsResult.isSuccess)
    assertTrue(toolsResult.getOrNull()!!.isNotEmpty())

    val callResult = client.callTool("search_web", mapOf("query" to "test"))
    assertTrue(callResult.isSuccess)

    client.close()
}
```

### Интеграционный тест с DeepWiki

```kotlin
@Test
fun testDeepWikiConnection() = runBlocking {
    val client = MCPSSEClient("https://mcp.deepwiki.com/sse")

    try {
        val initResult = client.initialize()
        assertTrue(initResult.isSuccess)

        val toolsResult = client.listTools()
        assertTrue(toolsResult.isSuccess)

        // Проверяем наличие инструментов DeepWiki
        val tools = toolsResult.getOrNull()!!
        assertTrue(tools.any { it.name == "read_wiki_structure" })
    } finally {
        client.close()
    }
}
```

## Возможные проблемы и решения

### Ошибка "SSE соединение вернуло статус 404"

**Решение:** Убедитесь, что URL правильный. DeepWiki использует `/sse` endpoint.

### Ошибка "Не удалось получить endpoint из SSE"

**Решение:** Сервер может не поддерживать session-based протокол. Попробуйте использовать `MCPClient` вместо `MCPSSEClient`.

### Таймаут подключения

**Решение:** Увеличьте таймаут в настройках HTTP клиента:

```kotlin
engine {
    requestTimeout = 60_000 // 60 секунд
}
```

## Структура файлов

```
src/main/kotlin/mcp/
├── MCPModels.kt          # Модели данных (добавлены MCPToolCallResult, MCPContent)
├── MCPClient.kt          # HTTP клиент (добавлен callTool)
├── MCPSSEClient.kt       # SSE клиент (добавлен callTool)
└── MCPMockClient.kt      # Mock клиент (добавлен callTool)

src/main/kotlin/ui/
└── MCPToolsUI.kt         # Полностью переписанный UI с поддержкой вызова инструментов

MCP_DEEPWIKI_SETUP.md     # Эта документация
```

## Дальнейшее развитие

- [ ] Добавить кэширование session endpoints
- [ ] Реализовать переподключение при обрыве SSE
- [ ] Добавить поддержку потоковой передачи данных
- [ ] Сохранение истории вызовов инструментов
- [ ] Экспорт результатов в файл
- [ ] Поддержка WebSocket транспорта

## Полезные ссылки

- [DeepWiki Documentation](https://docs.devin.ai/work-with-devin/deepwiki-mcp)
- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [MCP Servers Repository](https://github.com/modelcontextprotocol/servers)
