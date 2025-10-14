# MCPLocalClient - Клиент для локального MCP сервера

## Описание

`MCPLocalClient` - это Kotlin клиент для работы с локальными MCP (Model Context Protocol) серверами, которые используют HTTP транспорт через FastMCP.

## Особенности

- ✅ Полная поддержка MCP спецификации версии 2024-11-05
- ✅ HTTP транспорт (POST, GET, DELETE запросы)
- ✅ Автоматическая инициализация и управление сессией
- ✅ Type-safe API с использованием Kotlin Result
- ✅ AutoCloseable интерфейс для корректного освобождения ресурсов
- ✅ Подробная обработка ошибок
- ✅ Асинхронные операции с Kotlin Coroutines

## Сравнение с Python клиентом

### Python клиент (FastMCP)
```python
import asyncio
from fastmcp import Client

client = Client("http://127.0.0.1:8000/mcp")

async def call_tool():
    async with client:
        result = await client.call_tool("ttools", {})
        print(result)

asyncio.run(call_tool())
```

### Kotlin клиент (MCPLocalClient)
```kotlin
import org.kozyrev.mcp.MCPLocalClient
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    MCPLocalClient("http://127.0.0.1:8000/mcp").use { client ->
        val result = client.callTool("ttools")
        println(result.getOrNull())
    }
}
```

## Установка и настройка

### 1. Зависимости

Все необходимые зависимости уже включены в `build.gradle.kts`:
- Ktor Client для HTTP запросов
- Kotlinx Serialization для JSON
- Kotlinx Coroutines для асинхронности

### 2. Запуск Python MCP сервера

Сначала убедитесь, что ваш локальный MCP сервер запущен:

```bash
python your_mcp_server.py
```

Пример Python сервера:
```python
from fastmcp import FastMCP

mcp = FastMCP("My MCP Server")

@mcp.tool
def greet(name: str) -> str:
    return f"Hello, {name}!"

@mcp.tool
def add(a: int, b: int) -> int:
    """Add two numbers"""
    return a + b

if __name__ == "__main__":
    mcp.run(transport="http", port=8000)
```

Сервер должен быть доступен по адресу: `http://127.0.0.1:8000/mcp`

## Использование

### Базовый пример

```kotlin
import org.kozyrev.mcp.MCPLocalClient
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Создание клиента с автоматическим закрытием
    MCPLocalClient("http://127.0.0.1:8000/mcp").use { client ->

        // Инициализация (опционально, происходит автоматически)
        client.initialize()

        // Получение списка инструментов
        val tools = client.listTools().getOrThrow()
        println("Доступные инструменты: ${tools.map { it.name }}")

        // Вызов инструмента
        val result = client.callTool("greet", mapOf("name" to "World"))
        result.onSuccess { callResult ->
            callResult.content.forEach { content ->
                println("Результат: ${content.text}")
            }
        }
    }
}
```

### Получение списка инструментов

```kotlin
MCPLocalClient(serverUrl).use { client ->
    val toolsResult = client.listTools()

    toolsResult.onSuccess { tools ->
        tools.forEach { tool ->
            println("${tool.name}: ${tool.description ?: "нет описания"}")
            println("  Схема: ${tool.inputSchema}")
        }
    }

    toolsResult.onFailure { error ->
        println("Ошибка: ${error.message}")
    }
}
```

### Вызов инструментов

```kotlin
MCPLocalClient(serverUrl).use { client ->
    // Простой вызов без аргументов
    val result1 = client.callTool("ttools")

    // Вызов с одним аргументом
    val result2 = client.callTool("greet", mapOf("name" to "Kotlin"))

    // Вызов с несколькими аргументами
    val result3 = client.callTool("add", mapOf(
        "a" to 15,
        "b" to 27
    ))

    result3.onSuccess { callResult ->
        callResult.content.forEach { content ->
            when (content.type) {
                "text" -> println("Текст: ${content.text}")
                "image" -> println("Изображение: ${content.data}")
                else -> println("Неизвестный тип: ${content.type}")
            }
        }
    }
}
```

### Обработка ошибок

```kotlin
MCPLocalClient(serverUrl).use { client ->
    val result = client.callTool("nonexistent")

    result.fold(
        onSuccess = { callResult ->
            println("Успех: $callResult")
        },
        onFailure = { error ->
            when {
                error.message?.contains("not found") == true ->
                    println("Инструмент не найден")
                error.message?.contains("timeout") == true ->
                    println("Превышено время ожидания")
                else ->
                    println("Ошибка: ${error.message}")
            }
        }
    )
}
```

### Настройка таймаута

```kotlin
// Таймаут по умолчанию: 10 секунд
val client1 = MCPLocalClient("http://127.0.0.1:8000/mcp")

// Кастомный таймаут: 30 секунд
val client2 = MCPLocalClient(
    serverUrl = "http://127.0.0.1:8000/mcp",
    timeout = 30_000
)
```

## Протокол взаимодействия

Клиент выполняет следующую последовательность HTTP запросов:

