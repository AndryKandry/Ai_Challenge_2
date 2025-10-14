# Реализация MCP (Model Context Protocol) клиента

## Обзор

В проект добавлена поддержка Model Context Protocol (MCP) - протокола для взаимодействия с серверами инструментов AI. Реализация включает клиент для подключения к MCP серверам и UI для отображения доступных инструментов.

## Что было реализовано

### 1. Модели данных MCP (`src/main/kotlin/mcp/MCPModels.kt`)

Созданы data классы для работы с MCP протоколом:
- `MCPRequest` - запросы к серверу (JSON-RPC 2.0)
- `MCPResponse` - ответы от сервера
- `MCPTool` - описание инструмента
- `MCPInitializeResult` - результат инициализации соединения
- Прочие вспомогательные модели

### 2. MCP клиент (`src/main/kotlin/mcp/MCPClient.kt`)

Клиент для работы с MCP серверами со следующими возможностями:

**Основные функции:**
- `initialize()` - инициализация соединения с сервером
- `listTools()` - получение списка доступных инструментов
- `close()` - закрытие соединения и освобождение ресурсов

**Особенности реализации:**
- Использует Ktor HTTP клиент для запросов
- Поддержка JSON-RPC 2.0 протокола
- Обработка SSE (Server-Sent Events) ответов
- Автоматическая инициализация при первом запросе
- Обработка ошибок через Result<T>

### 3. UI для MCP Tools (`src/main/kotlin/ui/MCPToolsUI.kt`)

Пользовательский интерфейс на Compose Desktop:

**Функциональность:**
- Ввод URL MCP сервера
- Кнопка подключения с индикатором загрузки
- Отображение информации о сервере после подключения
- Список инструментов с описаниями
- Показ input schema для каждого инструмента
- Обработка и отображение ошибок

**UI компоненты:**
- `MCPToolsScreen` - основной экран
- `ToolCard` - карточка инструмента

### 4. Интеграция в приложение

Добавлена новая вкладка "🔧 MCP Tools" в главное окно приложения (`Application.kt`)

### 5. Тесты (`src/test/kotlin/mcp/MCPClientTest.kt`)

Созданы тесты для проверки работы клиента:
- `testMCPConnection` - тест подключения к основному URL
- `testAlternativeUrl` - тест альтернативного URL

## Использование

### Запуск приложения

```bash
./gradlew run
```

### Подключение к MCP серверу

1. Откройте приложение
2. Перейдите на вкладку "🔧 MCP Tools"
3. Введите URL MCP сервера (по умолчанию: `https://mcp.deepwiki.com/sse`)
4. Нажмите кнопку "Подключиться и получить инструменты"
5. После успешного подключения отобразится список доступных инструментов

### Примеры MCP серверов для тестирования

- `https://mcp.deepwiki.com/sse` (если не работает, попробуйте без `/sse`)
- `https://mcp.deepwiki.com/`

## Архитектура

```
┌─────────────────┐
│   UI Layer      │  MCPToolsScreen
│   (Compose)     │  └─ ToolCard
└────────┬────────┘
         │
┌────────▼────────┐
│   Client Layer  │  MCPClient
│   (Ktor)        │  ├─ initialize()
│                 │  ├─ listTools()
│                 │  └─ sendRequest()
└────────┬────────┘
         │
┌────────▼────────┐
│   Data Layer    │  MCPModels
│   (Models)      │  ├─ MCPRequest
│                 │  ├─ MCPResponse
│                 │  └─ MCPTool
└─────────────────┘
```

## Технический стек

- **Kotlin** - основной язык
- **Ktor Client** - HTTP клиент для запросов
- **Kotlinx Serialization** - JSON сериализация
- **Compose Desktop** - UI фреймворк
- **Coroutines** - асинхронность

## Особенности реализации

### Обработка SSE

Клиент поддерживает обработку Server-Sent Events формата:
```
data: {"jsonrpc":"2.0","id":1,"result":{...}}
event: message
```

Если сервер возвращает обычный JSON, клиент также это обрабатывает.

### Безопасность

- Все запросы выполняются асинхронно через Coroutines
- Используется `Result<T>` для безопасной обработки ошибок
- HTTP клиент правильно закрывается через `close()`

### JSON-RPC 2.0

Клиент полностью соответствует спецификации JSON-RPC 2.0:
- Автоматическая генерация уникальных ID запросов
- Поддержка `method`, `params`, `result` и `error`
- Правильная обработка уведомлений (notifications)

## Возможные проблемы

### 404 Not Found

Если сервер возвращает 404, попробуйте:
1. Убрать `/sse` из URL
2. Добавить другой endpoint (например `/api/mcp`)
3. Проверить, что сервер действительно поддерживает MCP

### CORS ошибки

Если возникают CORS ошибки в веб-версии, используйте десктоп версию приложения.

## Дальнейшее развитие

Потенциальные улучшения:
- [ ] Поддержка вызова инструментов
- [ ] Кэширование списка инструментов
- [ ] Сохранение истории подключений
- [ ] Поддержка WebSocket для real-time коммуникации
- [ ] Автоматическое переподключение при обрыве связи
- [ ] Поддержка аутентификации
- [ ] Экспорт списка инструментов в JSON/CSV

## Примеры использования в коде

### Простое подключение

```kotlin
val client = MCPClient("https://mcp.deepwiki.com/sse")

// Инициализация
val initResult = client.initialize()
if (initResult.isSuccess) {
    println("Connected to: ${initResult.getOrNull()?.serverInfo?.name}")
}

// Получение инструментов
val toolsResult = client.listTools()
if (toolsResult.isSuccess) {
    val tools = toolsResult.getOrNull() ?: emptyList()
    tools.forEach { tool ->
        println("Tool: ${tool.name} - ${tool.description}")
    }
}

// Закрытие
client.close()
```

### Использование в UI

```kotlin
@Composable
fun MyScreen() {
    var tools by remember { mutableStateOf<List<MCPTool>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Button(onClick = {
        scope.launch {
            val client = MCPClient("https://mcp.server.com")
            val result = client.listTools()
            if (result.isSuccess) {
                tools = result.getOrNull() ?: emptyList()
            }
            client.close()
        }
    }) {
        Text("Get Tools")
    }
}
```

## Файлы проекта

Созданные файлы:
- `src/main/kotlin/mcp/MCPModels.kt` - модели данных
- `src/main/kotlin/mcp/MCPClient.kt` - клиент MCP
- `src/main/kotlin/ui/MCPToolsUI.kt` - UI для отображения инструментов
- `src/test/kotlin/mcp/MCPClientTest.kt` - тесты
- `MCP_IMPLEMENTATION.md` - эта документация

Измененные файлы:
- `src/main/kotlin/Application.kt` - добавлена новая вкладка MCP Tools
