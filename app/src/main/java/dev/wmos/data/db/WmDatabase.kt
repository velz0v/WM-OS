package dev.wmos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.wmos.data.db.dao.ActionLogDao
import dev.wmos.data.db.dao.AutomationDao
import dev.wmos.data.db.dao.BrainDao
import dev.wmos.data.db.dao.ChatDao
import dev.wmos.data.db.dao.FolderDao
import dev.wmos.data.db.dao.InboxDao
import dev.wmos.data.db.dao.MemoryDao
import dev.wmos.data.db.dao.ProjectDao
import dev.wmos.data.db.dao.ProviderDao
import dev.wmos.data.db.dao.ReminderDao
import dev.wmos.data.db.dao.TagDao
import dev.wmos.data.db.dao.TaskDao
import dev.wmos.data.db.entity.ActionLogEntity
import dev.wmos.data.db.entity.AutomationRuleEntity
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

@Database(
    entities = [
        FolderEntity::class,
        KnowledgeItemEntity::class,
        TagEntity::class,
        ItemTagEntity::class,
        TaskEntity::class,
        ProjectEntity::class,
        ReminderEntity::class,
        ReminderOccurrenceEntity::class,
        InboxItemEntity::class,
        ConversationEntity::class,
        ChatMessageEntity::class,
        AiProviderEntity::class,
        ActionLogEntity::class,
        SavedMemoryEntity::class,
        AutomationRuleEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class WmDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao

    abstract fun brainDao(): BrainDao

    abstract fun tagDao(): TagDao

    abstract fun taskDao(): TaskDao

    abstract fun projectDao(): ProjectDao

    abstract fun reminderDao(): ReminderDao

    abstract fun inboxDao(): InboxDao

    abstract fun chatDao(): ChatDao

    abstract fun providerDao(): ProviderDao

    abstract fun actionLogDao(): ActionLogDao

    abstract fun memoryDao(): MemoryDao

    abstract fun automationDao(): AutomationDao

    companion object {
        const val NAME = "wm_os.db"

        @Volatile
        private var instance: WmDatabase? = null

        /**
         * No destructive fallback: a schema bump must ship an explicit [androidx.room.migration.Migration].
         * Losing user data on upgrade is a release blocker.
         */
        fun build(context: Context): WmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WmDatabase::class.java,
                    NAME,
                )
                    .addMigrations(*WmMigrations.all)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
    }
}
