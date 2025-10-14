# Решение: Python FastMCP клиент из Kotlin

## Проблема

HTTP клиент на Kotlin (`MCPLocalClient`) не смог корректно подключиться к локальному FastMCP серверу, несмотря на попытки исправления заголовков.

## Решение

Создан гибридный подход: Kotlin приложение запускает Python скрипт с FastMCP клиентом, гарантируя 100% совместимость с FastMCP сервером.

## Архитектура

```
┌─────────────────────┐
│   Kotlin UI         │
│   (MCPToolsScreen)  │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  MCPPythonClient    │
│  (Kotlin обёртка)   │
└──────────┬──────────┘
           │ ProcessBuilder
           ▼
┌─────────────────────┐
│ mcp_python_client.py│
│   (FastMCP Client)  │
└──────────┬──────────┘
           │ HTTP
           ▼
┌─────────────────────┐
│   FastMCP Server    │
│  (Python, port 8000)│
└─────────────────────┘
```

## Созданные файлы

### 1. mcp_python_client.py

Python скрипт, который использует FastMCP библиотеку для взаимодействия с сервером.

**Команды:**
- `initialize` - инициализация соединения
- `list_tools` - получение списка инструментов
- `call_tool` - вызов инструмента

**Использование из командной строки:**
```bash
# Инициализация
python mcp_python_client.py initialize http://127.0.0.1:8000/mcp

# Вызов инструмента
python mcp_python_client.py call_tool http://127.0.0.1:8000/mcp ttools "{}"
```

**Формат вывода:** JSON
```json
{
  "success": true,
  "content": [
    {
      "type": "text",
      "text": "результат"
    }
  ]
}
```

### 2. MCPPythonClient.kt

Kotlin обёртка, которая:
1. Проверяет наличие Python
2. Запускает Python скрипт через `ProcessBuilder`
3. Парсит JSON ответ
4. Преобразует в `MCPToolCallResult`
5. Реализует интерфейс `MCPClientInterface`

**Особенности:**
- Автоматически ищет `python3` или `python`
- Подробное логирование всех операций
- Корректная обработка ошибок
- Поддержка всех типов аргументов (String, Number, Boolean)

### 3. Интеграция в UI

Обновлён `MCPToolsUI.kt` для использования `MCPPythonClient` при подключении к локальным серверам (127.0.0.1, localhost).

## Требования

### Python окружение

```bash
# Установка fastmcp
pip install fastmcp

# Проверка установки
python -c "import fastmcp; print(fastmcp.__version__)"
```

### Расположение файлов

```
ai_challenge_2/
├── mcp_python_client.py          # Python скрипт (корень проекта)
└── src/main/kotlin/mcp/
    ├── MCPPythonClient.kt         # Kotlin обёртка
    └── ...
```

## Использование

### 1. Убедитесь, что Python и fastmcp установлены

```bash
python3 --version
pip show fastmcp
```

### 2. Запустите Python MCP сервер

```bash
python your_mcp_server.py
```

### 3. Запустите Kotlin приложение

```bash
./gradlew run
```

### 4. Подключитесь в UI

1. Откройте вкладку "🔧 MCP Tools"
2. Введите: `http://127.0.0.1:8000/mcp`
3. Нажмите "Подключиться"

## Логи взаимодействия

### Консоль Kotlin
```
🔄 Локальный сервер обнаружен, используем Python FastMCP клиент...
🐍 Запуск Python клиента: python3 mcp_python_client.py call_tool http://127.0.0.1:8000/mcp ttools {}
📊 Python вывод: {"success": true, "content": [{"type": "text", "text": "..."}]}
✓ ttools вызван успешно
```

### Консоль Python MCP сервера
```
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 202 Accepted
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
INFO:     127.0.0.1:xxxxx - "DELETE /mcp HTTP/1.1" 200 OK
```

## Преимущества решения

### ✅ Гарантированная совместимость
Используется та же библиотека FastMCP, что и на сервере