1. **POST /mcp** - Инициализация (initialize)
   ```json
   {
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
   }
   ```

2. **POST /mcp** - Уведомление об инициализации (notifications/initialized)
   ```json
   {
     "jsonrpc": "2.0",
     "method": "notifications/initialized"
   }
   ```

3. **POST /mcp** - Операции с инструментами (tools/list, tools/call)
   ```json
   {
     "jsonrpc": "2.0",
     "id": 2,
     "method": "tools/call",
     "params": {
       "name": "greet",
       "arguments": {"name": "World"}
     }
   }
   ```

4. **DELETE /mcp** - Завершение сессии (при закрытии клиента)

## Логи взаимодействия

При работе клиента вы увидите следующие HTTP запросы на сервере:

```
INFO:     127.0.0.1:60542 - "POST /mcp HTTP/1.1" 200 OK      # initialize
INFO:     127.0.0.1:60543 - "POST /mcp HTTP/1.1" 202 Accepted # notifications/initialized
INFO:     127.0.0.1:60544 - "POST /mcp HTTP/1.1" 200 OK      # tools/list или tools/call
INFO:     127.0.0.1:60545 - "DELETE /mcp HTTP/1.1" 200 OK    # close session
```

## Запуск примера

### Через Gradle
```bash
# Запуск примера использования
./gradlew run --args="org.kozyrev.mcp.MCPLocalClientExampleKt"
```

### Через IDE
Откройте `MCPLocalClientExample.kt` и запустите функцию `main()`

## Тестирование

### Запуск тестов

```bash
# Убедитесь, что локальный MCP сервер запущен!
python your_mcp_server.py

# Запустите тесты
./gradlew test --tests "org.kozyrev.mcp.MCPLocalClientTest"
```

### Доступные тесты

- `test initialization` - тест инициализации соединения
- `test list tools` - тест получения списка инструментов
- `test call greet tool` - тест вызова инструмента greet
- `test call add tool` - тест вызова инструмента add
- `test call ttools` - тест вызова инструмента ttools
- `test call non-existent tool` - тест обработки ошибок
- `test multiple sequential calls` - тест множественных вызовов
- `test client reusability` - тест переиспользования клиента

## Структура проекта

```
src/main/kotlin/mcp/
├── MCPLocalClient.kt          # Основной клиент
├── MCPLocalClientExample.kt   # Пример использования
├── MCPModels.kt               # Модели данных (общие с другими MCP клиентами)
└── MCPClient.kt               # SSE клиент (для удаленных серверов)

src/test/kotlin/mcp/
└── MCPLocalClientTest.kt      # Тесты клиента
```

## Отличия от MCPClient (SSE)

| Особенность | MCPLocalClient | MCPClient (SSE) |
|-------------|----------------|-----------------|
| Транспорт | HTTP (POST/GET/DELETE) | SSE (Server-Sent Events) |
| Назначение | Локальные FastMCP серверы | Удаленные MCP серверы |
| Streaming | Нет | Да |
| Таймаут | 10 секунд по умолчанию | 30 секунд по умолчанию |
| Сессии | DELETE для завершения | Автоматическое закрытие потока |

## Troubleshooting

### Ошибка подключения

**Проблема:** `Ошибка подключения: Connection refused`

**Решение:**
1. Убедитесь, что Python MCP сервер запущен
2. Проверьте, что сервер слушает на порту 8000
3. Проверьте URL: `http://127.0.0.1:8000/mcp`

### Таймаут

**Проблема:** `Превышено время ожидания`

**Решение:**
```kotlin
// Увеличьте таймаут
val client = MCPLocalClient(
    serverUrl = "http://127.0.0.1:8000/mcp",
    timeout = 30_000  // 30 секунд
)
```

### Ошибка инициализации

**Проблема:** `Клиент уже инициализирован`

**Решение:**
```kotlin
// Не вызывайте initialize() явно, если не требуется
MCPLocalClient(serverUrl).use { client ->
    // Инициализация происходит автоматически при первом вызове
    client.listTools()
}
```

## Примеры использования в UI

### Интеграция с Compose Desktop

```kotlin
@Composable
fun MCPToolsScreen() {
    var tools by remember { mutableStateOf<List<MCPTool>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        MCPLocalClient("http://127.0.0.1:8000/mcp").use { client ->
            client.listTools()
                .onSuccess { tools = it }
                .onFailure { error = it.message }
        }
        isLoading = false
    }

    Column {
        if (isLoading) {
            CircularProgressIndicator()
        }

        error?.let { Text("Ошибка: $it", color = Color.Red) }

        tools.forEach { tool ->
            Card {
                Text(tool.name)
                tool.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
```

## Дополнительные ресурсы

- [MCP Спецификация](https://spec.modelcontextprotocol.io/)
- [FastMCP документация](https://github.com/jlowin/fastmcp)
- [Ktor Client документация](https://ktor.io/docs/client.html)

## Лицензия

Этот код является частью учебного проекта AI Challenge 2.
