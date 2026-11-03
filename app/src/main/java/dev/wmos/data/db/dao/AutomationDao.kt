package dev.wmos.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.wmos.data.db.entity.AutomationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automation_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE id = :id")
    suspend fun getById(id: String): AutomationRuleEntity?

    @Query("SELECT * FROM automation_rules WHERE enabled = 1")
    suspend fun enabledRules(): List<AutomationRuleEntity>

    @Upsert
    suspend fun upsert(rule: AutomationRuleEntity)

    @Query("UPDATE automation_rules SET lastRunAt = :at, lastRunStatus = :status, lastRunMessage = :message, updatedAt = :at WHERE id = :id")
    suspend fun markRun(id: String, at: Long, status: String, message: String)

    @Query("UPDATE automation_rules SET enabled = :enabled, updatedAt = :at WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, at: Long)

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun delete(id: String)
}
