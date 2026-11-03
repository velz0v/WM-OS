package dev.wmos.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.wmos.data.db.WmDatabase
import dev.wmos.data.db.entity.ActionLogEntity
import dev.wmos.data.db.entity.AiProviderEntity
import dev.wmos.data.db.entity.ChatMessageEntity
import dev.wmos.data.db.entity.ConversationEntity
import dev.wmos.data.db.entity.InboxItemEntity
import dev.wmos.data.db.entity.InboxKindValues
import dev.wmos.data.db.entity.InboxStatusValues
import dev.wmos.data.prefs.SecretStore
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

/**
 * Inbox deliberately stores the raw material *before* any AI touches it. If analysis fails,
 * the original is still on disk and in the database.
 */
class InboxRepository(
    private val context: Context,
    private val db: WmDatabase,
) {
    private val dao = db.inboxDao()
    private val filesDir: File by lazy { File(context.filesDir, "inbox").apply { mkdirs() } }

    fun observeAll(): Flow<List<InboxItemEntity>> = dao.observeAll()

    fun observeNewCount(): Flow<Int> = dao.observeNewCount()

    suspend fun captureText(text: String, sourcePackage: String? = null): String {
        val now = System.currentTimeMillis()
        val isUrl = text.trim().startsWith("http://") || text.trim().startsWith("https://")
        val id = UUID.randomUUID().toString()
        dao.upsert(
            InboxItemEntity(
                id = id,
                kind = if (isUrl) InboxKindValues.LINK else InboxKindValues.TEXT,
                title = text.trim().take(80),
                rawText = text,
                url = if (isUrl) text.trim() else null,
                sourcePackage = sourcePackage,
                status = InboxStatusValues.NEW,
                createdAt = now,
            ),
        )
        return id
    }

    /**
     * Copies the shared content into private storage. A failed copy is reported as FAILED —
     * the UI must never show "Сохранено" for a stream that was not actually persisted.
     */
    suspend fun captureUri(uri: Uri, mimeType: String?, sourcePackage: String? = null): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val kind = when {
            mimeType?.startsWith("image/") == true -> InboxKindValues.IMAGE
            mimeType == "application/pdf" -> InboxKindValues.PDF
            else -> InboxKindValues.FILE
        }
        val displayName = queryDisplayName(uri) ?: "attachment-$now"
        return runCatching {
            val target = File(filesDir, "${id}_${sanitize(displayName)}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Не удалось открыть поток данных")
            dao.upsert(
                InboxItemEntity(
                    id = id,
                    kind = kind,
                    title = displayName,
                    rawText = "",
                    mimeType = mimeType,
                    localUri = target.toURI().toString(),
                    sourcePackage = sourcePackage,
                    status = InboxStatusValues.NEW,
                    createdAt = now,
                ),
            )
            id
        }.getOrElse { error ->
            dao.upsert(
                InboxItemEntity(
                    id = id,
                    kind = kind,
                    title = displayName,
                    mimeType = mimeType,
                    sourcePackage = sourcePackage,
                    status = InboxStatusValues.FAILED,
                    errorMessage = error.message ?: "Ошибка копирования",
                    createdAt = now,
                ),
            )
            id
        }
    }

    suspend fun get(id: String): InboxItemEntity? = dao.getById(id)

    suspend fun markTriaged(id: String, note: String? = null) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                status = InboxStatusValues.TRIAGED,
                processedAt = System.currentTimeMillis(),
                errorMessage = note,
            ),
        )
    }

    suspend fun delete(id: String) {
        val existing = dao.getById(id)
        existing?.localUri?.let { uri ->
            runCatching { File(Uri.parse(uri).path ?: return@runCatching).delete() }
        }
        dao.delete(id)
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "file" }
}

class ChatRepository(private val db: WmDatabase) {
    private val dao = db.chatDao()

    fun observeConversations(): Flow<List<ConversationEntity>> = dao.observeConversations()

    fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>> = dao.observeMessages(conversationId)

    suspend fun conversation(id: String): ConversationEntity? = dao.getConversation(id)

