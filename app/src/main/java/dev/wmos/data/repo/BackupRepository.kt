package dev.wmos.data.repo

import android.content.Context
import android.net.Uri
import dev.wmos.data.db.WmDatabase
import androidx.room.withTransaction
import dev.wmos.data.db.entity.FolderEntity
import dev.wmos.data.db.entity.KnowledgeItemEntity
import dev.wmos.data.db.entity.ProjectEntity
import dev.wmos.data.db.entity.ReminderEntity
import dev.wmos.data.db.entity.TaskEntity
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Export only. There is deliberately no import yet: a restore path that has not been tested on a
 * clean profile must not be advertised as working.
 *
 * Secrets never enter an export: API keys live in the keystore store and are referenced only by
 * an opaque alias, which is stripped here.
 */
class BackupRepository(
    private val context: Context,
    private val db: WmDatabase,
) {
    data class ExportSummary(val bytes: Int, val items: Int, val tasks: Int, val reminders: Int)

    suspend fun exportMarkdown(uri: Uri): Result<ExportSummary> = runCatching {
        val items = db.brainDao().allLive()
        val text = buildString {
            appendLine("# WM OS — экспорт заметок")
            appendLine()
            appendLine("Дата выгрузки: ${Instant.now()}")
            appendLine()
            items.forEach { item ->
                val title = item.title.ifBlank { item.content.lineSequence().firstOrNull().orEmpty().take(60) }
                appendLine("## ${title.ifBlank { "Без названия" }}")
                appendLine()
                appendLine("- Тип: ${item.type}")
                appendLine("- Изменено: ${Instant.ofEpochMilli(item.updatedAt)}")
                if (!item.url.isNullOrBlank()) appendLine("- Ссылка: ${item.url}")
                appendLine()
                appendLine(item.content)
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
        write(uri, text.toByteArray())
        ExportSummary(
            bytes = text.toByteArray().size,
            items = items.size,
            tasks = 0,
            reminders = 0,
        )
    }

    suspend fun exportJson(uri: Uri): Result<ExportSummary> = runCatching {
        val folders = db.folderDao().allOnce()
        val items = db.brainDao().allLive()
        val tasks = db.taskDao().allOnce()
        val projects = db.projectDao().allOnce()
        val reminders = db.reminderDao().allOnce()

        val root = buildJsonObject {
            put("schemaVersion", 1)
            put("app", "WM OS")
            put("appVersion", "0.1.0")
            put("exportedAt", Instant.now().toString())
            put(
                "folders",
                buildJsonArray {
                    folders.forEach { folder ->
                        add(
                            buildJsonObject {
                                put("id", folder.id)
                                put("name", folder.name)
                                put("description", folder.description)
                                put("parentId", folder.parentId ?: "")
                                put("isPinned", folder.isPinned)
                                put("isArchived", folder.isArchived)
                                put("createdAt", folder.createdAt)
                                put("updatedAt", folder.updatedAt)
                            },
                        )
                    }
                },
            )
            put(
                "items",
                buildJsonArray {
                    items.forEach { item ->
                        add(
                            buildJsonObject {
                                put("id", item.id)
                                put("type", item.type)
                                put("title", item.title)
                                put("content", item.content)
                                put("folderId", item.folderId ?: "")
                                put("url", item.url ?: "")
                                put("source", item.source ?: "")
                                put("isFavorite", item.isFavorite)
                                put("isPinned", item.isPinned)
                                put("isArchived", item.isArchived)
                                put("createdAt", item.createdAt)
                                put("updatedAt", item.updatedAt)
                            },
                        )
                    }
                },
            )
            put(
                "tasks",
                buildJsonArray {
                    tasks.forEach { task ->
                        add(
                            buildJsonObject {
                                put("id", task.id)
                                put("title", task.title)
                                put("description", task.description)
                                put("status", task.status)
                                put("priority", task.priority)
                                put("dueAt", task.dueAt ?: 0L)
                                put("projectId", task.projectId ?: "")
                                put("createdAt", task.createdAt)
                                put("updatedAt", task.updatedAt)
                            },
                        )
                    }
                },
            )
            put(
                "projects",
                buildJsonArray {
                    projects.forEach { project ->
                        add(
                            buildJsonObject {
                                put("id", project.id)
                                put("name", project.name)
                                put("description", project.description)
                                put("goal", project.goal)
                                put("status", project.status)
                                put("dueAt", project.dueAt ?: 0L)
                                put("createdAt", project.createdAt)
                            },
                        )
                    }
                },
            )
            put(
                "reminders",
                buildJsonArray {
                    reminders.forEach { reminder ->
                        add(
                            buildJsonObject {
                                put("id", reminder.id)
                                put("title", reminder.title)
                                put("description", reminder.description)
                                put("nextTriggerAt", reminder.nextTriggerAt)
                                put("timeZoneId", reminder.timeZoneId)
                                put("recurrenceRule", reminder.recurrenceRule ?: "")
                                put("status", reminder.status)
                                put("allDay", reminder.allDay)
                            },
                        )
                    }
                },
            )
        }
        val bytes = root.toString().toByteArray()
        write(uri, bytes)
        ExportSummary(bytes.size, items.size, tasks.size, reminders.size)
    }

    private fun write(uri: Uri, bytes: ByteArray) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: error("Не удалось открыть файл для записи")
        stream.use { it.write(bytes) }
    }

    suspend fun previewCounts(): Map<String, Int> {
        return runCatching {
            mapOf(
                "folders" to db.folderDao().allOnce().size,
                "items" to db.brainDao().allLive().size,
                "tasks" to db.taskDao().allOnce().size,
                "projects" to db.projectDao().allOnce().size,
                "reminders" to db.reminderDao().allOnce().size,
            )
        }.getOrDefault(emptyMap())
    }

    // ---------------------------------------------------------------- import

    enum class ImportMode { MERGE, AS_NEW, REPLACE }

    data class ImportPreview(
        val schemaVersion: Int,
        val exportedAt: String,
        val counts: Map<String, Int>,
        val conflicts: Int,
        val problems: List<String>,
    ) {
        val canImport: Boolean get() = problems.isEmpty() && schemaVersion == SCHEMA_VERSION
    }

    data class ImportSummary(
        val mode: ImportMode,
        val created: Int,
        val skipped: Int,
    )

    private data class Parsed(
        val schemaVersion: Int,
        val exportedAt: String,
        val folders: List<FolderEntity>,
        val items: List<KnowledgeItemEntity>,
        val tasks: List<TaskEntity>,
        val projects: List<ProjectEntity>,
        val reminders: List<ReminderEntity>,
        val problems: List<String>,
        val duplicatesInsideFile: Int,
    )

    suspend fun previewImport(uri: Uri): Result<ImportPreview> {
        val parsed = readAndParse(uri).getOrElse { return Result.failure(it) }
        val existing = existingIds()
        val conflicts = parsed.folders.count { it.id in existing.folders } +
            parsed.items.count { it.id in existing.items } +
            parsed.tasks.count { it.id in existing.tasks } +
            parsed.projects.count { it.id in existing.projects } +
            parsed.reminders.count { it.id in existing.reminders }
        val problems = parsed.problems.toMutableList()
        if (parsed.duplicatesInsideFile > 0) {
            problems += "В файле ${parsed.duplicatesInsideFile} повторяющихся ID"
        }
        return Result.success(
            ImportPreview(
                schemaVersion = parsed.schemaVersion,
                exportedAt = parsed.exportedAt,
                counts = mapOf(
                    "folders" to parsed.folders.size,
                    "items" to parsed.items.size,
                    "tasks" to parsed.tasks.size,
                    "projects" to parsed.projects.size,
                    "reminders" to parsed.reminders.size,
                ),
                conflicts = conflicts,
                problems = problems,
            ),
        )
    }

    /**
     * Applies an import inside a single transaction: either the whole package lands or nothing does.
     * REPLACE is destructive and is only reachable after an explicit confirmation in the UI.
     */
    suspend fun applyImport(uri: Uri, mode: ImportMode): Result<ImportSummary> {
        val parsed = readAndParse(uri).getOrElse { return Result.failure(it) }
        if (parsed.schemaVersion != SCHEMA_VERSION) {
            return Result.failure(IllegalArgumentException("Неподдерживаемая версия схемы: ${parsed.schemaVersion}"))
        }
        if (parsed.problems.isNotEmpty()) {
            return Result.failure(IllegalArgumentException(parsed.problems.first()))
        }

        var created = 0
        var skipped = 0
        db.withTransaction {
            if (mode == ImportMode.REPLACE) {
                db.reminderDao().deleteAll()
                db.taskDao().deleteAll()
                db.projectDao().deleteAll()
                db.brainDao().deleteAllHard()
                db.folderDao().deleteAll()
            }

            val existing = if (mode == ImportMode.MERGE) existingIds() else ExistingIds()

            val folderMap = mutableMapOf<String, String>()
            parsed.folders.forEach { folder ->
                if (mode == ImportMode.MERGE && folder.id in existing.folders) {
                    folderMap[folder.id] = folder.id
                    skipped++
                    return@forEach
                }
                folderMap[folder.id] = if (mode == ImportMode.AS_NEW) UUID.randomUUID().toString() else folder.id
            }
            parsed.folders.forEach { folder ->
                val newId = folderMap[folder.id] ?: return@forEach
                if (mode == ImportMode.MERGE && folder.id in existing.folders) return@forEach
                db.folderDao().upsert(
                    folder.copy(
                        id = newId,
                        parentId = folder.parentId?.let { folderMap[it] },
                    ),
                )
                created++
            }

            val projectMap = mutableMapOf<String, String>()
            parsed.projects.forEach { project ->
                if (mode == ImportMode.MERGE && project.id in existing.projects) {
                    projectMap[project.id] = project.id
                    skipped++
                    return@forEach
                }
                val newId = if (mode == ImportMode.AS_NEW) UUID.randomUUID().toString() else project.id
                projectMap[project.id] = newId
                db.projectDao().upsert(project.copy(id = newId))
                created++
            }

            parsed.items.forEach { item ->
                if (mode == ImportMode.MERGE && item.id in existing.items) {
                    skipped++
                    return@forEach
                }
                val newId = if (mode == ImportMode.AS_NEW) UUID.randomUUID().toString() else item.id
                db.brainDao().upsert(
                    item.copy(
                        id = newId,
                        folderId = item.folderId?.let { folderMap[it] },
                    ),
                )
                created++
            }

            parsed.tasks.forEach { task ->
                if (mode == ImportMode.MERGE && task.id in existing.tasks) {
                    skipped++
                    return@forEach
                }
                val newId = if (mode == ImportMode.AS_NEW) UUID.randomUUID().toString() else task.id
                db.taskDao().upsert(
                    task.copy(
                        id = newId,
                        projectId = task.projectId?.let { projectMap[it] },
                    ),
                )
                created++
            }

            parsed.reminders.forEach { reminder ->
                if (mode == ImportMode.MERGE && reminder.id in existing.reminders) {
                    skipped++
                    return@forEach
                }
                val newId = if (mode == ImportMode.AS_NEW) UUID.randomUUID().toString() else reminder.id
                val notificationId = (newId.hashCode() and 0x7FFFFFFF) % 100_000 + 1000
                db.reminderDao().upsert(
                    reminder.copy(id = newId, notificationId = notificationId),
                )
                created++
            }
        }
        return Result.success(ImportSummary(mode, created, skipped))
    }

    private data class ExistingIds(
        val folders: Set<String> = emptySet(),
        val items: Set<String> = emptySet(),
        val tasks: Set<String> = emptySet(),
        val projects: Set<String> = emptySet(),
        val reminders: Set<String> = emptySet(),
    )

    private suspend fun existingIds() = ExistingIds(
        folders = db.folderDao().allOnce().map { it.id }.toSet(),
        items = db.brainDao().allLive().map { it.id }.toSet(),
        tasks = db.taskDao().allOnce().map { it.id }.toSet(),
        projects = db.projectDao().allOnce().map { it.id }.toSet(),
        reminders = db.reminderDao().allOnce().map { it.id }.toSet(),
    )

    private suspend fun readAndParse(uri: Uri): Result<Parsed> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось прочитать файл")
        if (bytes.size > MAX_IMPORT_BYTES) {
            error("Файл больше ${MAX_IMPORT_BYTES / 1024 / 1024} МБ — импорт отклонён")
        }
        val text = bytes.toString(Charsets.UTF_8)
        val root = Json.parseToJsonElement(text).jsonObject
        val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val exportedAt = root["exportedAt"]?.jsonPrimitive?.content ?: ""
        val problems = mutableListOf<String>()

        val folders = root["folders"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj.str("id")
            if (id.isBlank()) {
                problems += "Папка без id пропущена"
                return@mapNotNull null
            }
            FolderEntity(
                id = id,
                name = obj.str("name").ifBlank { "Без названия" }.take(200),
                description = obj.str("description"),
                parentId = obj.str("parentId").ifBlank { null },
                isPinned = obj.bool("isPinned"),
                isArchived = obj.bool("isArchived"),
                createdAt = obj.long("createdAt", System.currentTimeMillis()),
                updatedAt = obj.long("updatedAt", System.currentTimeMillis()),
            )
        }

        val items = root["items"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj.str("id")
            if (id.isBlank()) {
                problems += "Запись без id пропущена"
                return@mapNotNull null
            }
            val content = obj.str("content")
            val title = obj.str("title")
            KnowledgeItemEntity(
                id = id,
                type = obj.str("type", "NOTE"),
                title = title,
                content = content,
                folderId = obj.str("folderId").ifBlank { null },
                url = obj.str("url").ifBlank { null },
                source = obj.str("source").ifBlank { null },
                searchText = (title + "\n" + content).lowercase().take(20_000),
                isFavorite = obj.bool("isFavorite"),
                isPinned = obj.bool("isPinned"),
                isArchived = obj.bool("isArchived"),
                createdAt = obj.long("createdAt", System.currentTimeMillis()),
                updatedAt = obj.long("updatedAt", System.currentTimeMillis()),
            )
        }

        val tasks = root["tasks"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj.str("id")
            val title = obj.str("title")
            if (id.isBlank() || title.isBlank()) {
                problems += "Задача без id или названия пропущена"
                return@mapNotNull null
            }
            TaskEntity(
                id = id,
                title = title.take(300),
                description = obj.str("description"),
                status = obj.str("status", "INBOX"),
                priority = obj.str("priority", "NORMAL"),
                dueAt = obj.long("dueAt", 0L).takeIf { it > 0L },
                projectId = obj.str("projectId").ifBlank { null },
                createdAt = obj.long("createdAt", System.currentTimeMillis()),
                updatedAt = obj.long("updatedAt", System.currentTimeMillis()),
            )
        }

        val projects = root["projects"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj.str("id")
            val name = obj.str("name")
            if (id.isBlank() || name.isBlank()) {
                problems += "Проект без id или названия пропущен"
                return@mapNotNull null
            }
            ProjectEntity(
                id = id,
                name = name.take(200),
                description = obj.str("description"),
                goal = obj.str("goal"),
                dueAt = obj.long("dueAt", 0L).takeIf { it > 0L },
                createdAt = obj.long("createdAt", System.currentTimeMillis()),
                updatedAt = obj.long("updatedAt", System.currentTimeMillis()),
            )
        }

        val reminders = root["reminders"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj.str("id")
            val title = obj.str("title")
            val triggerAt = obj.long("nextTriggerAt", 0L)
            if (id.isBlank() || title.isBlank() || triggerAt <= 0L) {
                problems += "Напоминание без id, названия или времени пропущено"
                return@mapNotNull null
            }
            ReminderEntity(
                id = id,
                title = title.take(300),
                description = obj.str("description"),
                nextTriggerAt = triggerAt,
                timeZoneId = obj.str("timeZoneId", "UTC").ifBlank { "UTC" },
                allDay = obj.bool("allDay"),
                recurrenceRule = obj.str("recurrenceRule").ifBlank { null },
                status = obj.str("status", "SCHEDULED"),
                notificationId = (id.hashCode() and 0x7FFFFFFF) % 100_000 + 1000,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        }

        val allIds = folders.map { it.id } + items.map { it.id } + tasks.map { it.id } +
            projects.map { it.id } + reminders.map { it.id }
        val duplicates = allIds.size - allIds.toSet().size

        Parsed(
            schemaVersion = schemaVersion,
            exportedAt = exportedAt,
            folders = folders,
            items = items,
            tasks = tasks,
            projects = projects,
            reminders = reminders,
            problems = problems,
            duplicatesInsideFile = duplicates,
        )
    }

    companion object {
        const val SCHEMA_VERSION = 1
        private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024
        private val Json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

private fun kotlinx.serialization.json.JsonObject.str(key: String, default: String = ""): String =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull() ?: default

private fun kotlinx.serialization.json.JsonObject.long(key: String, default: Long = 0L): Long =
    runCatching { this[key]?.jsonPrimitive?.content?.toLongOrNull() }.getOrNull() ?: default

private fun kotlinx.serialization.json.JsonObject.bool(key: String, default: Boolean = false): Boolean =
    runCatching { this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() }.getOrNull() ?: default
