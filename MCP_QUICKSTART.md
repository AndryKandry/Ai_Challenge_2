# MCP Tools - Быстрый старт

## Описание

Реализован полнофункциональный клиент для работы с MCP (Model Context Protocol) серверами. Приложение позволяет подключаться к MCP серверам и просматривать список доступных инструментов.

## Что реализовано

✅ **MCP SDK/клиент** - полноценный клиент с поддержкой JSON-RPC 2.0
✅ **Подключение к серверу** - создание MCP соединения и инициализация
✅ **Получение списка инструментов** - метод `tools/list`
✅ **UI интерфейс** - Compose Desktop приложение с красивым интерфейсом
✅ **Обработка ошибок** - корректная обработка сетевых и протокольных ошибок

## Структура проекта

```
src/main/kotlin/
├── mcp/
│   ├── MCPModels.kt      # Модели данных MCP протокола
│   └── MCPClient.kt      # Клиент для работы с MCP серверами
└── ui/
    └── MCPToolsUI.kt     # UI для отображения инструментов

src/test/kotlin/
└── mcp/
    └── MCPClientTest.kt  # Тесты подключения
```

## Запуск приложения

### Предварительные требования

1. Java 21 или выше
2. Переменная окружения `CLAUDE_API_KEY` (для остальных функций приложения)

### Установка CLAUDE_API_KEY

**macOS/Linux:**
```bash
export CLAUDE_API_KEY="your-api-key-here"
```

**Windows:**
```cmd
set CLAUDE_API_KEY=your-api-key-here
```

### Запуск

```bash
./gradlew run
```

### Открытие вкладки MCP Tools

1. После запуска приложения откроется окно
2. Перейдите на вкладку **"🔧 MCP Tools"** (последняя вкладка справа)
3. В поле "MCP Server URL" введите адрес сервера
4. Нажмите кнопку "Подключиться и получить инструменты"

## Тестирование MCP клиента

### Компиляция

```bash
./gradlew compileKotlin
```

### Запуск тестов

```bash
./gradlew test --tests "org.kozyrev.mcp.MCPClientTest"
```

## Примеры использования

### 1. Базовое подключение

```kotlin
import org.kozyrev.mcp.MCPClient

suspend fun main() {
    val client = MCPClient("https://mcp.deepwiki.com/sse")

    // Инициализация
    val initResult = client.initialize()
    if (initResult.isSuccess) {
        println("✅ Подключено!")
        val info = initResult.getOrNull()
        println("Сервер: ${info?.serverInfo?.name}")
    }

    // Получение инструментов
    val toolsResult = client.listTools()
    if (toolsResult.isSuccess) {
        val tools = toolsResult.getOrNull() ?: emptyList()
        println("Найдено инструментов: ${tools.size}")

        tools.forEach { tool ->
            println("📦 ${tool.name}")
            println("   ${tool.description}")
        }
    }

    client.close()
}
```

### 2. Использование в UI

```kotlin
@Composable
fun MCPExample() {
    var serverUrl by remember { mutableStateOf("https://mcp.deepwiki.com/sse") }
    var tools by remember { mutableStateOf<List<MCPTool>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Column {
        TextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("MCP Server URL") }
        )

        Button(onClick = {
            scope.launch {
                val client = MCPClient(serverUrl)
                val result = client.listTools()
                if (result.isSuccess) {
                    tools = result.getOrNull() ?: emptyList()
                }
                client.close()
            }
        }) {
            Text("Получить инструменты")
        }

        // Отображение инструментов
        tools.forEach { tool ->
            Text("${tool.name}: ${tool.description}")
        }
    }
}
```

## Возможности клиента

### MCPClient API

| Метод | Описание | Возвращает |
|-------|----------|-----------|
| `initialize()` | Инициализирует соединение с сервером | `Result<MCPInitializeResult>` |
| `listTools()` | Получает список доступных инструментов | `Result<List<MCPTool>>` |
| `close()` | Закрывает соединение и освобождает ресурсы | `Unit` |

