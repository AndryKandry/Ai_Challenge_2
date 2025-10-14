# Быстрый старт - MCP Local Client

## Шаг 1: Запустите Python MCP сервер

Сохраните ваш Python код в файл `mcp_server.py`:

```python
from fastmcp import FastMCP
import inspect
import json

mcp = FastMCP("My MCP Server")
_REGISTERED_TOOLS = {}

def register_tool(fn=None, *, name=None):
    def decorator(f):
        tool_name = name or f.__name__
        _REGISTERED_TOOLS[tool_name] = f
        return f
    if fn is None:
        return decorator
    return decorator(fn)

@mcp.tool
@register_tool
def greet(name: str) -> str:
    return f"Hello, {name}!"

@mcp.tool
@register_tool
def add(a: int, b: int) -> int:
    """Add two numbers"""
    return a + b

@mcp.tool
@register_tool
def ttools():
    tools = []
    for name, fn in _REGISTERED_TOOLS.items():
        sig = str(inspect.signature(fn))
        doc = inspect.getdoc(fn) or ""
        tools.append({"name": name, "signature": sig, "doc": doc})
    return json.dumps(tools)

if __name__ == "__main__":
    mcp.run(transport="http", port=8000)
```

Запустите сервер:
```bash
python mcp_server.py
```

Вы должны увидеть:
```
INFO:     Started server process [xxxxx]
INFO:     Waiting for application startup.
INFO:     Application startup complete.
INFO:     Uvicorn running on http://127.0.0.1:8000 (Press CTRL+C to quit)
```

## Шаг 2: Запустите Kotlin пример

### Вариант A: Через командную строку

```bash
./gradlew run -PmainClass=org.kozyrev.mcp.MCPLocalClientExampleKt
```

### Вариант B: Через IDE (IntelliJ IDEA)

1. Откройте файл `src/main/kotlin/mcp/MCPLocalClientExample.kt`
2. Нажмите на зеленую стрелку рядом с `fun main()`
3. Выберите "Run 'MCPLocalClientExampleKt'"

## Ожидаемый результат

В консоли Kotlin приложения вы увидите:

```
=== Пример работы с локальным MCP сервером ===

1. Инициализация соединения...
   ✓ Подключено к серверу: My MCP Server v0.1.0
   ✓ Версия протокола: 2024-11-05

2. Получение списка доступных инструментов...
   ✓ Найдено инструментов: 3
     - greet: нет описания
     - add: Add two numbers
     - ttools: нет описания

3. Вызов инструмента 'ttools' (детальная информация)...
   ✓ Результат:
   [{"name": "greet", "signature": "(name: str) -> str", "doc": ""}, ...]

4. Вызов инструмента 'greet' с аргументом name='Kotlin'...
   ✓ Ответ сервера: Hello, Kotlin!

5. Вызов инструмента 'add' с аргументами a=15, b=27...
   ✓ Результат вычисления: 42

6. Попытка вызова несуществующего инструмента 'nonexistent'...
   ✓ Ожидаемая ошибка: MCP Tools/Call Error: Unknown tool: nonexistent

=== Завершение работы (соединение закрывается автоматически) ===
```

В консоли Python сервера вы увидите логи:

```
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 202 Accepted
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
INFO:     127.0.0.1:xxxxx - "DELETE /mcp HTTP/1.1" 200 OK
```

## Шаг 3: Запустите тесты (опционально)

```bash
./gradlew test --tests "org.kozyrev.mcp.MCPLocalClientTest"
```

## Простой тест в коде

Создайте файл `TestMCPLocal.kt`:

```kotlin
package org.kozyrev.mcp

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("Тестирование MCPLocalClient...")

    MCPLocalClient("http://127.0.0.1:8000/mcp").use { client ->
        // Вызов greet
        val result = client.callTool("greet", mapOf("name" to "Test"))

        result.fold(
            onSuccess = { callResult ->
                val text = callResult.content.firstOrNull()?.text
                println("✓ Успех: $text")
            },
            onFailure = { error ->
                println("✗ Ошибка: ${error.message}")
            }
        )
    }

    println("Тест завершен!")
}
```

Запустите:
```bash
./gradlew run -PmainClass=org.kozyrev.mcp.TestMCPLocalKt
```

## Использование в вашем коде

```kotlin
import org.kozyrev.mcp.MCPLocalClient
import kotlinx.coroutines.runBlocking

fun callMCPTool() = runBlocking {
    MCPLocalClient("http://127.0.0.1:8000/mcp").use { client ->
        // Получить список инструментов
        val tools = client.listTools().getOrNull()
        println("Доступные инструменты: ${tools?.map { it.name }}")

        // Вызвать инструмент
        val result = client.callTool("add", mapOf("a" to 5, "b" to 10))
        result.onSuccess { callResult ->
            val answer = callResult.content.firstOrNull()?.text
            println("5 + 10 = $answer")
        }
    }
}
```

## Устранение проблем

### Проблема: Connection refused

**Причина:** Python сервер не запущен

**Решение:**
```bash
python mcp_server.py
```

### Проблема: timeout exceeded

**Причина:** Сервер не отвечает вовремя

**Решение:** Увеличьте таймаут
```kotlin
val client = MCPLocalClient(
    serverUrl = "http://127.0.0.1:8000/mcp",
    timeout = 30_000  // 30 секунд
)
```

### Проблема: Порт занят

**Причина:** Порт 8000 уже используется

**Решение:** Измените порт в Python сервере:
```python
mcp.run(transport="http", port=8001)
```

И в Kotlin клиенте:
```kotlin
MCPLocalClient("http://127.0.0.1:8001/mcp")
```

## Следующие шаги

1. Прочитайте полную документацию: `MCP_LOCAL_CLIENT_README.md`
2. Изучите примеры в `MCPLocalClientExample.kt`
3. Посмотрите тесты в `MCPLocalClientTest.kt`
4. Попробуйте добавить свои инструменты в Python сервер
5. Интегрируйте клиент в ваше приложение

## Полезные ссылки

- Основной файл клиента: `src/main/kotlin/mcp/MCPLocalClient.kt`
- Пример использования: `src/main/kotlin/mcp/MCPLocalClientExample.kt`
- Тесты: `src/test/kotlin/mcp/MCPLocalClientTest.kt`
- Полная документация: `MCP_LOCAL_CLIENT_README.md`
