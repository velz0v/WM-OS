package dev.wmos.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dev.wmos.data.db.WmDatabase
import dev.wmos.data.db.entity.KnowledgeItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Private attachments for knowledge items.
 *
 * Files are copied out of the SAF stream into the app's private storage — the app never keeps a
 * long-lived read grant to a user file, and a URI that becomes invalid later cannot break an entry.
 * Size and name are bounded, and nothing is ever executed.
 */
class AttachmentRepository(
    private val context: Context,
    private val db: WmDatabase,
) {
    private val dao = db.brainDao()

    private val attachmentsDir: File
        get() = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun observeItemsWithAttachments(): Flow<List<KnowledgeItemEntity>> = dao.observeWithAttachments()

    /**
     * Copies [source] into private storage and links it to [itemId].
     * Returns the stored relative path (`attachments/<file>`).
     */
    suspend fun attach(itemId: String, source: Uri, mimeType: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val item = dao.getById(itemId) ?: error("Запись не найдена")
                val displayName = queryDisplayName(source) ?: "attachment"
                val safeName = sanitize(displayName)
                val target = File(attachmentsDir, "${itemId.take(8)}_$safeName")

                val copied: Long = withContext(Dispatchers.IO) {
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    val input = context.contentResolver.openInputStream(source)
                        ?: error("Не удалось прочитать файл")
                    input.use { stream ->
                        target.outputStream().use { output ->
                            while (true) {
                                val read = stream.read(buffer)
                                if (read <= 0) break
                                total += read
                                if (total > MAX_BYTES) error("Файл больше ${MAX_BYTES / 1024 / 1024} МБ")
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    total
                }
                if (copied == 0L) error("Файл пустой")

                // Replace semantics: an item has at most one attachment.
                item.attachmentUri?.let { old -> resolve(old)?.delete() }
                dao.upsert(
                    item.copy(
                        attachmentUri = "$DIR_NAME/${target.name}",
                        mimeType = mimeType ?: item.mimeType,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                "$DIR_NAME/${target.name}"
            }
        }

    /** Removes the link and the private copy. The original user file is never deleted. */
    suspend fun detach(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val item = dao.getById(itemId) ?: error("Запись не найдена")
            item.attachmentUri?.let { path -> resolve(path)?.delete() }
            dao.upsert(
                item.copy(
                    attachmentUri = null,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun resolve(relativePath: String): File? {
        val name = relativePath.substringAfterLast('/')
        val file = File(attachmentsDir, name)
        return if (file.exists()) file else null
    }

    fun sizeOf(relativePath: String): Long = resolve(relativePath)?.length() ?: 0L

    fun uriFor(relativePath: String): Uri? {
        val file = resolve(relativePath) ?: return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun displayName(relativePath: String): String =
        relativePath.substringAfterLast('/').substringAfter('_', missingDelimiterValue = relativePath)

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "file" }

    companion object {
        const val DIR_NAME = "attachments"
        const val MAX_BYTES = 20L * 1024 * 1024
    }
}
