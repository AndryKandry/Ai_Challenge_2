# Сравнение токенов с поддержкой Yandex GPT

## Новые возможности

### 1. Поддержка Yandex GPT
Добавлена полная поддержка Yandex GPT в систему сравнения токенов:
- Автоматический подсчёт токенов для Yandex GPT моделей
- Поддержка лимита контекста 8000 токенов
- Стратегии обрезки и сжатия для Yandex GPT

### 2. Графический интерфейс
Создан полноценный UI для сравнения обработки токенов:
- Визуальный выбор провайдера (Claude, HuggingFace, Yandex GPT)
- Удобный ввод учётных данных
- Отображение результатов в реальном времени
- Детальные метрики и рекомендации

## Быстрый старт

### Способ 1: Через UI (рекомендуется)

1. Запустите приложение:
```bash
./gradlew run
```

2. В главном окне перейдите на вкладку **"🔢 Анализ токенов"**

3. Выберите **Yandex GPT**

4. Заполните поля:
   - **API Key**: ваш API ключ от Yandex Cloud
   - **Folder ID**: ID вашего каталога в Yandex Cloud

5. Нажмите **"▶ Запустить сравнение"**

6. Дождитесь завершения и изучите отчёт

### Способ 2: Через командную строку

```bash
./gradlew run --args="yandex YOUR_API_KEY:YOUR_FOLDER_ID"
```

Отчёты сохраняются в директории `reports/`:
- `comparison_yandex_<timestamp>.json` - JSON отчёт
- `comparison_yandex_<timestamp>.txt` - текстовый отчёт

### Способ 3: Из кода

```kotlin
import org.kozyrev.claude.tokenization.ComparisonRunner

val runner = ComparisonRunner()
runner.runComparison(
    clientType = "yandex",
    apiKey = "YOUR_API_KEY:YOUR_FOLDER_ID",
    outputDir = "reports"
)
```

## Что тестируется

Система автоматически выполняет 3 теста:

### 1. Короткий запрос (SHORT)
- **Размер**: < 50 токенов
- **Пример**: "What is the capital of France?"
- **Цель**: Проверка базовой обработки

### 2. Длинный запрос (LONG)
- **Размер**: 500-1500 токенов
- **Содержание**: Комплексный запрос с множественными аспектами
- **Цель**: Проверка обработки типичных запросов

### 3. Превышение лимита (EXCEEDING_LIMIT)
- **Размер**: > максимального контекста модели
- **Цель**: Проверка автоматической обрезки/сжатия

## Метрики в отчёте

Для каждого теста собираются:

### Токены
- **Входные токены**: количество токенов в запросе
- **Выходные токены**: количество токенов в ответе
- **Всего токенов**: общее количество
- **Трансформация**: применённая стратегия обрезки/сжатия

### Качество
- **Статус**: success/truncated/error
- **Полнота ответа**: оценка от 0% до 100%
- **Длина ответа**: в символах
- **Время ответа**: в миллисекундах

### Сводка
- Общее количество тестов
- Успешные/усечённые/ошибочные
- Средняя полнота ответов
- Среднее время ответа
- Рекомендации по оптимизации

## Примеры использования

### Сравнение всех трёх провайдеров

```bash
# Claude
./gradlew run --args="claude YOUR_CLAUDE_KEY"

# HuggingFace
./gradlew run --args="huggingface YOUR_HF_KEY"

# Yandex GPT
./gradlew run --args="yandex YOUR_YANDEX_KEY:YOUR_FOLDER_ID"
```

### Анализ отчётов

Отчёты сохраняются в JSON формате и могут быть проанализированы программно:

```kotlin
import kotlinx.serialization.json.Json
import org.kozyrev.claude.tokenization.ComparisonReport
import java.io.File

val jsonContent = File("reports/comparison_yandex_123456789.json").readText()
val report = Json.decodeFromString<ComparisonReport>(jsonContent)

println("Модель: ${report.modelName}")
println("Успешных тестов: ${report.summary.successCount}")
println("Средняя полнота: ${report.summary.averageCompletenessScore * 100}%")

report.testResults.forEach { result ->
    println("${result.promptType}: ${result.promptTokens} -> ${result.responseTokens} токенов")
}
```

## Настройка для Yandex GPT

### Получение API ключа

1. Перейдите в [Yandex Cloud Console](https://console.cloud.yandex.ru)
2. Выберите ваш каталог
3. Перейдите в **API ключи**
4. Создайте новый API ключ
5. Скопируйте ключ и ID каталога

### Переменные окружения (опционально)

Можно настроить переменные окружения для автоматического подключения:

```bash
export YANDEX_API_KEY="your-api-key"
export YANDEX_FOLDER_ID="your-folder-id"
```

После этого можно просто запустить приложение без ввода ключей.

## Технические детали

### Модели Yandex GPT

Поддерживаемые модели:
- **yandexgpt-lite** (по умолчанию) - быстрая модель для простых задач
- **yandexgpt** - стандартная модель
- **yandexgpt-32k** - модель с расширенным контекстом

### Лимиты

| Параметр | Значение |
|----------|----------|
| Максимальный контекст | 8000 токенов |
| Ожидаемые токены ответа | 1024 токена |
| Токен-бюджет для запроса | 6976 токенов |
| Таймаут запроса | 60 секунд |

### Стратегия обрезки

Для Yandex GPT используется `TruncationStrategy.KEEP_START`:
- При превышении лимита сохраняется начало текста
- Обрезка происходит автоматически и прозрачно
- Логируется исходное и итоговое количество токенов

## Устранение проблем

### Ошибка аутентификации

```
Error: Yandex GPT API error: Invalid API key
```

**Решение**: Проверьте правильность API ключа и Folder ID

### Превышение квоты

```
Error: Rate limit exceeded
```

**Решение**: Подождите несколько минут перед повторной попыткой

### Таймаут

```
Error: Request timeout
```

**Решение**: Увеличьте таймаут или проверьте сетевое соединение

## Дополнительная информация

- Полная документация: [TOKEN_MANAGEMENT_README.md](TOKEN_MANAGEMENT_README.md)
- Настройка Yandex GPT: [YANDEX_GPT_SETUP.md](YANDEX_GPT_SETUP.md)
- Примеры использования: [AGENTS_README.md](AGENTS_README.md)
