package dev.wmos.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Every entity uses a stable string UUID as its identifier and stores instants as
 * epoch milliseconds in UTC. Local presentation (time zone, formatting) happens in
 * the UI layer only, so the same row reads identically on any device.
 */

@Entity(tableName = "folders", indices = [Index("parentId"), Index("name")])
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val parentId: String? = null,
    val colorKey: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "knowledge_items",
    indices = [Index("folderId"), Index("updatedAt"), Index("isDeleted"), Index("type")],
)
data class KnowledgeItemEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String = "",
    val content: String = "",
    val folderId: String? = null,
    val url: String? = null,
    val source: String? = null,
    val mimeType: String? = null,
    val attachmentUri: String? = null,
    /** Lower-cased title + content, maintained by the repository so search works for Cyrillic. */
    val searchText: String = "",
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val revision: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorKey: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "item_tags",
    primaryKeys = ["itemId", "tagId"],
    indices = [Index("tagId")],
)
data class ItemTagEntity(
    val itemId: String,
    val tagId: String,
)

@Entity(tableName = "tasks", indices = [Index("status"), Index("dueAt"), Index("projectId")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val status: String = TaskStatusValues.INBOX,
    val priority: String = TaskPriorityValues.NORMAL,
    val dueAt: Long? = null,
    val projectId: String? = null,
    val parentTaskId: String? = null,
    val completedAt: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Entity(tableName = "projects", indices = [Index("status")])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val goal: String = "",
    val status: String = "ACTIVE",
    val priority: String = TaskPriorityValues.NORMAL,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    val colorKey: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "reminders", indices = [Index("status"), Index("nextTriggerAt")])
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    /** Next planned firing, epoch millis UTC. */
    val nextTriggerAt: Long,
    val timeZoneId: String,
    val allDay: Boolean = false,
    /** Serialized [dev.wmos.domain.reminder.RecurrenceRule]; null for a one-shot reminder. */
    val recurrenceRule: String? = null,
    val status: String = ReminderStatusValues.SCHEDULED,
    val linkedEntityId: String? = null,
    val linkedEntityType: String? = null,
    val notificationId: Int = 0,
    val lastFiredAt: Long? = null,
    val revision: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "reminder_occurrences", indices = [Index("reminderId")])
data class ReminderOccurrenceEntity(
    @PrimaryKey val id: String,
    val reminderId: String,
    val scheduledAt: Long,
    val firedAt: Long? = null,
    val state: String = "PENDING",
    val snoozedUntil: Long? = null,
)

@Entity(tableName = "inbox_items", indices = [Index("status"), Index("createdAt")])
data class InboxItemEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String = "",
    val rawText: String = "",
    val url: String? = null,
    val mimeType: String? = null,
    val localUri: String? = null,
    val sourcePackage: String? = null,
    val status: String = "NEW",
    val errorMessage: String? = null,
    val createdAt: Long,
    val processedAt: Long? = null,
)

@Entity(tableName = "conversations", indices = [Index("updatedAt")])
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String? = null,
    val model: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "chat_messages", indices = [Index("conversationId")])
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val toolName: String? = null,
    val toolStatus: String? = null,
    val metaJson: String? = null,
)

@Entity(tableName = "ai_providers", indices = [Index("kind")])
data class AiProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val baseUrl: String,
    val model: String,
    /** Reference into [dev.wmos.data.prefs.SecretStore]; the key itself is never stored here. */
    val secretAlias: String,
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "action_log", indices = [Index("createdAt")])
data class ActionLogEntity(
    @PrimaryKey val id: String,
    val actor: String,
    val tool: String,
    val status: String,
    val summary: String,
    val targetIds: String = "",
    val createdAt: Long,
)

@Entity(tableName = "saved_memory", indices = [Index("key")])
data class SavedMemoryEntity(
    @PrimaryKey val id: String,
    val key: String,
    val value: String,
    val isSensitive: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

object TaskStatusValues {
    const val INBOX = "INBOX"
    const val NEXT = "NEXT"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val WAITING = "WAITING"
    const val DONE = "DONE"
    const val CANCELLED = "CANCELLED"

    val all = listOf(INBOX, NEXT, IN_PROGRESS, WAITING, DONE, CANCELLED)
    val open = listOf(INBOX, NEXT, IN_PROGRESS, WAITING)
}

object TaskPriorityValues {
    const val LOW = "LOW"
    const val NORMAL = "NORMAL"
    const val HIGH = "HIGH"
    const val URGENT = "URGENT"

    val all = listOf(LOW, NORMAL, HIGH, URGENT)
}

object ReminderStatusValues {
    const val SCHEDULED = "SCHEDULED"
    const val NOT_SCHEDULED = "NOT_SCHEDULED"
    const val SNOOZED = "SNOOZED"
    const val FIRED = "FIRED"
    const val DONE = "DONE"
    const val CANCELLED = "CANCELLED"
    const val MISSED = "MISSED"
}

object KnowledgeTypeValues {
    const val NOTE = "NOTE"
    const val MEMO = "MEMO"
    const val LINK = "LINK"
    const val EXCERPT = "EXCERPT"
    const val FILE = "FILE"

    val all = listOf(NOTE, MEMO, LINK, EXCERPT, FILE)
}

object InboxKindValues {
    const val TEXT = "TEXT"
    const val LINK = "LINK"
    const val IMAGE = "IMAGE"
    const val PDF = "PDF"
    const val FILE = "FILE"
}

object InboxStatusValues {
    const val NEW = "NEW"
    const val TRIAGED = "TRIAGED"
    const val FAILED = "FAILED"
}
