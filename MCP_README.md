# MCP Integration - Quick Start

## Быстрый старт за 3 минуты

### 1️⃣ Запустите приложение

```bash
./gradlew run
```

### 2️⃣ Откройте вкладку "🔧 MCP Tools"

### 3️⃣ Подключитесь к DeepWiki

URL уже установлен: `https://mcp.deepwiki.com/sse`

Просто нажмите **"Подключиться"**

### 4️⃣ Используйте инструменты

После подключения увидите 3 инструмента:
- `read_wiki_structure` - Структура документации
- `read_wiki_contents` - Содержимое документации
- `ask_question` - AI-вопросы о репозитории

**Попробуйте:**
1. Нажмите "Вызвать" на `read_wiki_structure`
2. Введите: `anthropics/claude-code`
3. Нажмите "Выполнить"
4. Результат появится вверху экрана

## Другие серверы

### Time Server
```
https://mcp.demo.modelcontextprotocol.io/time/sse
```

### Fetch Server
```
https://mcp.demo.modelcontextprotocol.io/fetch/sse
```

## Демо-режим

Для тестирования без интернета:
1. Отметьте "Использовать демо-сервер (Mock)"
2. Нажмите "Подключиться к демо-серверу"
3. Увидите 5 демо-инструментов

## Пример кода

```kotlin
import org.kozyrev.mcp.*

suspend fun example() {
    val client = MCPSSEClient("https://mcp.deepwiki.com/sse")

    client.initialize()

    val result = client.callTool(
        "ask_question",
        mapOf(
            "repository" to "anthropics/claude-code",
            "question" to "What is Claude Code?"
        )
    )

    println(result.getOrNull()?.content?.first()?.text)

    client.close()
}
```

## Документация

- **MCP_USAGE_GUIDE.md** - Подробное руководство пользователя
- **MCP_DEEPWIKI_SETUP.md** - Техническая документация
- **MCP_FINAL_IMPLEMENTATION.md** - Описание реализации

## Что реализовано

✅ Подключение к MCP серверам через SSE
✅ Session-based протокол для DeepWiki
✅ Вызов инструментов с параметрами
✅ Интерактивный UI с автоматическими формами
✅ Отображение результатов
✅ Mock сервер для тестирования
✅ Полная документация
✅ Тесты

## Тесты

```bash
./gradlew test --tests "org.kozyrev.mcp.DeepWikiTest"
```

Результат: ✅ Все тесты проходят успешно

## Архитектура

```
UI (MCPToolsScreen)
    ↓
MCPClientInterface
    ↓
┌────────────┬────────────┬────────────┐
│ MCPClient  │ MCPSSEClient │ MCPMockClient │
│  (HTTP)    │   (SSE)      │   (Demo)    │
└────────────┴────────────┴────────────┘
```

## Поддержка

Если что-то не работает:
1. Проверьте URL сервера
2. Попробуйте Mock сервер
3. Посмотрите логи в консоли
4. Читайте MCP_USAGE_GUIDE.md

---

**Готово к использованию!** 🚀

Начните с подключения к DeepWiki и изучения документации любого GitHub репозитория.