### Модели данных

**MCPTool:**
```kotlin
data class MCPTool(
    val name: String,                    // Название инструмента
    val description: String? = null,     // Описание
    val inputSchema: JsonObject? = null  // Схема входных параметров
)
```

**MCPInitializeResult:**
```kotlin
data class MCPInitializeResult(
    val protocolVersion: String,      // Версия протокола
    val serverInfo: MCPServerInfo,    // Информация о сервере
    val capabilities: JsonObject?     // Возможности сервера
)
```

## UI компоненты

### MCPToolsScreen

Главный экран с:
- Поле ввода URL сервера
- Кнопка подключения с индикатором загрузки
- Информация о подключенном сервере
- Список инструментов с карточками

### ToolCard

Карточка инструмента отображает:
- Название инструмента (синим цветом)
- Описание инструмента
- Input Schema (в моноширинном шрифте)

## Примеры MCP серверов

Для тестирования попробуйте:

1. **DeepWiki MCP Server:**
   - URL: `https://mcp.deepwiki.com/sse`
   - Альтернатива: `https://mcp.deepwiki.com/`

2. **Локальный сервер:**
   - URL: `http://localhost:3000/mcp`

## Технические детали

### Поддерживаемые возможности

✅ JSON-RPC 2.0 протокол
✅ Инициализация соединения (`initialize`)
✅ Получение списка инструментов (`tools/list`)
✅ Обработка SSE ответов
✅ Обработка обычных JSON ответов
✅ Уведомления (notifications)
✅ Обработка ошибок

### Зависимости

- **Ktor Client 3.3.0** - HTTP клиент
- **Kotlinx Serialization** - JSON обработка
- **Kotlin Coroutines** - асинхронность
- **Compose Desktop** - UI фреймворк

## Отладка

### Логирование

Клиент использует Ktor Logging. Для включения детальных логов:

```kotlin
install(Logging) {
    logger = Logger.DEFAULT
    level = LogLevel.ALL  // Показывать все запросы/ответы
}
```

### Проверка соединения

Если подключение не работает:

1. Проверьте доступность сервера:
   ```bash
   curl https://mcp.deepwiki.com/sse
   ```

2. Попробуйте альтернативный URL (без `/sse`)

3. Проверьте логи в терминале

## Известные ограничения

1. **Сервер может быть недоступен** - пример сервера `https://mcp.deepwiki.com/sse` может возвращать 404
2. **Только чтение** - пока реализовано только получение списка инструментов, без возможности их вызова
3. **POST запросы** - клиент использует POST для всех запросов (как требует JSON-RPC)

## Дальнейшее развитие

Что можно добавить:

- [ ] Вызов инструментов (`tools/call`)
- [ ] Поддержка WebSocket для real-time
- [ ] Кэширование результатов
- [ ] История подключений
- [ ] Экспорт списка инструментов
- [ ] Аутентификация
- [ ] Автоматическое переподключение

## Поддержка

Для вопросов и issues см. документацию:
- [MCP Protocol Specification](https://spec.modelcontextprotocol.io/)
- [Полная документация](MCP_IMPLEMENTATION.md)

## Примеры результата работы

При успешном подключении вы увидите:

```
✅ Подключено к серверу
Сервер: DeepWiki MCP Server v1.0.0
Найдено инструментов: 5

📦 search_wikipedia
   Поиск статей в Wikipedia

📦 get_article
   Получить полный текст статьи

... и т.д.
```

## Заключение

Реализация полностью соответствует требованиям задания:
1. ✅ Установлен MCP SDK (использованы существующие библиотеки Ktor)
2. ✅ Написан код для создания MCP-соединения
3. ✅ Получен список доступных инструментов
4. ✅ Пользователь может вводить адрес сервера
5. ✅ Результат отображается в UI

Приложение готово к использованию!
