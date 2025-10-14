#!/bin/bash
# Скрипт для установки зависимостей Python MCP клиента

echo "🔧 Установка зависимостей для Python MCP клиента..."
echo ""

# Проверяем Python
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 не найден"
    echo "   Установите Python 3.7 или выше:"
    echo "   - macOS: brew install python3"
    echo "   - Ubuntu/Debian: sudo apt-get install python3"
    echo "   - Windows: скачайте с https://python.org"
    exit 1
fi

PYTHON_VERSION=$(python3 --version)
echo "✅ Python найден: $PYTHON_VERSION"

# Проверяем pip
if ! python3 -m pip --version &> /dev/null; then
    echo "❌ pip не найден"
    echo "   Установите pip:"
    echo "   - python3 -m ensurepip --upgrade"
    exit 1
fi

echo "✅ pip найден"
echo ""

# Устанавливаем fastmcp
echo "📦 Установка fastmcp..."
if python3 -m pip install fastmcp --quiet; then
    echo "✅ fastmcp успешно установлен"
else
    echo "❌ Не удалось установить fastmcp"
    echo "   Попробуйте вручную: pip3 install fastmcp"
    exit 1
fi

echo ""

# Проверяем установку
echo "🔍 Проверка установки..."
if python3 -c "import fastmcp; print(f'FastMCP версия: {fastmcp.__version__}')" 2>/dev/null; then
    echo "✅ fastmcp работает корректно"
else
    echo "⚠️ fastmcp установлен, но не импортируется"
    exit 1
fi

echo ""
echo "🎉 Готово! Можно запускать приложение."
echo ""
echo "Следующие шаги:"
echo "1. Запустите Python MCP сервер: python your_mcp_server.py"
echo "2. Запустите Kotlin приложение: ./gradlew run"
echo "3. Подключитесь к http://127.0.0.1:8000/mcp в UI"
