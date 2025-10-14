# Краткое руководство по использованию MCP в приложении

## Быстрый старт

### 1. Запуск приложения

```bash
./gradlew run
```

### 2. Подключение к DeepWiki

1. Откройте вкладку **🔧 MCP Tools**
2. URL по умолчанию уже установлен: `https://mcp.deepwiki.com/sse`
3. Нажмите кнопку **"Подключиться"**
4. Подождите несколько секунд
5. Увидите список доступных инструментов

### 3. Использование инструментов DeepWiki

#### read_wiki_structure - Получить структуру документации

1. Найдите инструмент `read_wiki_structure`
2. Нажмите кнопку **"Вызвать"**
3. Введите название репозитория, например: `anthropics/claude-code`
4. Нажмите **"Выполнить"**
5. Результат появится вверху экрана

#### read_wiki_contents - Прочитать документацию

1. Найдите инструмент `read_wiki_contents`
2. Нажмите **"Вызвать"**
3. Заполните параметры:
   - `repository`: `anthropics/claude-code`
   - `path`: путь к файлу из структуры
4. Нажмите **"Выполнить"**

#### ask_question - Задать вопрос о репозитории

1. Найдите инструмент `ask_question`
2. Нажмите **"Вызвать"**
3. Заполните параметры:
   - `repository`: `anthropics/claude-code`
   - `question`: ваш вопрос на английском
4. Нажмите **"Выполнить"**

## Другие доступные серверы

### Time Server

```
URL: https://mcp.demo.modelcontextprotocol.io/time/sse
```

Инструменты для работы с текущим временем.

### Fetch Server

```
URL: https://mcp.demo.modelcontextprotocol.io/fetch/sse
```

Инструменты для загрузки веб-страниц.

## Демо-режим (Mock)

Для тестирования UI без реального подключения:

1. Отметьте чекбокс **"Использовать демо-сервер (Mock)"**
2. Нажмите **"Подключиться к демо-серверу"**
3. Увидите 5 демо-инструментов
4. Можете их вызывать - они вернут фиктивные результаты

## Примеры использования в коде

### Подключение к DeepWiki

```kotlin
import org.kozyrev.mcp.*

suspend fun example() {
    val client = MCPSSEClient("https://mcp.deepwiki.com/sse")

    // Инициализация
    client.initialize()

    // Получить список инструментов
    val tools = client.listTools().getOrNull()

    // Вызвать инструмент
    val result = client.callTool(
        "read_wiki_structure",
        mapOf("repository" to "anthropics/claude-code")
    )

    // Получить результат
    result.getOrNull()?.content?.forEach { content ->
        println(content.text)
    }

    // Закрыть соединение
    client.close()
}
```

### Использование Mock клиента

```kotlin
import org.kozyrev.mcp.*

suspend fun testExample() {
    val client = MCPMockClient()

    client.initialize()
    val tools = client.listTools().getOrNull()

    // Все инструменты доступны для тестирования
    val result = client.callTool(
        "search_web",
        mapOf("query" to "test")
    )

    println(result.getOrNull()?.content?.first()?.text)

    client.close()
}
```

## Обработка ошибок

```kotlin
val result = client.callTool("tool_name", args)

if (result.isSuccess) {
    val data = result.getOrNull()
    // Успешный результат
} else {
    val error = result.exceptionOrNull()
    println("Ошибка: ${error?.message}")
}
```

## Типичные проблемы

### "Ошибка подключения"

- Проверьте URL сервера
- Убедитесь, что есть интернет-соединение
- Попробуйте Mock сервер для тестирования UI

### "Ошибка инициализации"

- Сервер может быть недоступен
- Попробуйте другой MCP сервер
- Проверьте логи в консоли приложения

### "Ошибка получения инструментов"

- Сервер инициализирован, но не отвечает на запросы
- Попробуйте переподключиться
- Проверьте, что сервер поддерживает MCP протокол

## Документация

Полная документация доступна в файлах:
- `MCP_DEEPWIKI_SETUP.md` - Подробное описание реализации
- `MCP_IMPLEMENTATION.md` - Оригинальная документация
- `MCP_SOLUTION.md` - Решение проблем с подключением

## Структура проекта

```
src/main/kotlin/
├── mcp/
│   ├── MCPModels.kt          # Модели данных
│   ├── MCPClient.kt          # HTTP клиент
│   ├── MCPSSEClient.kt       # SSE клиент (для DeepWiki)
│   └── MCPMockClient.kt      # Mock клиент для тестов
└── ui/
    └── MCPToolsUI.kt         # UI для работы с MCP
```

## Возможности

- ✅ Подключение к MCP серверам через SSE
- ✅ Автоматическая инициализация
- ✅ Получение списка инструментов
- ✅ Вызов инструментов с параметрами
- ✅ Интерактивные формы для ввода параметров
- ✅ Отображение результатов
- ✅ Mock сервер для тестирования
- ✅ Обработка ошибок
- ✅ Корректное управление соединениями

## Следующие шаги

1. Попробуйте подключиться к DeepWiki
2. Изучите документацию любого интересующего репозитория
3. Протестируйте другие MCP серверы
4. Используйте MCP в своих агентах и приложениях
