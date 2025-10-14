# Быстрый старт

## Запуск приложения

### Вариант 1: Только Claude (минимальная настройка)

```bash
export CLAUDE_API_KEY="ваш-ключ-claude"
./gradlew run
```

### Вариант 2: Claude + Yandex GPT

```bash
export CLAUDE_API_KEY="ваш-ключ-claude"
export YANDEX_API_KEY="ваш-ключ-yandex"
export YANDEX_FOLDER_ID="ваш-folder-id"
./gradlew run
```

## Что делает приложение

Это GUI чат-приложение на Kotlin с Compose Desktop, которое позволяет общаться с AI моделями:
- **Claude AI** (Anthropic)
- **Yandex GPT** (опционально)

## Основные возможности

✅ Переключение между Claude и Yandex GPT одним кликом
✅ Регулировка температуры (креативность ответов)
✅ История диалога
✅ Метрики использования (токены, время, стоимость)
✅ Чистый текстовый формат (без JSON)

## Интерфейс

### Панель выбора модели
- 🔵 **Claude** - использовать Claude AI
- 🟡 **Yandex GPT** - использовать Yandex GPT (если настроен)

### Управление температурой
- **0.0** - строгие, предсказуемые ответы
- **0.5** - сбалансированные ответы (рекомендуется)
- **1.0** - креативные, разнообразные ответы

### Кнопки управления
- **Отправить** - отправить сообщение
- **Очистить историю** - начать новый диалог

## Горячие клавиши

- `Enter` - отправить сообщение
- `Shift+Enter` - новая строка в сообщении

## Получение API ключей

### Claude AI
1. Зарегистрируйтесь: https://console.anthropic.com/
2. Создайте API ключ
3. Установите: `export CLAUDE_API_KEY="ключ"`

### Yandex GPT (опционально)
1. Зарегистрируйтесь: https://cloud.yandex.ru/
2. Создайте каталог (Folder)
3. Создайте сервисный аккаунт
4. Назначьте роль: `ai.languageModels.user`
5. Создайте API ключ
6. Установите:
   ```bash
   export YANDEX_API_KEY="ключ"
   export YANDEX_FOLDER_ID="id-каталога"
   ```

## Решение проблем

### Yandex GPT не работает?

Запустите с логированием:
```bash
./gradlew run 2>&1 | tee yandex_debug.log
```

### Приложение не запускается?

Проверьте:
1. Java 17+ установлена: `java -version`
2. API ключ Claude установлен: `echo $CLAUDE_API_KEY`
3. Логи Gradle: `./gradlew run --stacktrace`

## Структура проекта

```
ai_challenge_2/
├── src/main/kotlin/
│   ├── Application.kt          # Точка входа
│   ├── claude/
│   │   ├── ChatManager.kt      # Менеджер диалога
│   │   ├── ClaudeClient.kt     # Клиент Claude
│   │   ├── YandexGPTClient.kt  # Клиент Yandex GPT
│   │   └── ...
│   └── ui/
│       ├── ChatUI.kt           # Основной UI
│       └── ...
├── YANDEX_GPT_SETUP.md        # Настройка Yandex GPT
└── README_QUICK_START.md      # Этот файл
```

## Примеры использования

### Обычное общение
```
Вы: Привет! Расскажи о себе
AI: Здравствуйте! Я дружелюбный AI помощник...
```

### Переключение моделей
1. Задайте вопрос Claude
2. Нажмите кнопку "🟡 Yandex GPT"
3. Задайте тот же вопрос Yandex GPT
4. Сравните ответы!

### Регулировка температуры
- Установите 0.0 для точных фактов
- Установите 1.0 для творческих идей

## Полезные файлы

- [YANDEX_GPT_SETUP.md](YANDEX_GPT_SETUP.md) - Подробная настройка Yandex GPT
