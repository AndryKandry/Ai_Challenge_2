# Исправление: fastmcp не установлен

## Проблема

При попытке подключения к локальному серверу возникла ошибка:

```
ModuleNotFoundError: No module named 'fastmcp'
```

## Решение

Создано **3 способа** установки fastmcp.

## Способ 1: Автоматический скрипт (рекомендуется)

Самый простой способ:

```bash
# Запустите скрипт установки
./setup_mcp_client.sh
```

Скрипт автоматически:
1. ✅ Проверит наличие Python 3
2. ✅ Проверит наличие pip
3. ✅ Установит fastmcp
4. ✅ Проверит корректность установки

**Ожидаемый вывод:**
```
🔧 Установка зависимостей для Python MCP клиента...

✅ Python найден: Python 3.11.5
✅ pip найден

📦 Установка fastmcp...
✅ fastmcp успешно установлен

🔍 Проверка установки...
✅ fastmcp работает корректно

🎉 Готово! Можно запускать приложение.
```

## Способ 2: Автоматическая установка при запуске

Python скрипт теперь **автоматически** пытается установить fastmcp при первом запуске.

При следующем подключении в UI вы увидите:

```
⚠️ Python stderr: {"error": "fastmcp не установлен. Попытка автоматической установки..."}
⚠️ Python stderr: {"success": true, "message": "fastmcp успешно установлен"}
```

После этого подключение должно пройти успешно.

## Способ 3: Ручная установка

Если автоматические способы не сработали:

```bash
# Установка через pip
pip install fastmcp

# Или через pip3
pip3 install fastmcp

# Проверка установки
python3 -c "import fastmcp; print('OK')"
```

## Проверка после установки

### 1. Прямой тест Python клиента

```bash
python3 mcp_python_client.py call_tool http://127.0.0.1:8000/mcp ttools "{}"
```

**Ожидаемый результат:**
```json
{
  "success": true,
  "content": [
    {
      "type": "text",
      "text": "[{\"name\": \"greet\", ...}]"
    }
  ]
}
```

### 2. Тест через UI

1. Запустите Python MCP сервер: `python your_mcp_server.py`
2. Запустите Kotlin приложение: `./gradlew run`
3. Откройте вкладку "MCP Tools"
4. Подключитесь к `http://127.0.0.1:8000/mcp`

**Ожидаемые логи:**
```
🔄 Локальный сервер обнаружен, используем Python FastMCP клиент...
🐍 Запуск Python клиента: python3 mcp_python_client.py initialize ...
📊 Python вывод: {"success":true,...}
✓ ttools вызван успешно
```

## Улучшения в коде

### 1. mcp_python_client.py

Добавлена автоматическая установка fastmcp:

```python
try:
    from fastmcp import Client
except ImportError:
    print("fastmcp не установлен. Попытка автоматической установки...", file=sys.stderr)
    subprocess.check_call([sys.executable, "-m", "pip", "install", "fastmcp", "--quiet"])
    from fastmcp import Client
```

### 2. MCPPythonClient.kt

Добавлена специфичная диагностика:

```kotlin
if (error.contains("No module named 'fastmcp'")) {
    println("💡 Подсказка: Установите fastmcp: pip install fastmcp")
    val errorMsg = "fastmcp не установлен. Установите командой: pip install fastmcp"
    return Result.failure(Exception(errorMsg))
}
```

### 3. setup_mcp_client.sh

Создан скрипт для удобной установки всех зависимостей.

## Troubleshooting

### Проблема: pip не найден

```bash
# Установите pip
python3 -m ensurepip --upgrade

# Или через package manager
# macOS
brew install python3

# Ubuntu/Debian
sudo apt-get install python3-pip
```

### Проблема: Permission denied

```bash
# Используйте --user flag
pip install --user fastmcp

# Или установите в виртуальное окружение
python3 -m venv venv
source venv/bin/activate
pip install fastmcp
```

### Проблема: Версия Python слишком старая

```bash
# Проверьте версию
python3 --version

# Должна быть 3.7 или выше
# Обновите Python если необходимо
```

### Проблема: fastmcp устанавливается, но не импортируется

```bash
# Убедитесь, что используете правильный Python
which python3

# Попробуйте явно указать путь к pip
python3 -m pip install fastmcp

# Проверьте, куда установлен пакет
python3 -m pip show fastmcp
```

## Пошаговая инструкция

### Для macOS/Linux:

```bash
# 1. Запустите скрипт установки
cd /Users/andreykozyrev/AiProjects/ai_challenge_2
./setup_mcp_client.sh

# 2. Запустите Python сервер
python your_mcp_server.py &

# 3. Протестируйте клиент
python3 mcp_python_client.py call_tool http://127.0.0.1:8000/mcp ttools "{}"

# 4. Если тест успешен, запустите приложение
./gradlew run
```

### Для Windows:

```powershell
# 1. Установите fastmcp
pip install fastmcp

# 2. Запустите Python сервер
python your_mcp_server.py

# 3. В новом терминале протестируйте клиент
python mcp_python_client.py call_tool http://127.0.0.1:8000/mcp ttools "{}"

# 4. Запустите приложение
gradlew.bat run
```

## Проверочный чек-лист

Перед запуском приложения убедитесь:

- [x] Python 3.7+ установлен: `python3 --version` ✓
- [x] pip установлен: `python3 -m pip --version` ✓
- [ ] **fastmcp установлен: `pip show fastmcp`** ← Сейчас здесь
- [ ] mcp_python_client.py существует в корне проекта
- [ ] Python MCP сервер запущен
- [ ] Код скомпилирован: `./gradlew compileKotlin` ✓

## Быстрая проверка

```bash
# Проверка в одну команду
python3 -c "import fastmcp; print('✅ fastmcp установлен')" || echo "❌ fastmcp не установлен"
```

## Файлы

- `setup_mcp_client.sh` - скрипт автоматической установки
- `mcp_python_client.py` - обновлён с автоустановкой
- `src/main/kotlin/mcp/MCPPythonClient.kt` - улучшена диагностика

## Заключение

После установки fastmcp любым из способов:

1. ✅ Python клиент будет работать корректно
2. ✅ Автоматическая установка сработает при следующем запуске
3. ✅ Подключение к локальному серверу пройдёт успешно

**Рекомендованный способ:** запустите `./setup_mcp_client.sh` для гарантированной установки всех зависимостей.
