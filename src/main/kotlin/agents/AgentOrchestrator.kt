package org.kozyrev.agents

import org.kozyrev.claude.AIClient
import kotlinx.serialization.Serializable

/**
 * Статус выполнения задачи
 */
enum class TaskStatus {
    PENDING,    // Ожидает выполнения
    RUNNING,    // Выполняется
    COMPLETED,  // Завершена успешно
    FAILED      // Завершена с ошибкой
}

/**
 * Результат работы агента
 */
@Serializable
data class AgentTaskResult(
    val agentName: String,
    val status: String,
    val output: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Полный результат взаимодействия агентов
 */
data class OrchestrationResult(
    val topic: String,
    val writerResult: AgentTaskResult,
    val article: Article?,
    val reviewerResult: AgentTaskResult,
    val review: ArticleReview?,
    val success: Boolean
)

/**
 * Оркестратор для управления взаимодействием агентов
 */
class AgentOrchestrator(private val aiClient: AIClient) {

    private val writerAgent = WriterAgent(aiClient)
    private val reviewerAgent = ReviewerAgent(aiClient)

    // Коллбэк для отслеживания прогресса
    var onProgressUpdate: ((String, TaskStatus) -> Unit)? = null

    /**
     * Запускает полный цикл: Агент 1 пишет статью -> Агент 2 проверяет
     */
    suspend fun runAgentWorkflow(topic: String): OrchestrationResult {
        var article: Article? = null
        var review: ArticleReview? = null
        var writerResult: AgentTaskResult
        var reviewerResult: AgentTaskResult

        try {
            // ШАГ 1: Агент-писатель генерирует статью
            onProgressUpdate?.invoke("Агент-Писатель начал работу...", TaskStatus.RUNNING)

            article = writerAgent.writeArticle(topic, temperature = 0.7)

            val formattedArticle = writerAgent.formatArticleForReview(article)

            writerResult = AgentTaskResult(
                agentName = "WriterAgent",
                status = "COMPLETED",
                output = formattedArticle
            )

            onProgressUpdate?.invoke("Агент-Писатель завершил работу", TaskStatus.COMPLETED)

            // ШАГ 2: Агент-ревьюер проверяет статью
            onProgressUpdate?.invoke("Агент-Ревьюер начал проверку...", TaskStatus.RUNNING)

            review = reviewerAgent.reviewArticle(formattedArticle, temperature = 0.3)

            val formattedReview = reviewerAgent.formatReview(review)

            reviewerResult = AgentTaskResult(
                agentName = "ReviewerAgent",
                status = "COMPLETED",
                output = formattedReview
            )

            onProgressUpdate?.invoke("Агент-Ревьюер завершил проверку", TaskStatus.COMPLETED)

            return OrchestrationResult(
                topic = topic,
                writerResult = writerResult,
                article = article,
                reviewerResult = reviewerResult,
                review = review,
                success = true
            )

        } catch (e: Exception) {
            // В случае ошибки возвращаем частичный результат
            writerResult = if (article == null) {
                AgentTaskResult(
                    agentName = "WriterAgent",
                    status = "FAILED",
                    output = "Ошибка: ${e.message}"
                )
            } else {
                AgentTaskResult(
                    agentName = "WriterAgent",
                    status = "COMPLETED",
                    output = writerAgent.formatArticleForReview(article)
                )
            }

            reviewerResult = AgentTaskResult(
                agentName = "ReviewerAgent",
                status = "FAILED",
                output = "Ошибка: ${e.message}"
            )

            onProgressUpdate?.invoke("Ошибка при выполнении", TaskStatus.FAILED)

            return OrchestrationResult(
                topic = topic,
                writerResult = writerResult,
                article = article,
                reviewerResult = reviewerResult,
                review = review,
                success = false
            )
        }
    }

    /**
     * Форматирует итоговый результат для отображения
     */
    fun formatResult(result: OrchestrationResult): String {
        return buildString {
            appendLine("╔═══════════════════════════════════════════════════════╗")
            appendLine("║          РЕЗУЛЬТАТ РАБОТЫ АГЕНТОВ                    ║")
            appendLine("╚═══════════════════════════════════════════════════════╝")
            appendLine()
            appendLine("📋 Тема: ${result.topic}")
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🤖 АГЕНТ 1: ПИСАТЕЛЬ")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Статус: ${result.writerResult.status}")
            appendLine()
            appendLine(result.writerResult.output)
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🤖 АГЕНТ 2: РЕВЬЮЕР")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Статус: ${result.reviewerResult.status}")
            appendLine()
            appendLine(result.reviewerResult.output)
            appendLine()

            if (result.success) {
                appendLine("✅ Взаимодействие агентов завершено успешно!")
            } else {
                appendLine("❌ Возникли ошибки при выполнении")
            }
        }
    }
}
