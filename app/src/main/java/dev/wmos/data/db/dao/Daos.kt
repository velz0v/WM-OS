package dev.wmos.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.wmos.data.db.entity.ActionLogEntity
import dev.wmos.data.db.entity.AiProviderEntity
import dev.wmos.data.db.entity.ChatMessageEntity
import dev.wmos.data.db.entity.ConversationEntity
import dev.wmos.data.db.entity.FolderEntity
import dev.wmos.data.db.entity.InboxItemEntity
import dev.wmos.data.db.entity.ItemTagEntity
import dev.wmos.data.db.entity.KnowledgeItemEntity
import dev.wmos.data.db.entity.ProjectEntity
import dev.wmos.data.db.entity.ReminderEntity
import dev.wmos.data.db.entity.ReminderOccurrenceEntity
import dev.wmos.data.db.entity.SavedMemoryEntity
import dev.wmos.data.db.entity.TagEntity
import dev.wmos.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

data class FolderCountRow(val folderId: String?, val count: Int)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE isArchived = 0 ORDER BY isPinned DESC, sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY isArchived ASC, isPinned DESC, sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun allOnce(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observeById(id: String): Flow<FolderEntity?>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE parentId IS :parentId AND isArchived = 0 ORDER BY isPinned DESC, name COLLATE NOCASE ASC")
    suspend fun childrenOf(parentId: String?): List<FolderEntity>

    @Query("SELECT COUNT(*) FROM folders WHERE parentId = :parentId AND isArchived = 0")
    suspend fun childCount(parentId: String): Int

    @Query("SELECT folderId AS folderId, COUNT(*) AS count FROM knowledge_items WHERE isDeleted = 0 GROUP BY folderId")
    fun observeFolderCounts(): Flow<List<FolderCountRow>>

    @Query("SELECT folderId AS folderId, COUNT(*) AS count FROM knowledge_items WHERE isDeleted = 1 GROUP BY folderId")
    fun observeTrashCounts(): Flow<List<FolderCountRow>>

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}

@Dao
interface BrainDao {
    @Query(
        """
        SELECT * FROM knowledge_items
        WHERE isDeleted = 0 AND isArchived = :archived
          AND (:folderId IS NULL OR folderId = :folderId)
          AND (:favoriteOnly = 0 OR isFavorite = 1)
          AND (:typeFilter = '' OR type = :typeFilter)
          AND (:query = '' OR searchText LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, updatedAt DESC
        LIMIT :limit
        """,
    )
    fun observeItems(
        archived: Boolean,
        folderId: String?,
        favoriteOnly: Boolean,
        typeFilter: String,
        query: String,
        limit: Int = 500,
    ): Flow<List<KnowledgeItemEntity>>

