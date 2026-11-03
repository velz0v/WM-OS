package dev.wmos.data.repo

import dev.wmos.data.db.WmDatabase
import dev.wmos.data.db.entity.SavedMemoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Saved facts the assistant is allowed to remember.
 *
 * Deliberately small and explicit: the user can list, edit and delete every entry, and nothing is
 * written here automatically. Sensitive entries are flagged so they can be excluded from AI context.
 */
class MemoryRepository(private val db: WmDatabase) {
    private val dao = db.memoryDao()

    fun observeAll(): Flow<List<SavedMemoryEntity>> = dao.observeAll()

    suspend fun save(id: String?, key: String, value: String, sensitive: Boolean): Result<String> {
        val cleanKey = key.trim()
        val cleanValue = value.trim()
        if (cleanKey.isEmpty()) return Result.failure(IllegalArgumentException("Ключ не может быть пустым"))
        if (cleanValue.isEmpty()) return Result.failure(IllegalArgumentException("Значение не может быть пустым"))
        if (cleanKey.length > 120) return Result.failure(IllegalArgumentException("Ключ слишком длинный"))
        if (cleanValue.length > 2000) return Result.failure(IllegalArgumentException("Значение слишком длинное"))
        val now = System.currentTimeMillis()
        val entity = SavedMemoryEntity(
            id = id ?: UUID.randomUUID().toString(),
            key = cleanKey,
            value = cleanValue,
            isSensitive = sensitive,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(entity)
        return Result.success(entity.id)
    }

    suspend fun delete(id: String) = dao.delete(id)
}
