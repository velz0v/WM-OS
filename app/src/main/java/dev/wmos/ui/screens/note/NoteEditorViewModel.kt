package dev.wmos.ui.screens.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.data.db.entity.KnowledgeTypeValues
import dev.wmos.data.prefs.DraftStore
import dev.wmos.data.repo.ConflictException
import dev.wmos.ui.container
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SaveState { SAVED, DIRTY, SAVING, ERROR }

data class EditorState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val title: String = "",
    val content: String = "",
    val type: String = KnowledgeTypeValues.NOTE,
    val folderId: String? = null,
    val folderName: String? = null,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val saveState: SaveState = SaveState.SAVED,
    val errorMessage: String? = null,
    val revision: Int = 0,
    val updatedAt: Long = 0L,
    val draftOffer: DraftStore.Draft? = null,
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentSize: Long = 0L,
)

/**
 * Editor with debounced autosave.
 *
 * Ordering matters: the database write is attempted first, and only then is the recoverable
 * draft cleared. If saving fails, the draft survives and the status says so.
 */
class NoteEditorViewModel(
    private val container: AppContainer,
    private val itemId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state

    private var saveJob: Job? = null
    private var loaded = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val item = container.brain.item(itemId)
        if (item == null) {
            _state.value = EditorState(loading = false, missing = true)
            return
        }
        val tags = container.brain.observeTagsForItem(itemId).first().map { it.name }
        val folder = item.folderId?.let { container.brain.folder(it)?.name }
        val draft = container.drafts.read(itemId)?.takeIf { it.savedAt > item.updatedAt }
        val attachment = item.attachmentUri
        loaded = true
        _state.value = EditorState(
            loading = false,
            title = item.title,
            content = item.content,
            type = item.type,
            folderId = item.folderId,
            folderName = folder,
            tags = tags,
            isFavorite = item.isFavorite,
            isPinned = item.isPinned,
            isArchived = item.isArchived,
            revision = item.revision,
            updatedAt = item.updatedAt,
            draftOffer = draft,
            attachmentPath = attachment,
            attachmentName = attachment?.let { container.attachments.displayName(it) },
            attachmentSize = attachment?.let { container.attachments.sizeOf(it) } ?: 0L,
        )
    }

    fun onTitleChange(value: String) {
        if (!loaded) return
        _state.value = _state.value.copy(title = value, saveState = SaveState.DIRTY)
        scheduleSave()
    }

    fun onContentChange(value: String) {
        if (!loaded) return
        _state.value = _state.value.copy(content = value, saveState = SaveState.DIRTY)
        scheduleSave()
    }

    private fun scheduleSave() {
        container.drafts.save(itemId, _state.value.title, _state.value.content)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            save()
        }
    }

    suspend fun save() {
        val current = _state.value
        if (current.loading || current.missing) return
        _state.value = current.copy(saveState = SaveState.SAVING, errorMessage = null)
        val result = container.brain.saveItem(
            id = itemId,
            title = current.title,
            content = current.content,
            expectedRevision = current.revision.takeIf { it > 0 },
        )
        result.onSuccess { revision ->
            container.drafts.clear(itemId)
            _state.value = _state.value.copy(
                saveState = SaveState.SAVED,
                revision = revision,
                updatedAt = System.currentTimeMillis(),
            )
        }.onFailure { error ->
            _state.value = _state.value.copy(
                saveState = SaveState.ERROR,
                errorMessage = when (error) {
                    is ConflictException -> "Запись изменена в другом месте. Ваш текст сохранён как черновик."
                    else -> error.message ?: "Не удалось сохранить"
                },
            )
        }
    }

    fun retrySave() {
        viewModelScope.launch { save() }
    }

    fun restoreDraft() {
        val draft = _state.value.draftOffer ?: return
        _state.value = _state.value.copy(
            title = draft.title,
            content = draft.content,
            draftOffer = null,
        )
        viewModelScope.launch { save() }
    }

    fun discardDraft() {
        container.drafts.clear(itemId)
        _state.value = _state.value.copy(draftOffer = null)
    }

    fun setFolder(folderId: String?, folderName: String?) {
        _state.value = _state.value.copy(folderId = folderId, folderName = folderName, saveState = SaveState.DIRTY)
        viewModelScope.launch {
            container.brain.updateItem(itemId) { it.copy(folderId = folderId) }
            _state.value = _state.value.copy(saveState = SaveState.SAVED)
        }
    }

    fun addTag(raw: String) {
        val tag = raw.trim().removePrefix("#").lowercase()
        if (tag.isBlank() || _state.value.tags.contains(tag)) return
        val updated = _state.value.tags + tag
        _state.value = _state.value.copy(tags = updated)
        viewModelScope.launch { container.brain.setTags(itemId, updated) }
    }

    fun removeTag(tag: String) {
        val updated = _state.value.tags - tag
        _state.value = _state.value.copy(tags = updated)
        viewModelScope.launch { container.brain.setTags(itemId, updated) }
    }

    fun toggleFavorite() {
        val next = !_state.value.isFavorite
        _state.value = _state.value.copy(isFavorite = next)
        viewModelScope.launch { container.brain.toggleFavorite(itemId) }
    }

    fun togglePinned() {
        val next = !_state.value.isPinned
        _state.value = _state.value.copy(isPinned = next)
        viewModelScope.launch { container.brain.togglePinned(itemId) }
    }

    fun toggleArchived() {
        val next = !_state.value.isArchived
        _state.value = _state.value.copy(isArchived = next)
        viewModelScope.launch { container.brain.toggleArchived(itemId) }
    }

    fun deleteForeverShell() {
        viewModelScope.launch { container.brain.moveToTrash(itemId) }
    }

    /**
     * Copies a user-selected document into private storage. The original file is never modified
     * or deleted, and the SAF grant is not kept — only our own copy remains.
     */
    fun attachFile(uri: android.net.Uri, mimeType: String?) {
        viewModelScope.launch {
            container.attachments.attach(itemId, uri, mimeType)
                .onSuccess { path ->
                    _state.value = _state.value.copy(
                        attachmentPath = path,
                        attachmentName = container.attachments.displayName(path),
                        attachmentSize = container.attachments.sizeOf(path),
                        errorMessage = null,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = error.message ?: "Не удалось прикрепить файл",
                    )
                }
        }
    }

    fun detachFile() {
        viewModelScope.launch {
            container.attachments.detach(itemId).onSuccess {
                _state.value = _state.value.copy(
                    attachmentPath = null,
                    attachmentName = null,
                    attachmentSize = 0L,
                )
            }
        }
    }

    fun attachmentUri(): android.net.Uri? =
        _state.value.attachmentPath?.let { container.attachments.uriFor(it) }

    fun markdown(): String {
        val s = _state.value
        val header = if (s.title.isBlank()) "" else "# ${s.title}\n\n"
        val tags = if (s.tags.isEmpty()) "" else "\n\n" + s.tags.joinToString(" ") { "#$it" }
        return header + s.content + tags
    }

    /**
     * Last-resort flush. The ViewModel scope is already cancelled here, so the write runs on the
     * application scope; the draft stays on disk if this write fails.
     */
    override fun onCleared() {
        super.onCleared()
        saveJob?.cancel()
        val snapshot = _state.value
        if (!snapshot.loading && !snapshot.missing && snapshot.saveState != SaveState.SAVED) {
            container.drafts.save(itemId, snapshot.title, snapshot.content)
            container.appScope.launch {
                container.brain.saveItem(
                    id = itemId,
                    title = snapshot.title,
                    content = snapshot.content,
                )
            }
        }
    }

    companion object {
        fun factory(itemId: String) = viewModelFactory {
            initializer { NoteEditorViewModel(container(), itemId) }
        }
    }
}
