# Интеграция MCPLocalClient в UI

## Обзор изменений

Добавлена полная поддержка локального MCP сервера в графическом интерфейсе приложения через вкладку "MCP Tools".

## Что было сделано

### 1. Реализация интерфейса MCPClientInterface

**Файл:** `src/main/kotlin/mcp/MCPLocalClient.kt`

`MCPLocalClient` теперь реализует интерфейс `MCPClientInterface`, что позволяет использовать его единообразно с другими MCP клиентами в UI.

```kotlin
class MCPLocalClient(
    private val serverUrl: String,
    private val timeout: Long = 10_000
) : MCPClientInterface, AutoCloseable {

    override suspend fun initialize(): Result<MCPInitializeResult>
    override suspend fun listTools(): Result<List<MCPTool>>
    override suspend fun callTool(toolName: String, arguments: Map<String, Any>): Result<MCPToolCallResult>
    override fun close()
}
```

### 2. Автоматическое определение типа клиента

**Файл:** `src/main/kotlin/ui/MCPToolsUI.kt`

При вводе URL сервера приложение автоматически определяет, какой клиент использовать:

- `127.0.0.1` или `localhost` → **MCPLocalClient** (HTTP транспорт)
- `deepwiki.com` → **MCPDeepWikiClient** (специализированный клиент)
- Другие URL → **MCPSSEClient** (SSE транспорт)

```kotlin
val client: MCPClientInterface = when {
    serverUrl.contains("deepwiki.com") -> {
        println("🔄 Используем специальный DeepWiki клиент...")
        MCPDeepWikiClient()
    }
    serverUrl.contains("127.0.0.1") || serverUrl.contains("localhost") -> {
        println("🔄 Локальный сервер обнаружен, используем MCPLocalClient...")
        MCPLocalClient(serverUrl)
    }
    else -> {
        println("🔄 Попытка подключения через SSE клиент...")
        MCPSSEClient(serverUrl)
    }
}
```

### 3. Автоматический вызов ttools

При успешном подключении к любому MCP серверу автоматически вызывается инструмент `ttools` (если он доступен) для получения детальной информации об инструментах:

```kotlin
// Пробуем вызвать ttools для получения детальной информации
try {
    val ttoolsResult = client.callTool("ttools", emptyMap())
    if (ttoolsResult.isSuccess) {
        val content = ttoolsResult.getOrNull()?.content
        ttoolsInfo = content?.firstOrNull()?.text
        println("✓ ttools вызван успешно")
    }
} catch (e: Exception) {
    println("⚠️ ttools недоступен: ${e.message}")
}
```

### 4. Отображение информации ttools в UI

Добавлена специальная карточка для отображения детальной информации из `ttools`:

```kotlin
// Детальная информация из ttools
ttoolsInfo?.let { info ->
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Детальная информация об инструментах (ttools):",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                TextButton(onClick = { ttoolsInfo = null }) {
                    Text("Скрыть")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = info,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 150.dp),
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
```

### 5. Обновленная подсказка пользователю

Добавлен пример подключения к локальному серверу в информационном блоке:

```
Примеры серверов:

🏠 Локальный Python MCP сервер:
http://127.0.0.1:8000/mcp

📚 DeepWiki (документация GitHub):
https://mcp.deepwiki.com/sse

При подключении к локальному серверу автоматически будет вызван
инструмент ttools для получения детальной информации о доступных функциях.
```

### 6. Улучшенная обработка ошибок

Специализированные сообщения об ошибках для локальных серверов:

```kotlin
if (serverUrl.contains("127.0.0.1") || serverUrl.contains("localhost")) {
    errorMessage = "Не удалось подключиться к локальному серверу.
                    Убедитесь, что Python MCP сервер запущен."
} else {
    errorMessage = "Не удалось подключиться к серверу.
                    Проверьте URL и интернет-соединение."
}
```

## Как использовать

### 1. Запустите Python MCP сервер

```bash
python your_mcp_server.py
```

Убедитесь, что сервер слушает на `http://127.0.0.1:8000/mcp`

### 2. Откройте вкладку "MCP Tools" в приложении

```bash
./gradlew run
```

### 3. Введите URL локального сервера

В поле "MCP Server URL" введите:
```
http://127.0.0.1:8000/mcp
```

### 4. Нажмите "Подключиться"

Приложение автоматически:
1. Определит, что это локальный сервер
2. Использует `MCPLocalClient`
3. Инициализирует соединение
4. Получит список инструментов через `tools/list`
5. Вызовет `ttools` для детальной информации
6. Отобразит все доступные инструменты

### 5. Используйте инструменты

- Нажмите "Вызвать" на любом инструменте
- Заполните необходимые параметры
- Нажмите "Выполнить"
- Результат отобразится в отдельной карточке

## Архитектура

```
┌─────────────────────────┐
│   MCPToolsScreen (UI)   │
└───────────┬─────────────┘
            │
            ├──────────────────┐
            │                  │
            ▼                  ▼
┌─────────────────┐   ┌──────────────────┐
│ MCPLocalClient  │   │ MCPSSEClient /   │
│ (127.0.0.1)     │   │ MCPDeepWikiClient│
└────────┬────────┘   └────────┬─────────┘
         │                     │
         │                     │
         ▼                     ▼
┌──────────────────────────────────┐
│     MCPClientInterface           │
│  - initialize()                  │
│  - listTools()                   │
│  - callTool(name, args)          │
│  - close()                       │
└──────────────────────────────────┘
```

## Логи взаимодействия

При подключении к локальному серверу в консоли вы увидите:

```
🔄 Локальный сервер обнаружен, используем MCPLocalClient...
✓ ttools вызван успешно
```

На стороне Python сервера:

```
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK      # initialize
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 202 Accepted # notifications/initialized
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK      # tools/list
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK      # tools/call (ttools)
```

## Преимущества

1. **Автоматическое определение** - не нужно выбирать тип клиента вручную
2. **Детальная информация** - автоматический вызов `ttools` для получения сигнатур и документации
3. **Единый интерфейс** - все клиенты используют `MCPClientInterface`
4. **Понятные ошибки** - специализированные сообщения для разных типов серверов
5. **Бесшовная интеграция** - локальный клиент работает так же, как и остальные

## Тестирование

Для тестирования функциональности:

1. Запустите Python MCP сервер с инструментами `greet`, `add`, `ttools`
2. Запустите приложение: `./gradlew run`
3. Перейдите на вкладку "MCP Tools"
4. Подключитесь к `http://127.0.0.1:8000/mcp`
5. Проверьте, что:
   - Отображается информация о сервере
   - Показывается результат `ttools`
   - Список инструментов доступен
   - Можно вызывать инструменты с параметрами

## Файлы, затронутые изменениями

1. `src/main/kotlin/mcp/MCPLocalClient.kt` - реализация `MCPClientInterface`
2. `src/main/kotlin/ui/MCPToolsUI.kt` - интеграция в UI
3. `src/main/kotlin/mcp/MCPLocalClientExample.kt` - исправлены вызовы

## Совместимость

- ✅ Работает с FastMCP серверами (Python)
- ✅ Совместимо с существующими SSE и DeepWiki клиентами
- ✅ Поддерживает все инструменты, включая `ttools`
- ✅ Корректно обрабатывает ошибки и таймауты

## Дальнейшие улучшения

Возможные улучшения в будущем:

1. Кэширование результатов `ttools`
2. Автообновление списка инструментов
3. Экспорт детальной информации об инструментах
4. Сохранение истории вызовов
5. Поддержка нескольких одновременных подключений