    suspend fun createConversation(title: String = "Новый диалог", providerId: String? = null, model: String? = null): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.upsertConversation(
            ConversationEntity(
                id = id,
                title = title,
                providerId = providerId,
                model = model,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    suspend fun appendMessage(
        conversationId: String,
        role: String,
        content: String,
        toolName: String? = null,
        toolStatus: String? = null,
        metaJson: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertMessage(
            ChatMessageEntity(
                id = id,
                conversationId = conversationId,
                role = role,
                content = content,
                createdAt = now,
                toolName = toolName,
                toolStatus = toolStatus,
                metaJson = metaJson,
            ),
        )
        val conversation = dao.getConversation(conversationId)
        if (conversation != null) {
            val title = if (conversation.title == "Новый диалог" && role == "user") {
                content.take(48).trim().ifBlank { conversation.title }
            } else {
                conversation.title
            }
            dao.upsertConversation(conversation.copy(title = title, updatedAt = now))
        }
        return id
    }

    suspend fun rename(id: String, title: String) {
        val conversation = dao.getConversation(id) ?: return
        dao.upsertConversation(conversation.copy(title = title.trim().take(120), updatedAt = System.currentTimeMillis()))
    }

    suspend fun archive(id: String) {
        val conversation = dao.getConversation(id) ?: return
        dao.upsertConversation(conversation.copy(isArchived = true, updatedAt = System.currentTimeMillis()))
    }

    /**
     * Deleting a conversation removes only the chat log. Tasks, notes and reminders created
     * through tools are independent entities and stay untouched.
     */
    suspend fun deleteConversation(id: String) {
        dao.deleteMessages(id)
        dao.deleteConversation(id)
    }
}

class ProviderRepository(
    private val db: WmDatabase,
    private val secrets: SecretStore,
) {
    private val dao = db.providerDao()

    fun observeAll(): Flow<List<AiProviderEntity>> = dao.observeAll()

    suspend fun get(id: String): AiProviderEntity? = dao.getById(id)

    suspend fun default(): AiProviderEntity? = dao.defaultProvider()

    suspend fun save(
        id: String?,
        name: String,
        kind: String,
        baseUrl: String,
        model: String,
        apiKey: String?,
        makeDefault: Boolean,
    ): Result<String> {
        val trimmedName = name.trim()
        val trimmedUrl = baseUrl.trim()
        val trimmedModel = model.trim()
        if (trimmedName.isEmpty()) return Result.failure(IllegalArgumentException("Укажите название провайдера"))
        if (!trimmedUrl.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("Разрешён только HTTPS-адрес"))
        }
        if (trimmedModel.isEmpty()) return Result.failure(IllegalArgumentException("Укажите модель"))

        val now = System.currentTimeMillis()
        val providerId = id ?: UUID.randomUUID().toString()
        val existing = id?.let { dao.getById(it) }
        val alias = existing?.secretAlias ?: SecretStore.newAlias(providerId)
        if (!apiKey.isNullOrBlank()) secrets.put(alias, apiKey.trim())
        if (existing == null && !secrets.exists(alias)) {
            return Result.failure(IllegalArgumentException("Укажите API-ключ"))
        }
        if (makeDefault) dao.clearDefaults()
        dao.upsert(
            AiProviderEntity(
                id = providerId,
                name = trimmedName,
                kind = kind,
                baseUrl = trimmedUrl.trimEnd('/'),
                model = trimmedModel,
                secretAlias = alias,
                isDefault = makeDefault || existing?.isDefault == true,
                enabled = true,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        return Result.success(providerId)
    }

    suspend fun setDefault(id: String) {
        dao.clearDefaults()
        dao.getById(id)?.let { dao.upsert(it.copy(isDefault = true, updatedAt = System.currentTimeMillis())) }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.getById(id)?.let { dao.upsert(it.copy(enabled = enabled, updatedAt = System.currentTimeMillis())) }
    }

    /** Removing a provider also wipes its secret from the keystore-backed store. */
    suspend fun delete(id: String) {
        dao.getById(id)?.let { secrets.delete(it.secretAlias) }
        dao.delete(id)
    }

    fun maskedKey(provider: AiProviderEntity): String = secrets.mask(provider.secretAlias)

    fun apiKey(provider: AiProviderEntity): String? = secrets.get(provider.secretAlias)
}

class ActionLogRepository(private val db: WmDatabase) {
    private val dao = db.actionLogDao()

    fun observeRecent(limit: Int = 100): Flow<List<ActionLogEntity>> = dao.observeRecent(limit)

    suspend fun log(
        actor: String,
        tool: String,
        status: String,
        summary: String,
        targetIds: List<String> = emptyList(),
    ) {
        dao.insert(
            ActionLogEntity(
                id = UUID.randomUUID().toString(),
                actor = actor,
                tool = tool,
                status = status,
                summary = summary.take(500),
                targetIds = targetIds.joinToString(","),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun purgeOlderThan(days: Int) {
        dao.deleteOlderThan(System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L)
    }
}
