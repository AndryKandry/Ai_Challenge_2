# Управление токенами и сравнение запросов

Этот модуль добавляет возможности подсчёта токенов, автоматической обрезки/сжатия текста и сравнения различных типов запросов к AI моделям.

## Возможности

### 1. Подсчёт токенов

Модуль предоставляет несколько токенизаторов для различных моделей:
- **ClaudeTokenizer** - для моделей Claude (приблизительная оценка)
- **GPTTokenizer** - для OpenAI-совместимых моделей
- **SimpleTokenizer** - универсальный fallback токенизатор

Токенизатор автоматически выбирается на основе имени модели.

### 2. Стратегии обрезки и сжатия

#### Truncation (Усечение)
Три режима усечения:
- `KEEP_START` - сохранить начало текста
- `KEEP_END` - сохранить конец текста
- `KEEP_BOTH` - сохранить начало и конец, с `...` посередине

#### Summarization (Суммаризация)
Две стратегии:
- **AI-суммаризация** - использует AI модель для генерации краткого содержания
- **Экстрактивная суммаризация** - выбирает наиболее важные предложения на основе эвристик

### 3. Сравнение запросов

Автоматический прогон трёх типов запросов:
- **SHORT** - короткий запрос (<50 токенов)
- **LONG** - длинный запрос (500-1500 токенов)
- **EXCEEDING_LIMIT** - запрос, превышающий лимит модели

## Использование

### UI интерфейс (рекомендуется)

Самый простой способ - через графический интерфейс:

1. Запустите приложение:
```bash
./gradlew run
```

2. Перейдите на вкладку **"🔢 Анализ токенов"**

3. Выберите провайдера AI:
   - **Claude** - модели Anthropic
   - **HuggingFace** - модели с HuggingFace
   - **Yandex GPT** - модели Yandex Cloud

4. Введите учётные данные:
   - **Claude**: API ключ
   - **HuggingFace**: API ключ
   - **Yandex GPT**: API ключ + Folder ID (два поля)

5. Нажмите **"▶ Запустить сравнение"**

6. Дождитесь завершения и просмотрите детальный отчёт с:
   - ✓ Сводкой по всем тестам (успешные/усечённые/ошибки)
   - 🔢 Метриками токенов для каждого типа запроса
   - 📊 Оценками качества ответов
   - 💡 Рекомендациями по оптимизации

### Базовое использование из кода

```kotlin
import org.kozyrev.claude.tokenization.*
import org.kozyrev.claude.*

// Создаём клиент
val client = ClaudeClient(ClaudeConfig(apiKey = "your-api-key"))

// Создаём конфигурацию токенов
val tokenConfig = TokenConfig(
    modelName = "claude-3-haiku-20240307",
    maxContextTokens = 4000,
    expectedResponseTokens = 1024,
    compressionStrategy = TruncationStrategy(
        TruncationStrategy.TruncationMode.KEEP_START
    )
)

// Оборачиваем клиент для работы с токенами
val tokenAwareClient = TokenAwareClient(client, tokenConfig)

// Отправляем запрос с автоматической обработкой токенов
val messages = listOf(Message(role = "user", content = "Your prompt"))
val (response, metrics) = tokenAwareClient.sendConversationWithEnhancedMetrics(messages)

println("Tokens used: ${metrics.totalTokens}")
println("Transformation: ${metrics.transformationApplied}")
```

### Запуск сравнения запросов

#### Из кода

```kotlin
import org.kozyrev.claude.tokenization.ComparisonRunner

val runner = ComparisonRunner()
runner.runComparison(
    clientType = "claude",  // или "huggingface", "yandex"
    apiKey = "your-api-key", // для Yandex: "apiKey:folderId"
    outputDir = "reports"
)
```

#### Из командной строки

```bash
# Для Claude
./gradlew run --args="claude YOUR_CLAUDE_API_KEY"

# Для HuggingFace
./gradlew run --args="huggingface YOUR_HF_API_KEY"

# Для Yandex GPT
./gradlew run --args="yandex YOUR_API_KEY:YOUR_FOLDER_ID"

# С указанием директории для отчётов
./gradlew run --args="claude YOUR_API_KEY custom_reports"
```

### Запуск тестов

```bash
# Запустить все тесты
./gradlew test

# Запустить только тесты токенизации
./gradlew test --tests "tokenization.*"

# Запустить конкретный тест
./gradlew test --tests "tokenization.TokenizerTest"
```

## Формат отчётов

### JSON отчёт

Сохраняется в файл `reports/comparison_<client>_<timestamp>.json`

```json
{
  "modelName": "claude-3-haiku-20240307",
  "maxContextTokens": 200000,
  "testResults": [
    {
      "promptType": "SHORT",
      "promptTokens": 15,
      "responseTokens": 25,
      "totalTokens": 40,
      "status": "success",
      "qualityMetrics": {
        "responseLength": 120,
        "isTruncated": false,
        "containsError": false,
        "completenessScore": 1.0
      },
      "transformationApplied": "none"
    }
  ],
  "summary": {
    "totalTests": 3,
    "successCount": 3,
    "truncatedCount": 0,
    "errorCount": 0,
    "averageCompletenessScore": 0.95,
    "averageResponseTimeMs": 1234,
    "recommendations": [
      "Все тесты прошли успешно!"
    ]
  }
}
```

