# Быстрый старт: Python MCP клиент

## Что изменилось?

Вместо прямого HTTP подключения из Kotlin, приложение теперь использует Python FastMCP клиент для гарантированной совместимости с локальным сервером.

## Подготовка (однократно)

### 1. Проверьте Python

```bash
python3 --version
# Должна быть версия 3.7 или выше
```

### 2. Установите fastmcp

```bash
pip install fastmcp
```

### 3. Проверьте установку

```bash
python3 -c "import fastmcp; print('FastMCP установлен ✓')"
```

## Тестирование Python клиента

### Прямой тест (без UI)

```bash
# Перейдите в корень проекта
cd /Users/andreykozyrev/AiProjects/ai_challenge_2

# Запустите Python MCP сервер (в отдельном терминале)
python your_mcp_server.py

# Протестируйте Python клиент
python3 mcp_python_client.py call_tool http://127.0.0.1:8000/mcp ttools "{}"
```

**Ожидаемый результат:**
```json
{
  "success": true,
  "content": [
    {
      "type": "text",
      "text": "[{\"name\": \"greet\", \"signature\": \"(name: str) -> str\", ...}]"
    }
  ]
}
```

### Тест с параметрами

```bash
# Вызов greet
python3 mcp_python_client.py call_tool http://127.0.0.1:8000/mcp greet '{"name":"Test"}'

# Ожидается:
# {"success": true, "content": [{"type": "text", "text": "Hello, Test!"}]}
```

```bash
# Вызов add
python3 mcp_python_client.py call_tool http://127.0.0.1:8000/mcp add '{"a":10,"b":20}'

# Ожидается:
# {"success": true, "content": [{"type": "text", "text": "30"}]}
```

## Запуск через UI

### 1. Запустите Python MCP сервер

```bash
python your_mcp_server.py
```

Вы должны увидеть:
```
INFO:     Uvicorn running on http://127.0.0.1:8000
```

### 2. Запустите Kotlin приложение

```bash
./gradlew run
```

### 3. Подключитесь

1. Откройте вкладку "🔧 MCP Tools"
2. Введите URL: `http://127.0.0.1:8000/mcp`
3. Нажмите "Подключиться"

## Ожидаемые логи

### В консоли Kotlin приложения

```
🔄 Локальный сервер обнаружен, используем Python FastMCP клиент...
🐍 Запуск Python клиента: python3 mcp_python_client.py initialize http://127.0.0.1:8000/mcp
📊 Python вывод: {"success":true,"serverInfo":{"name":"MCP Server","version":"1.0.0"}...}
🐍 Запуск Python клиента: python3 mcp_python_client.py call_tool http://127.0.0.1:8000/mcp ttools {}
📊 Python вывод: {"success":true,"content":[{"type":"text","text":"[...]"}]}
✓ ttools вызван успешно
```

### В консоли Python MCP сервера

```
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 202 Accepted
INFO:     127.0.0.1:xxxxx - "POST /mcp HTTP/1.1" 200 OK
INFO:     127.0.0.1:xxxxx - "DELETE /mcp HTTP/1.1" 200 OK
```

### В UI

Вы увидите:
- ✅ "Сервер: MCP Server v1.0.0"
- ✅ Детальную информацию из ttools
- ✅ Список всех инструментов (greet, add, ttools)
- ✅ Возможность вызвать любой инструмент

## Troubleshooting

### ❌ Python не найден

```
Python не найден. Установите Python 3.7+
```

**Решение:**
```bash
# Проверьте, установлен ли Python
which python3

# Если нет, установите:
# macOS
brew install python3

# Ubuntu/Debian
sudo apt-get install python3

# Windows - скачайте с python.org
```

### ❌ fastmcp не установлен

```
ModuleNotFoundError: No module named 'fastmcp'
```

**Решение:**
```bash
pip3 install fastmcp

# Проверьте установку
pip3 show fastmcp
```

### ❌ mcp_python_client.py не найден

```
Python скрипт не найден
```

**Решение:**
```bash
# Убедитесь, что вы в корне проекта
pwd
# Должно быть: /Users/andreykozyrev/AiProjects/ai_challenge_2

# Проверьте наличие файла
ls -la mcp_python_client.py

# Если файла нет, он должен быть создан автоматически
```

### ❌ Connection refused

```
Python call_tool error: Connection refused
```

**Решение:**
```bash
# Убедитесь, что Python MCP сервер запущен
ps aux | grep "python.*mcp"

# Перезапустите сервер
python your_mcp_server.py
```

### ❌ JSON parse error

```
Не удалось распарсить ответ Python
```

**Решение:**
```bash
# Запустите Python клиент напрямую для диагностики
python3 mcp_python_client.py call_tool http://127.0.0.1:8000/mcp ttools "{}"

# Проверьте вывод и ошибки
```

## Проверочный чек-лист

Перед запуском убедитесь:

- [ ] Python 3.7+ установлен: `python3 --version`
- [ ] fastmcp установлен: `pip3 show fastmcp`
- [ ] mcp_python_client.py существует в корне проекта
- [ ] Python MCP сервер запущен на порту 8000
- [ ] Kotlin приложение скомпилировано: `./gradlew compileKotlin`

## Быстрая проверка работоспособности

```bash
# 1. Запустите Python сервер
python your_mcp_server.py &

# 2. Подождите 2 секунды
sleep 2

# 3. Протестируйте Python клиент
python3 mcp_python_client.py call_tool http://127.0.0.1:8000/mcp ttools "{}"

# Если видите JSON с "success": true - всё работает!
```

## Преимущества этого подхода

✅ **Гарантированная совместимость** - используется та же библиотека FastMCP
✅ **Простота отладки** - можно тестировать Python клиент отдельно
✅ **Надёжность** - проверенная библиотека, никаких проблем с HTTP заголовками
✅ **Гибкость** - легко модифицировать Python код при необходимости

## Файлы

- `mcp_python_client.py` - Python скрипт (корень проекта)
- `src/main/kotlin/mcp/MCPPythonClient.kt` - Kotlin обёртка
- `src/main/kotlin/ui/MCPToolsUI.kt` - интеграция в UI

## Дополнительная документация

- `MCP_PYTHON_CLIENT_SOLUTION.md` - полное описание решения
- `MCP_LOCAL_CLIENT_README.md` - документация оригинального HTTP клиента

---

Готово! Теперь можно работать с локальным FastMCP сервером! 🎉
