package dev.wmos.data.repo

import dev.wmos.data.db.WmDatabase
import dev.wmos.data.db.entity.InboxItemEntity
import dev.wmos.data.db.entity.KnowledgeItemEntity
import dev.wmos.data.db.entity.ProjectEntity
import dev.wmos.data.db.entity.ReminderEntity
import dev.wmos.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.first

data class SearchResults(
    val query: String = "",
    val notes: List<KnowledgeItemEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
    val inbox: List<InboxItemEntity> = emptyList(),
) {
    val total: Int
        get() = notes.size + tasks.size + reminders.size + projects.size + inbox.size
}

/**
 * Global search across every user-visible entity.
 *
 * Notes use the maintained `searchText` column, so Cyrillic matches case-insensitively in SQL.
 * The other entities are filtered in Kotlin for the same reason: SQLite `LIKE`/`lower()` only fold
 * ASCII, which would silently fail for Russian text.
 */
class SearchRepository(private val db: WmDatabase) {
    suspend fun search(rawQuery: String, perTypeLimit: Int = 20): SearchResults {
        val query = rawQuery.trim()
        if (query.length < 2) return SearchResults(query = query)
        val needle = query.lowercase()

        val notes = db.brainDao().searchOnce(needle, perTypeLimit)
        val tasks = db.taskDao().allOnce()
            .filter { it.title.lowercase().contains(needle) || it.description.lowercase().contains(needle) }
            .take(perTypeLimit)
        val reminders = db.reminderDao().allOnce()
            .filter { it.title.lowercase().contains(needle) || it.description.lowercase().contains(needle) }
            .take(perTypeLimit)
        val projects = db.projectDao().allOnce()
            .filter { it.name.lowercase().contains(needle) || it.goal.lowercase().contains(needle) }
            .take(perTypeLimit)
        val inbox = db.inboxDao().observeAll().first()
            .filter { it.title.lowercase().contains(needle) || it.rawText.lowercase().contains(needle) }
            .take(perTypeLimit)

        return SearchResults(
            query = query,
            notes = notes,
            tasks = tasks,
            reminders = reminders,
            projects = projects,
            inbox = inbox,
        )
    }
}