### ✅ Простота отладки
Python код легко тестировать отдельно:
```bash
python mcp_python_client.py call_tool http://127.0.0.1:8000/mcp greet '{"name":"Test"}'
```

### ✅ Минимальные изменения в UI
Достаточно заменить `MCPLocalClient` на `MCPPythonClient`

### ✅ Подробное логирование
Все операции логируются, легко диагностировать проблемы

### ✅ Универсальность
Работает на любой платформе, где есть Python 3.7+

## Тестирование

### Тест 1: Прямой вызов Python скрипта

```bash
# Вызов ttools
python mcp_python_client.py call_tool http://127.0.0.1:8000/mcp ttools "{}"

# Ожидаемый вывод:
# {"success": true, "content": [{"type": "text", "text": "[{\"name\": \"greet\", ...}]"}]}
```

### Тест 2: Через Kotlin приложение

1. Запустите MCP сервер
2. Запустите приложение
3. Подключитесь к `http://127.0.0.1:8000/mcp`
4. Проверьте:
   - ✅ Информация о сервере отображается
   - ✅ ttools вызывается автоматически
   - ✅ Список инструментов доступен
   - ✅ Инструменты можно вызывать

### Тест 3: Вызов инструмента с параметрами

```bash
python mcp_python_client.py call_tool http://127.0.0.1:8000/mcp greet '{"name":"Kotlin"}'

# Ожидаемый вывод:
# {"success": true, "content": [{"type": "text", "text": "Hello, Kotlin!"}]}
```

## Troubleshooting

### Проблема: Python не найден

**Ошибка:**
```
Python не найден. Установите Python 3.7+
```

**Решение:**
```bash
# macOS/Linux
brew install python3
# или
apt-get install python3

# Windows
# Скачайте с https://python.org
```

### Проблема: fastmcp не установлен

**Ошибка:**
```
ModuleNotFoundError: No module named 'fastmcp'
```

**Решение:**
```bash
pip install fastmcp
# или
pip3 install fastmcp
```

### Проблема: mcp_python_client.py не найден

**Ошибка:**
```
Python скрипт не найден: /path/to/mcp_python_client.py
```

**Решение:**
- Убедитесь, что файл находится в корне проекта
- Проверьте права доступа: `chmod +x mcp_python_client.py`

### Проблема: JSON parse error

**Ошибка:**
```
Не удалось распарсить ответ Python: ...
```

**Решение:**
- Запустите Python скрипт напрямую для диагностики
- Проверьте вывод stderr
- Убедитесь, что fastmcp установлен корректно

## Пример полного цикла

```
1. Пользователь вводит URL: http://127.0.0.1:8000/mcp
2. UI определяет: локальный сервер → MCPPythonClient
3. MCPPythonClient.initialize():
   - Проверяет наличие Python ✓
   - Запускает: python3 mcp_python_client.py initialize http://127.0.0.1:8000/mcp
   - Получает JSON: {"success": true, "serverInfo": {...}}
   - Возвращает MCPInitializeResult
4. UI автоматически вызывает ttools:
   - MCPPythonClient.callTool("ttools", {})
   - Запускает: python3 mcp_python_client.py call_tool http://127.0.0.1:8000/mcp ttools {}
   - Получает список инструментов
5. UI отображает результаты
6. Пользователь вызывает greet с параметром "World":
   - MCPPythonClient.callTool("greet", {"name": "World"})
   - Запускает: python3 mcp_python_client.py call_tool http://127.0.0.1:8000/mcp greet '{"name":"World"}'
   - Получает: {"success": true, "content": [{"text": "Hello, World!"}]}
7. UI отображает результат
```

## Компиляция

```bash
./gradlew compileKotlin
# BUILD SUCCESSFUL
```

## Заключение

Этот гибридный подход обеспечивает:
- ✅ 100% совместимость с FastMCP серверами
- ✅ Простоту отладки
- ✅ Гибкость (можно легко модифицировать Python код)
- ✅ Надёжность (используется проверенная библиотека)

Теперь приложение может корректно работать с локальными FastMCP серверами! 🎉
