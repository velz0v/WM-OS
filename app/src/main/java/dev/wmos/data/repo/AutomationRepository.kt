package dev.wmos.data.repo

import dev.wmos.data.db.WmDatabase
import dev.wmos.data.db.entity.AutomationActionValues
import dev.wmos.data.db.entity.AutomationRuleEntity
import dev.wmos.data.db.entity.AutomationRunStatusValues
import dev.wmos.data.db.entity.KnowledgeTypeValues
import dev.wmos.data.db.entity.TaskStatusValues
import dev.wmos.domain.automation.AutomationActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class AutomationRun(
    val status: String,
    val message: String,
    val createdId: String? = null,
)

/**
 * Autopilot: user-defined rules that perform a small, allowed set of actions.
 *
 * Safety model:
 *  * only the three whitelisted actions exist — no shell, no file paths, no network;
 *  * configs are validated before execution, and an invalid rule fails loudly instead of guessing;
 *  * a mutating run marks the rule with its result and writes an action-log entry;
 *  * repeated runs within [DUPLICATE_GUARD_MS] are skipped, so a double tap cannot create twins.
 */
class AutomationRepository(
    private val db: WmDatabase,
    private val brain: BrainRepository,
    private val tasks: TaskRepository,
    private val reminders: ReminderRepository,
    private val actionLog: ActionLogRepository,
) {
    private val dao = db.automationDao()

    fun observeAll(): Flow<List<AutomationRuleEntity>> = dao.observeAll()

    suspend fun rule(id: String): AutomationRuleEntity? = dao.getById(id)

    suspend fun save(
        id: String?,
        name: String,
        actionType: String,
        configJson: String,
        requiresConfirmation: Boolean,
    ): Result<String> {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return Result.failure(IllegalArgumentException("Укажите название сценария"))
        if (cleanName.length > 120) return Result.failure(IllegalArgumentException("Название слишком длинное"))
        AutomationActions.validate(actionType, configJson).onFailure { return Result.failure(it) }
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.getById(it) }
        val entity = AutomationRuleEntity(
            id = existing?.id ?: id ?: UUID.randomUUID().toString(),
            name = cleanName,
            triggerType = existing?.triggerType ?: dev.wmos.data.db.entity.AutomationTriggerValues.MANUAL,
            actionType = actionType,
            configJson = configJson,
            enabled = existing?.enabled ?: true,
            requiresConfirmation = requiresConfirmation,
            lastRunAt = existing?.lastRunAt,
            lastRunStatus = existing?.lastRunStatus,
            lastRunMessage = existing?.lastRunMessage,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsert(entity)
        return Result.success(entity.id)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) =
        dao.setEnabled(id, enabled, System.currentTimeMillis())

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun run(id: String, now: Long = System.currentTimeMillis()): AutomationRun {
        val rule = dao.getById(id)
            ?: return AutomationRun(AutomationRunStatusValues.FAILED, "Сценарий не найден")
        if (!rule.enabled) {
            dao.markRun(id, now, AutomationRunStatusValues.SKIPPED, "Сценарий выключен")
            return AutomationRun(AutomationRunStatusValues.SKIPPED, "Сценарий выключен")
        }
        if (rule.lastRunAt != null && now - rule.lastRunAt < DUPLICATE_GUARD_MS) {
            dao.markRun(id, now, AutomationRunStatusValues.SKIPPED, "Повторный запуск слишком быстро")
            return AutomationRun(
                AutomationRunStatusValues.SKIPPED,
                "Сценарий уже запускался только что — повтор пропущен",
            )
        }

        val validated = AutomationActions.validate(rule.actionType, rule.configJson)
            .getOrElse { error ->
                val message = error.message ?: "Некорректная конфигурация"
                dao.markRun(id, now, AutomationRunStatusValues.FAILED, message)
                actionLog.log("autopilot", rule.actionType, AutomationRunStatusValues.FAILED, message)
                return AutomationRun(AutomationRunStatusValues.FAILED, message)
            }

        val result = when (validated) {
            is AutomationActions.Validated.Task -> createTask(validated.config)
            is AutomationActions.Validated.Note -> createNote(validated.config)
            AutomationActions.Validated.DailySummary -> createDailySummary(now)
        }

        dao.markRun(id, now, result.status, result.message.take(400))
        actionLog.log(
            actor = "autopilot",
            tool = rule.actionType,
            status = result.status,
            summary = "${rule.name}: ${result.message}".take(400),
            targetIds = listOfNotNull(result.createdId),
        )
        return result
    }

    private suspend fun createTask(config: AutomationActions.TaskConfig): AutomationRun {
        val dueAt = config.dueInDays?.let { days ->
            LocalDate.now().plusDays(days.toLong())
                .atTime(18, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
        return tasks.createTask(title = config.title, dueAt = dueAt).fold(
            onSuccess = { id ->
                val stored = tasks.task(id)
                if (stored == null) {
                    AutomationRun(AutomationRunStatusValues.FAILED, "Задача не найдена после создания")
                } else {
                    AutomationRun(
                        AutomationRunStatusValues.SUCCESS,
                        "Создана задача «${stored.title}»",
                        createdId = id,
                    )
                }
            },
            onFailure = {
                AutomationRun(AutomationRunStatusValues.FAILED, it.message ?: "Не удалось создать задачу")
            },
        )
    }

    private suspend fun createNote(config: AutomationActions.NoteConfig): AutomationRun {
        val id = brain.createItem(
            type = KnowledgeTypeValues.NOTE,
            title = config.title,
            content = config.body,
        )
        val stored = brain.item(id)
        return if (stored == null) {
            AutomationRun(AutomationRunStatusValues.FAILED, "Заметка не найдена после создания")
        } else {
            AutomationRun(
                AutomationRunStatusValues.SUCCESS,
                "Создана заметка «${stored.title.ifBlank { "Без названия" }}»",
                createdId = id,
            )
        }
    }

    /**
     * Builds the summary from real rows only. If there is nothing to report, it says so instead
     * of writing an empty note.
     */
    private suspend fun createDailySummary(now: Long): AutomationRun {
        val openTasks = tasks.observeOpen().first()
        val activeReminders = reminders.observeActive().first()
        if (openTasks.isEmpty() && activeReminders.isEmpty()) {
            return AutomationRun(
                AutomationRunStatusValues.SKIPPED,
                "Нечего сводить: нет открытых задач и активных напоминаний",
            )
        }
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru", "RU")))
        val overdue = openTasks.filter { it.dueAt != null && it.dueAt < now }
        val today = openTasks.filter {
            it.dueAt != null && it.dueAt >= now && it.dueAt - now < 24L * 60 * 60 * 1000
        }
        val body = buildString {
            appendLine("Сводка на $date")
            appendLine()
            appendLine("Открытых задач: ${openTasks.size}, просрочено: ${overdue.size}, на сегодня: ${today.size}.")
            if (overdue.isNotEmpty()) {
                appendLine()
                appendLine("Просрочено:")
                overdue.take(10).forEach { appendLine("• ${it.title}") }
            }
            if (today.isNotEmpty()) {
                appendLine()
                appendLine("Сегодня:")
                today.take(10).forEach { appendLine("• ${it.title}") }
            }
            if (activeReminders.isNotEmpty()) {
                appendLine()
                appendLine("Ближайшие напоминания:")
                activeReminders.take(5).forEach { appendLine("• ${it.title}") }
            }
            appendLine()
            appendLine("Сгенерировано автоматизацией WM OS из локальных данных.")
        }
        val id = brain.createItem(
            type = KnowledgeTypeValues.NOTE,
            title = "Сводка на $date",
            content = body.toString(),
        )
        return AutomationRun(
            AutomationRunStatusValues.SUCCESS,
            "Сводка собрана: задач ${openTasks.size}, напоминаний ${activeReminders.size}",
            createdId = id,
        )
    }

    suspend fun openTaskCount(): Int = tasks.observeOpen().first().size

    suspend fun openTaskTitles(limit: Int = 5): List<String> =
        tasks.observeOpen().first().filter { it.status != TaskStatusValues.DONE }.take(limit).map { it.title }

    companion object {
        private const val DUPLICATE_GUARD_MS = 2_000L
        val supportedActions = AutomationActionValues.all
    }
}
