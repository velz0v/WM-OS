package dev.wmos.data.repo

import dev.wmos.data.db.WmDatabase
import dev.wmos.data.db.entity.FolderEntity
import dev.wmos.data.db.entity.ItemTagEntity
import dev.wmos.data.db.entity.KnowledgeItemEntity
import dev.wmos.data.db.entity.KnowledgeTypeValues
import dev.wmos.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Second Brain: folders, knowledge items, tags, trash and search.
 *
 * Invariants enforced here (not in the UI):
 *  * a folder can never become its own ancestor;
 *  * deleting a folder never silently deletes its content;
 *  * `searchText` is always the lower-cased title+content, so Cyrillic search works;
 *  * trash is a soft delete and can be undone.
 */
class BrainRepository(private val db: WmDatabase) {
    private val folderDao = db.folderDao()
    private val brainDao = db.brainDao()
    private val tagDao = db.tagDao()

    // ---------------------------------------------------------------- folders

    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeActive()

    fun observeAllFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()

    fun observeFolder(id: String): Flow<FolderEntity?> = folderDao.observeById(id)

    fun observeFolderCounts(): Flow<Map<String?, Int>> =
        db.folderDao().observeFolderCounts().map { rows -> rows.associate { it.folderId to it.count } }

    suspend fun folder(id: String): FolderEntity? = folderDao.getById(id)

