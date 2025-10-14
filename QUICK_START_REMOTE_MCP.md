# Быстрое подключение к Remote MCP Servers

## 🚀 За 3 шага

### 1️⃣ Запустите приложение
```bash
./gradlew run
```

### 2️⃣ Откройте вкладку "🔧 MCP Tools"

### 3️⃣ Введите URL и подключитесь

**Для Fetch сервера:**
```
https://remote.mcpservers.org/fetch/mcp
```

**Для Time сервера:**
```
https://remote.mcpservers.org/time/mcp
```

**Для Weather сервера:**
```
https://remote.mcpservers.org/weather/mcp
```

Нажмите **"Подключиться"** - приложение автоматически выберет правильный способ подключения!

## 📋 Все доступные серверы

### 🌐 Remote MCP Servers (формат `/mcp`)
```
https://remote.mcpservers.org/fetch/mcp    - Загрузка веб-страниц
https://remote.mcpservers.org/time/mcp     - Текущее время
https://remote.mcpservers.org/weather/mcp  - Прогноз погоды
```

### 📚 DeepWiki (формат `/sse`)
```
https://mcp.deepwiki.com/sse               - Документация GitHub
```

### ⏰ Demo серверы (формат `/sse`)
```
https://mcp.demo.modelcontextprotocol.io/time/sse
https://mcp.demo.modelcontextprotocol.io/fetch/sse
```

## 💡 Примеры использования

### В UI:
1. Введите URL (например, `https://remote.mcpservers.org/fetch/mcp`)
2. Нажмите "Подключиться"
3. Выберите инструмент и нажмите "Вызвать"
4. Заполните параметры
5. Нажмите "Выполнить"

### В коде:
```kotlin
val client = MCPSimpleClient("https://remote.mcpservers.org/fetch/mcp")
client.initialize()

val result = client.callTool(
    "fetch",
    mapOf("url" to "https://example.com")
)

println(result.getOrNull()?.content?.first()?.text)
client.close()
```

## ❓ Частые вопросы

**Q: Какой клиент использовать для remote.mcpservers.org?**
A: UI автоматически выберет правильный (MCPSimpleClient). Но можно явно использовать `MCPSimpleClient`.

**Q: Работает ли с DeepWiki?**
A: Да! UI автоматически попробует SSE клиент, затем Simple клиент.

**Q: Что делать если не подключается?**
A:
1. Проверьте URL (должен точно совпадать)
2. Посмотрите логи в консоли
3. Попробуйте Mock сервер (чекбокс в UI)

**Q: Можно ли использовать свой MCP сервер?**
A: Да! Просто введите URL. Приложение автоматически определит формат.

## 🔧 Под капотом

MCPSimpleClient автоматически определяет формат URL:
- Если заканчивается на `/mcp` → пробует remote.mcpservers.org формат
- Если заканчивается на `/sse` → пробует SSE формат
- Для остальных → пробует все варианты

UI всегда пробует сначала SSE клиент, затем Simple клиент.

## ✅ Готово!

Теперь вы можете подключаться к любым MCP серверам:
- Remote MCP Servers (формат `/mcp`) ✅
- SSE серверы (формат `/sse`) ✅
- Автоматический выбор клиента ✅
- Более 6 серверов из коробки ✅

**Просто введите URL и нажмите "Подключиться"!** 🎉
