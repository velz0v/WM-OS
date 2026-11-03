package dev.wmos.domain.automation

import dev.wmos.data.db.entity.AutomationActionValues
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Pure validation and parsing for automation rules.
 *
 * Kept free of Android and the database so the rules can be unit-tested, and so a malformed rule
 * can never reach the runner.
 */
object AutomationActions {
    const val MAX_TITLE = 300
    const val MAX_BODY = 20_000

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class TaskConfig(val title: String, val dueInDays: Int?)

    data class NoteConfig(val title: String, val body: String)

    sealed interface Validated {
        data class Task(val config: TaskConfig) : Validated

        data class Note(val config: NoteConfig) : Validated

        data object DailySummary : Validated
    }

    fun encodeTask(title: String, dueInDays: Int?): String = buildJsonObject {
        put("title", title.trim())
        if (dueInDays != null) put("dueInDays", dueInDays)
    }.toString()

    fun encodeNote(title: String, body: String): String = buildJsonObject {
        put("title", title.trim())
        put("body", body)
    }.toString()

    fun validate(actionType: String, configJson: String): Result<Validated> {
        val config: JsonObject = runCatching { json.parseToJsonElement(configJson).jsonObject }
            .getOrElse { return Result.failure(IllegalArgumentException("Конфигурация не является JSON-объектом")) }
        return when (actionType) {
            AutomationActionValues.CREATE_TASK -> {
                val title = config.str("title").trim()
                if (title.isEmpty()) {
                    Result.failure(IllegalArgumentException("Укажите название задачи"))
                } else if (title.length > MAX_TITLE) {
                    Result.failure(IllegalArgumentException("Название задачи слишком длинное"))
                } else {
                    val dueInDays = config.int("dueInDays")?.takeIf { it in 0..365 }
                    Result.success(Validated.Task(TaskConfig(title, dueInDays)))
                }
            }

            AutomationActionValues.CREATE_NOTE -> {
                val title = config.str("title").trim()
                val body = config.str("body")
                when {
                    title.isEmpty() && body.isBlank() ->
                        Result.failure(IllegalArgumentException("Укажите заголовок или текст заметки"))

                    body.length > MAX_BODY ->
                        Result.failure(IllegalArgumentException("Текст заметки слишком длинный"))

                    else -> Result.success(Validated.Note(NoteConfig(title, body)))
                }
            }

            AutomationActionValues.DAILY_SUMMARY -> Result.success(Validated.DailySummary)

            else -> Result.failure(IllegalArgumentException("Неизвестное действие: $actionType"))
        }
    }

    /** Human-readable summary of what the rule will do. Shown before enabling or running. */
    fun describe(actionType: String, configJson: String): String =
        when (val validated = validate(actionType, configJson).getOrNull()) {
            is Validated.Task -> buildString {
                append("Создаст задачу «${validated.config.title}»")
                validated.config.dueInDays?.let { append(", срок через $it дн.") }
            }

            is Validated.Note -> "Создаст заметку «${validated.config.title.ifBlank { "Без названия" }}»"
            Validated.DailySummary -> "Соберёт сводку открытых задач и ближайших напоминаний в заметку"
            null -> "Некорректная конфигурация"
        }

    private fun JsonObject.str(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: ""

    private fun JsonObject.int(key: String): Int? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() }.getOrNull()
}