    suspend fun createFolder(name: String, parentId: String? = null, description: String = ""): Result<String> {
        val clean = name.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Название папки не может быть пустым"))
        if (clean.length > 120) return Result.failure(IllegalArgumentException("Название слишком длинное"))
        if (parentId != null && folderDao.getById(parentId) == null) {
            return Result.failure(IllegalArgumentException("Родительская папка не найдена"))
        }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        folderDao.upsert(
            FolderEntity(
                id = id,
                name = clean,
                description = description.trim(),
                parentId = parentId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return Result.success(id)
    }

    suspend fun renameFolder(id: String, name: String): Result<Unit> {
        val folder = folderDao.getById(id) ?: return Result.failure(IllegalStateException("Папка не найдена"))
        val clean = name.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Название не может быть пустым"))
        folderDao.upsert(folder.copy(name = clean, updatedAt = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    /** Moves [id] under [newParentId]. Rejects cycles and self-parenting. */
    suspend fun moveFolder(id: String, newParentId: String?): Result<Unit> {
        val folder = folderDao.getById(id) ?: return Result.failure(IllegalStateException("Папка не найдена"))
        if (newParentId == id) return Result.failure(IllegalArgumentException("Нельзя переместить папку в саму себя"))
        if (newParentId != null) {
            if (folderDao.getById(newParentId) == null) {
                return Result.failure(IllegalArgumentException("Целевая папка не найдена"))
            }
            if (isDescendant(candidateId = newParentId, ancestorId = id)) {
                return Result.failure(IllegalArgumentException("Нельзя переместить папку внутрь её же подпапки"))
            }
        }
        folderDao.upsert(folder.copy(parentId = newParentId, updatedAt = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    suspend fun isDescendant(candidateId: String, ancestorId: String): Boolean {
        var cursor: String? = candidateId
        var guard = 0
        while (cursor != null && guard++ < 64) {
            if (cursor == ancestorId) return true
            cursor = folderDao.getById(cursor)?.parentId
        }
        return false
    }

    suspend fun setFolderPinned(id: String, pinned: Boolean) {
        folderDao.getById(id)?.let {
            folderDao.upsert(it.copy(isPinned = pinned, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun setFolderArchived(id: String, archived: Boolean) {
        folderDao.getById(id)?.let {
            folderDao.upsert(it.copy(isArchived = archived, updatedAt = System.currentTimeMillis()))
        }
    }

    /** Deletes the folder only; items are moved to the parent folder (or the root). */
    suspend fun deleteFolderAndKeepItems(id: String): Result<Int> {
        val folder = folderDao.getById(id) ?: return Result.failure(IllegalStateException("Папка не найдена"))
        val ids = brainDao.idsInFolder(id)
        if (ids.isNotEmpty()) {
            brainDao.moveToFolder(ids, folder.parentId, System.currentTimeMillis())
        }
        folderDao.delete(id)
        return Result.success(ids.size)
    }

    // ------------------------------------------------------------------ items

    fun observeItems(
        folderId: String? = null,
        favoriteOnly: Boolean = false,
        typeFilter: String = "",
        query: String = "",
        archived: Boolean = false,
    ): Flow<List<KnowledgeItemEntity>> = brainDao.observeItems(
        archived = archived,
        folderId = folderId,
        favoriteOnly = favoriteOnly,
        typeFilter = typeFilter,
        query = normalize(query),
    )

    fun observeRecent(query: String = "", limit: Int = 30): Flow<List<KnowledgeItemEntity>> =
        brainDao.observeRecent(normalize(query), limit)

    fun observeItem(id: String): Flow<KnowledgeItemEntity?> = brainDao.observeById(id)

    fun observeTrash(): Flow<List<KnowledgeItemEntity>> = brainDao.observeTrash()

    fun observeLiveCount(): Flow<Int> = brainDao.observeLiveCount()

    suspend fun item(id: String): KnowledgeItemEntity? = brainDao.getById(id)

    suspend fun createItem(
        type: String = KnowledgeTypeValues.NOTE,
        title: String = "",
        content: String = "",
        folderId: String? = null,
        url: String? = null,
        source: String? = null,
        mimeType: String? = null,
        attachmentUri: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        brainDao.upsert(
            KnowledgeItemEntity(
                id = id,
                type = type,
                title = title.trim(),
                content = content,
                folderId = folderId,
                url = url,
                source = source,
                mimeType = mimeType,
                attachmentUri = attachmentUri,
                searchText = buildSearchText(title.trim(), content),
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    /**
     * Autosave target. Returns the new revision so the editor can detect a lost update.
     * An item that disappeared (purged elsewhere) is recreated instead of silently dropped.
     */
    suspend fun saveItem(
        id: String,
        title: String,
        content: String,
        expectedRevision: Int? = null,
    ): Result<Int> {
        val now = System.currentTimeMillis()
        val existing = brainDao.getById(id)
        if (existing == null) {
            brainDao.upsert(
                KnowledgeItemEntity(
                    id = id,
                    type = KnowledgeTypeValues.NOTE,
                    title = title.trim(),
                    content = content,
                    searchText = buildSearchText(title, content),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            return Result.success(1)
        }
        if (expectedRevision != null && existing.revision > expectedRevision) {
            return Result.failure(ConflictException(existing.revision))
        }
        if (existing.title == title.trim() && existing.content == content) {
            return Result.success(existing.revision)
        }
        val updated = existing.copy(
            title = title.trim(),
            content = content,
            searchText = buildSearchText(title, content),
            revision = existing.revision + 1,
            updatedAt = now,
        )
        brainDao.upsert(updated)
        return Result.success(updated.revision)
    }

    /**
     * Metadata patch (folder, tags, flags). Deliberately does not bump [KnowledgeItemEntity.revision]:
     * the revision guards concurrent *content* edits, and a folder change must not turn an
     * in-flight autosave into a false conflict.
     */
    suspend fun updateItem(id: String, patch: (KnowledgeItemEntity) -> KnowledgeItemEntity): Boolean {
        val existing = brainDao.getById(id) ?: return false
        val patched = patch(existing)
        val updated = patched.copy(
            searchText = buildSearchText(patched.title, patched.content),
            updatedAt = System.currentTimeMillis(),
        )
        brainDao.upsert(updated)
        return true
    }

    suspend fun setTitleAndType(id: String, title: String, type: String) {
        val existing = brainDao.getById(id) ?: return
        brainDao.upsert(
            existing.copy(
                title = title.trim(),
                type = type,
                searchText = buildSearchText(title, existing.content),
                revision = existing.revision + 1,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun toggleFavorite(id: String) {
        val existing = brainDao.getById(id) ?: return
        brainDao.upsert(existing.copy(isFavorite = !existing.isFavorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun togglePinned(id: String) {
        val existing = brainDao.getById(id) ?: return
        brainDao.upsert(existing.copy(isPinned = !existing.isPinned, updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleArchived(id: String) {
        val existing = brainDao.getById(id) ?: return
        brainDao.upsert(existing.copy(isArchived = !existing.isArchived, updatedAt = System.currentTimeMillis()))
    }

    suspend fun moveItems(ids: List<String>, folderId: String?): Result<Int> {
        if (ids.isEmpty()) return Result.success(0)
        brainDao.moveToFolder(ids, folderId, System.currentTimeMillis())
        return Result.success(ids.size)
    }

    suspend fun moveToTrash(id: String) = brainDao.moveToTrash(id, System.currentTimeMillis())

    suspend fun restoreFromTrash(id: String) = brainDao.restoreFromTrash(id, System.currentTimeMillis())

    suspend fun deleteForever(id: String) = brainDao.deleteHard(id)

    suspend fun emptyTrash() = brainDao.emptyTrash()

    suspend fun search(query: String, limit: Int = 50): List<KnowledgeItemEntity> =
        brainDao.searchOnce(normalize(query), limit)

    // ------------------------------------------------------------------- tags

    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()

    fun observeTagsForItem(itemId: String): Flow<List<TagEntity>> = tagDao.observeForItem(itemId)

    /** Replaces the tag set of an item; unknown tag names are created on the fly. */
    suspend fun setTags(itemId: String, tagNames: List<String>): List<String> {
        val normalized = tagNames
            .map { it.trim().removePrefix("#").lowercase() }
            .filter { it.isNotEmpty() && it.length <= 40 }
            .distinct()
            .take(20)
        val now = System.currentTimeMillis()
        tagDao.clearForItem(itemId)
        for (name in normalized) {
            val existing = tagDao.findByName(name)
            if (existing == null) {
                tagDao.insertIgnore(TagEntity(id = UUID.randomUUID().toString(), name = name, createdAt = now))
            }
            val resolved = tagDao.findByName(name)
            if (resolved != null) tagDao.attach(ItemTagEntity(itemId = itemId, tagId = resolved.id))
        }
        return normalized
    }

    suspend fun deleteOrphanTags() = tagDao.deleteOrphans()

    // ------------------------------------------------------------------- seed

    /** Idempotent first-run content: a small, editable folder skeleton. */
    suspend fun seedDefaultFoldersIfEmpty() {
        val existing = folderDao.childrenOf(null)
        if (existing.isNotEmpty()) return
        val now = System.currentTimeMillis()
        val seeds = listOf(
            "Входящие" to "Материалы, которые ещё не разобраны",
            "Памятки" to "Инструкции, справочники и чек-листы",
            "Проекты" to "Рабочие материалы по проектам",
            "Документы" to "Импортированные документы и выдержки",
            "Идеи" to "Быстрые мысли и заготовки",
            "Справочники" to "Долговременные знания",
        )
        seeds.forEachIndexed { index, (name, description) ->
            folderDao.upsert(
                FolderEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    description = description,
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    fun normalize(query: String): String = query.trim().lowercase()

    fun buildSearchText(title: String, content: String): String =
        (title + "\n" + content).lowercase().replace(Regex("\\s+"), " ").take(20_000)
}

class ConflictException(val currentRevision: Int) :
    IllegalStateException("Запись изменена в другом месте (ревизия $currentRevision)")