### Текстовый отчёт

Сохраняется в файл `reports/comparison_<client>_<timestamp>.txt`

Содержит человекочитаемую сводку с:
- Информацией о модели и лимитах
- Детальными результатами для каждого типа запроса
- Метриками качества ответов
- Рекомендациями по оптимизации

## Конфигурация

### Модели и лимиты

Предустановленные конфигурации для популярных моделей:

| Модель | Max Context | Рекомендуемая стратегия |
|--------|-------------|-------------------------|
| claude-3-haiku | 200,000 | TruncationStrategy.KEEP_START |
| claude-3-sonnet | 200,000 | TruncationStrategy.KEEP_BOTH |
| llama-3.1-8b | 8,192 | ExtractiveSummarization |
| gpt-4 | 8,192 | SummarizationStrategy |

### Переключение токенизатора

```kotlin
// Автоматический выбор на основе модели
val tokenizer = TokenizerFactory.forModel("claude-3-haiku-20240307")

// Явное указание токенизатора
val tokenizer = TokenizerFactory.byName("claude")

// Использование собственного токенизатора
val customTokenizer = object : Tokenizer {
    override fun countTokens(text: String): Int {
        // Ваша реализация
    }
    // ... другие методы
}

val config = TokenConfig(
    modelName = "my-model",
    maxContextTokens = 4000,
    tokenizer = customTokenizer
)
```

### Выбор стратегии сжатия

```kotlin
// Truncation (быстро, детерминированно)
val truncation = TruncationStrategy(TruncationStrategy.TruncationMode.KEEP_BOTH)

// AI Summarization (медленнее, лучшее качество)
val summarization = SummarizationStrategy(
    client = client,
    compressionRatio = 0.5  // целевое сжатие 50%
)

// Extractive Summarization (компромисс)
val extractive = ExtractiveSummarizationStrategy()

// Применение в конфигурации
val config = TokenConfig(
    modelName = "claude-3-haiku-20240307",
    maxContextTokens = 200000,
    compressionStrategy = truncation  // или summarization, или extractive
)
```

## Структура модуля

```
src/main/kotlin/claude/tokenization/
├── Tokenizer.kt           # Интерфейсы и реализации токенизаторов
├── TextCompression.kt     # Стратегии обрезки и сжатия
├── TokenAwareClient.kt    # Обёртка клиента с поддержкой токенов
├── PromptComparison.kt    # Система сравнения запросов
└── ComparisonRunner.kt    # Entry point для запуска сравнений

src/test/kotlin/tokenization/
├── TokenizerTest.kt       # Тесты токенизаторов
└── TextCompressionTest.kt # Тесты стратегий сжатия
```

## Логирование

Все операции логируются с подробной информацией:

```
Original prompt tokens: 5000
Token budget: 199000
Model: claude-3-haiku-20240307

Transformation: truncation (Truncation_KEEP_START)
Original tokens: 5000
Processed tokens: 4500
Reduction: 500 tokens (10%)
```

## Примеры использования

### Пример 1: Проверка токенов перед отправкой

```kotlin
val tokenAwareClient = TokenAwareClient(client, config)
val text = "Your very long text..."

val tokenCount = tokenAwareClient.countTokens(text)
println("This text contains: $tokenCount tokens")

if (tokenCount > config.getTokenBudget()) {
    println("Text exceeds budget, will be compressed")
}
```

### Пример 2: Ручная обработка с выбором стратегии

```kotlin
val tokenizer = TokenizerFactory.forModel("claude-3-haiku-20240307")
val strategy = TruncationStrategy(TruncationStrategy.TruncationMode.KEEP_BOTH)

val result = strategy.process(
    text = longText,
    tokenizer = tokenizer,
    targetTokens = 1000
)

println(result.toLogString())
println("Processed text: ${result.processedText}")
```

### Пример 3: Сравнение нескольких моделей

```kotlin
val models = listOf("claude", "huggingface")
val runner = ComparisonRunner()

models.forEach { model ->
    runner.runComparison(
        clientType = model,
        apiKey = getApiKey(model),
        outputDir = "reports/$model"
    )
}
```

## Устранение проблем

### Ошибка: "Prompt exceeds token budget"

Это означает, что даже после сжатия запрос превышает лимит. Решения:
1. Увеличьте `compressionRatio` для более агрессивного сжатия
2. Используйте `SummarizationStrategy` вместо `TruncationStrategy`
3. Разбейте запрос на несколько частей

### Ошибка: "Could not apply summarization"

AI-суммаризация может не сработать из-за проблем с API. Модуль автоматически переключится на truncation как fallback.

### Неточный подсчёт токенов

Для максимальной точности используйте официальную библиотеку токенизации вашей модели. Текущая реализация предоставляет приблизительные оценки.

## Дополнительная информация

Для получения дополнительной помощи:
- Изучите примеры в `src/main/kotlin/claude/tokenization/ComparisonRunner.kt`
- Посмотрите тесты в `src/test/kotlin/tokenization/`
- Обратитесь к документации API вашей модели для точных лимитов токенов
