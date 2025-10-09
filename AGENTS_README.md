# 🤖 Система взаимодействия AI Агентов

## Описание

Реализована система из двух AI агентов с взаимодействием:

### Агент 1: WriterAgent (Писатель)
- **Задача**: Генерирует качественные статьи на заданную тему
- **Модель данных**: `Article` - структурированная статья с заголовком, содержанием, разделами
- **Температура**: 0.7 (более креативный режим)
- **Выходной формат**: JSON, который затем форматируется в читаемый текст

### Агент 2: ReviewerAgent (Ревьюер)
- **Задача**: Проверяет качество статьи от первого агента
- **Модель данных**: `ArticleReview` - детальная оценка с баллами и рекомендациями
- **Температура**: 0.3 (более строгий и объективный режим)
- **Входной формат**: Получает отформатированную статью от WriterAgent
- **Критерии оценки**:
  - Качество содержания (информативность, точность, глубина)
  - Структура (логичность, связность, разделение)
  - Читаемость (ясность, стиль, доступность)

## Архитектура

```
┌─────────────────────┐
│  AgentOrchestrator  │  ← Управляет взаимодействием
└──────────┬──────────┘
           │
           ├──► WriterAgent ──┐
           │                  │ (форматированный текст)
           │                  ▼
           └──► ReviewerAgent ◄──┘
```

### Основные классы

#### 1. WriterAgent
```kotlin
class WriterAgent(private val aiClient: AIClient)

// Модель данных
data class Article(
    val title: String,
    val topic: String,
    val content: String,
    val wordCount: Int,
    val sections: List<String>
)

// Основной метод
suspend fun writeArticle(topic: String, temperature: Double = 0.7): Article

// Форматирование для передачи следующему агенту
fun formatArticleForReview(article: Article): String
```

#### 2. ReviewerAgent
```kotlin
class ReviewerAgent(private val aiClient: AIClient)

// Модель данных
data class ArticleReview(
    val overallScore: Int,        // 1-10
    val qualityScore: Int,         // 1-10
    val structureScore: Int,       // 1-10
    val readabilityScore: Int,     // 1-10
    val strengths: List<String>,
    val weaknesses: List<String>,
    val recommendations: List<String>,
    val verdict: String
)

// Основной метод
suspend fun reviewArticle(formattedArticle: String, temperature: Double = 0.3): ArticleReview

// Форматирование результатов
fun formatReview(review: ArticleReview): String
```

#### 3. AgentOrchestrator
```kotlin
class AgentOrchestrator(private val aiClient: AIClient)

// Результат взаимодействия
data class OrchestrationResult(
    val topic: String,
    val writerResult: AgentTaskResult,
    val article: Article?,
    val reviewerResult: AgentTaskResult,
    val review: ArticleReview?,
    val success: Boolean
)

// Запуск полного цикла
suspend fun runAgentWorkflow(topic: String): OrchestrationResult

// Коллбэк для отслеживания прогресса
var onProgressUpdate: ((String, TaskStatus) -> Unit)?
```

## Визуализация

Создан отдельный UI компонент `AgentInteractionScreen` с:
- **Выбором AI модели** (Claude или Yandex GPT)
- Полем ввода темы статьи
- Индикаторами статуса каждого агента
- Визуализацией процесса взаимодействия
- Отображением результатов работы обоих агентов

### Интерфейс
- **Вкладка**: "🤖 Агенты" в главном окне приложения
- **Выбор модели**:
  - 🔵 Claude (всегда доступен)
  - 🟡 Yandex GPT (требует настройки API ключей)
- **Статусы**:
  - Ожидает (серый)
  - Работает (желтый)
  - Готово (зеленый)
  - Ошибка (красный)

## Использование

### Через UI
1. Запустите приложение
2. Перейдите на вкладку "🤖 Агенты"
3. **Выберите AI модель** (Claude или Yandex GPT)
4. Введите тему статьи (например: "Искусственный интеллект в медицине")
5. Нажмите "🚀 Запустить агентов"
6. Наблюдайте за процессом в реальном времени
7. Получите результаты от обоих агентов

**Примечание**: Выбор модели влияет на оба агента - они будут использовать одну и ту же модель для работы.

### Программно
```kotlin
val orchestrator = AgentOrchestrator(aiClient)

// Настройка коллбэка для отслеживания
orchestrator.onProgressUpdate = { message, status ->
    println("$message - $status")
}

// Запуск
val result = orchestrator.runAgentWorkflow("Квантовые компьютеры")

// Получение результатов
println("Статья: ${result.article}")
println("Оценка: ${result.review}")
println("Успешно: ${result.success}")
```

## Пример работы

**Тема**: "Квантовые компьютеры"

**Шаг 1 - WriterAgent**:
```
=== СТАТЬЯ ДЛЯ ПРОВЕРКИ ===

Название: Квантовые компьютеры: революция в вычислениях
Тема: Квантовые компьютеры
Количество слов: 450
Разделы: Введение, Принцип работы, Применение, Вызовы, Будущее

--- СОДЕРЖАНИЕ ---
[Полный текст статьи...]
```

**Шаг 2 - ReviewerAgent**:
```
╔════════════════════════════════════════════╗
║       РЕЗУЛЬТАТЫ ПРОВЕРКИ СТАТЬИ          ║
╚════════════════════════════════════════════╝

📊 ОЦЕНКИ:
   • Общая оценка: 8/10 👍 Хорошо
   • Качество содержания: 9/10
   • Структура: 8/10
   • Читаемость: 8/10

✅ СИЛЬНЫЕ СТОРОНЫ:
   1. Хорошо структурированная статья
   2. Доступный язык
   ...

⚠️ СЛАБЫЕ СТОРОНЫ:
   1. Недостаточно конкретных примеров
   ...

💡 РЕКОМЕНДАЦИИ:
   1. Добавить примеры реальных квантовых компьютеров
   ...

📝 ВЕРДИКТ:
   Качественная обзорная статья...
```

## Технические детали

### Формат обмена данными
- Агенты обмениваются данными через **читаемый текстовый формат**
- WriterAgent форматирует статью с четкими разделителями
- ReviewerAgent получает структурированный текст для анализа

### Обработка ошибок
- Если WriterAgent падает - процесс останавливается
- Если ReviewerAgent падает - возвращается частичный результат
- Все ошибки логируются и отображаются в UI

### Производительность
- Асинхронное выполнение через корутины
- Отслеживание прогресса в реальном времени
- Примерное время выполнения: 10-30 секунд на полный цикл

## Файлы проекта

- `src/main/kotlin/agents/WriterAgent.kt` - Агент-писатель
- `src/main/kotlin/agents/ReviewerAgent.kt` - Агент-ревьюер
- `src/main/kotlin/agents/AgentOrchestrator.kt` - Оркестратор
- `src/main/kotlin/ui/AgentInteractionUI.kt` - UI компонент
- `src/main/kotlin/Application.kt` - Интеграция в приложение

## Требования

- Kotlin 1.9+
- Claude AI API ключ (через ANTHROPIC_API_KEY)
- Jetpack Compose для Desktop
- kotlinx.serialization для работы с JSON

## Заключение

Система демонстрирует полноценное взаимодействие двух AI агентов:
1. ✅ Два независимых класса с собственными моделями данных
2. ✅ Настроенное взаимодействие через читаемый формат
3. ✅ Агент 2 проверяет работу Агента 1
4. ✅ Визуализация процесса в UI