    @Query(
        """
        SELECT * FROM knowledge_items
        WHERE isDeleted = 0 AND isArchived = 0
          AND (:query = '' OR searchText LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, updatedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(query: String, limit: Int): Flow<List<KnowledgeItemEntity>>

    @Query("SELECT * FROM knowledge_items WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<KnowledgeItemEntity>>

    @Query("SELECT * FROM knowledge_items WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun allLive(): List<KnowledgeItemEntity>

    @Query("SELECT * FROM knowledge_items WHERE id = :id")
    fun observeById(id: String): Flow<KnowledgeItemEntity?>

    @Query("SELECT * FROM knowledge_items WHERE id = :id")
    suspend fun getById(id: String): KnowledgeItemEntity?

    @Query("SELECT * FROM knowledge_items WHERE isDeleted = 0 AND (:query = '' OR searchText LIKE '%' || :query || '%') ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun searchOnce(query: String, limit: Int): List<KnowledgeItemEntity>

    @Query("SELECT * FROM knowledge_items WHERE attachmentUri IS NOT NULL AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeWithAttachments(): Flow<List<KnowledgeItemEntity>>

    @Query("SELECT COUNT(*) FROM knowledge_items WHERE isDeleted = 0")
    fun observeLiveCount(): Flow<Int>

    @Query("SELECT id FROM knowledge_items WHERE folderId = :folderId")
    suspend fun idsInFolder(folderId: String): List<String>

    @Upsert
    suspend fun upsert(item: KnowledgeItemEntity)

    @Query("UPDATE knowledge_items SET isDeleted = 1, deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun moveToTrash(id: String, at: Long)

    @Query("UPDATE knowledge_items SET isDeleted = 0, deletedAt = NULL, updatedAt = :at WHERE id = :id")
    suspend fun restoreFromTrash(id: String, at: Long)

    @Query("DELETE FROM knowledge_items WHERE id = :id")
    suspend fun deleteHard(id: String)

    @Query("DELETE FROM knowledge_items WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM knowledge_items")
    suspend fun deleteAllHard()

    @Query("UPDATE knowledge_items SET folderId = :folderId, updatedAt = :at WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<String>, folderId: String?, at: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT t.* FROM tags t INNER JOIN item_tags it ON it.tagId = t.id WHERE it.itemId = :itemId ORDER BY t.name")
    fun observeForItem(itemId: String): Flow<List<TagEntity>>

    @Query("SELECT t.* FROM tags t INNER JOIN item_tags it ON it.tagId = t.id WHERE it.itemId = :itemId ORDER BY t.name")
    suspend fun forItem(itemId: String): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attach(cross: ItemTagEntity)

    @Query("DELETE FROM item_tags WHERE itemId = :itemId")
    suspend fun clearForItem(itemId: String)

    @Query("DELETE FROM item_tags WHERE itemId = :itemId AND tagId = :tagId")
    suspend fun detach(itemId: String, tagId: String)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM item_tags)")
    suspend fun deleteOrphans()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY (dueAt IS NULL) ASC, dueAt ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0")
    suspend fun allOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND status IN ('INBOX','NEXT','IN_PROGRESS','WAITING') ORDER BY (dueAt IS NULL) ASC, dueAt ASC")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND status IN ('DONE','CANCELLED') ORDER BY completedAt DESC")
    fun observeClosed(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND projectId = :projectId ORDER BY (dueAt IS NULL) ASC, dueAt ASC")
    fun observeByProject(projectId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 0 AND status IN ('INBOX','NEXT','IN_PROGRESS','WAITING') AND dueAt IS NOT NULL AND dueAt < :now")
    fun observeOverdueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 0 AND status IN ('INBOX','NEXT','IN_PROGRESS','WAITING')")
    fun observeOpenCount(): Flow<Int>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, completedAt = :completedAt, updatedAt = :at WHERE id = :id")
    suspend fun setStatus(id: String, status: String, completedAt: Long?, at: Long)

    @Query("UPDATE tasks SET isDeleted = 1, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE tasks SET projectId = NULL, updatedAt = :at WHERE projectId = :projectId")
    suspend fun clearProject(projectId: String, at: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects")
    suspend fun allOnce(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projectId AND isDeleted = 0 AND status NOT IN ('DONE','CANCELLED')")
    fun observeOpenTaskCount(projectId: String): Flow<Int>

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY nextTriggerAt ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun allOnce(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE status IN ('SCHEDULED','SNOOZED','NOT_SCHEDULED') ORDER BY nextTriggerAt ASC")
    fun observeActive(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observeById(id: String): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE status IN ('SCHEDULED','SNOOZED','NOT_SCHEDULED')")
    suspend fun activeReminders(): List<ReminderEntity>

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("UPDATE reminders SET status = :status, lastFiredAt = :firedAt, updatedAt = :at, revision = revision + 1 WHERE id = :id")
    suspend fun markFired(id: String, status: String, firedAt: Long, at: Long)

    @Query("UPDATE reminders SET nextTriggerAt = :nextTriggerAt, status = :status, updatedAt = :at, revision = revision + 1 WHERE id = :id")
    suspend fun reschedule(id: String, nextTriggerAt: Long, status: String, at: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsertOccurrence(occurrence: ReminderOccurrenceEntity)

    @Query("SELECT * FROM reminder_occurrences WHERE reminderId = :reminderId ORDER BY scheduledAt DESC LIMIT 20")
    fun observeOccurrences(reminderId: String): Flow<List<ReminderOccurrenceEntity>>
}

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<InboxItemEntity>>

    @Query("SELECT COUNT(*) FROM inbox_items WHERE status = 'NEW'")
    fun observeNewCount(): Flow<Int>

    @Query("SELECT * FROM inbox_items WHERE id = :id")
    suspend fun getById(id: String): InboxItemEntity?

    @Upsert
    suspend fun upsert(item: InboxItemEntity)

    @Query("DELETE FROM inbox_items WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>>

    @Upsert
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Upsert
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE conversationId = :id")
    suspend fun deleteMessages(id: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM ai_providers ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_providers WHERE id = :id")
    suspend fun getById(id: String): AiProviderEntity?

    @Query("SELECT * FROM ai_providers WHERE isDefault = 1 AND enabled = 1 LIMIT 1")
    suspend fun defaultProvider(): AiProviderEntity?

    @Upsert
    suspend fun upsert(provider: AiProviderEntity)

    @Query("UPDATE ai_providers SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("DELETE FROM ai_providers WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ActionLogDao {
    @Query("SELECT * FROM action_log ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<ActionLogEntity>>

    @Insert
    suspend fun insert(entry: ActionLogEntity)

    @Query("DELETE FROM action_log WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM saved_memory ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SavedMemoryEntity>>

    @Upsert
    suspend fun upsert(memory: SavedMemoryEntity)

    @Query("DELETE FROM saved_memory WHERE id = :id")
    suspend fun delete(id: String)
}
