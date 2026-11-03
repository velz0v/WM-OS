package dev.wmos.ui.screens.graus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.ai.PendingAction
import dev.wmos.data.db.entity.AiProviderEntity
import dev.wmos.data.db.entity.ChatMessageEntity
import dev.wmos.data.db.entity.ConversationEntity
import dev.wmos.ui.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GrausViewModel(private val container: AppContainer) : ViewModel() {
    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _pending = MutableStateFlow<PendingAction?>(null)
    val pending: StateFlow<PendingAction?> = _pending

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _toolNotes = MutableStateFlow<List<String>>(emptyList())
    val toolNotes: StateFlow<List<String>> = _toolNotes

    /** Live answer text while the provider streams. Null = idle. */
    private val _streaming = MutableStateFlow<String?>(null)
    val streaming: StateFlow<String?> = _streaming

    val conversations: StateFlow<List<ConversationEntity>> = container.chat.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val provider: StateFlow<AiProviderEntity?> = container.providers.observeAll()
        .map { list -> list.firstOrNull { it.isDefault } ?: list.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessageEntity>> = _conversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.chat.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun newConversation() {
        viewModelScope.launch {
            _conversationId.value = container.chat.createConversation()
            _pending.value = null
            _error.value = null
            _toolNotes.value = emptyList()
        }
    }

    fun openConversation(id: String) {
        _conversationId.value = id
        _pending.value = null
        _error.value = null
        _toolNotes.value = emptyList()
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _sending.value) return
        viewModelScope.launch {
            val conversationId = _conversationId.value ?: container.chat.createConversation().also {
                _conversationId.value = it
            }
            _sending.value = true
            _error.value = null
            _toolNotes.value = emptyList()
            _streaming.value = null
            container.chat.appendMessage(conversationId, "user", trimmed)
            // messages.value may lag one emission behind the appended user message — drop it if so.
            val history = messages.value
                .filter { it.content.isNotBlank() }
                .let { list ->
                    val last = list.lastOrNull()
                    if (last?.role == "user" && last.content == trimmed) list.dropLast(1) else list
                }
            val reply = withContext(Dispatchers.IO) {
                container.graus.ask(
                    conversationId,
                    history,
                    trimmed,
                    onDelta = { delta -> if (delta.isNotEmpty()) _streaming.value = delta },
                )
            }
            _streaming.value = null
            when {
                reply.error != null -> {
                    _error.value = reply.error
                }

                reply.pending != null -> {
                    _pending.value = reply.pending
                    if (reply.text.isNotBlank()) {
                        container.chat.appendMessage(conversationId, "assistant", reply.text)
                    }
                }

                else -> {
                    container.chat.appendMessage(conversationId, "assistant", reply.text)
                    _toolNotes.value = reply.toolNotes
                }
            }
            _sending.value = false
        }
    }

    fun confirmPending() {
        val pending = _pending.value ?: return
        val conversationId = _conversationId.value ?: return
        viewModelScope.launch {
            _sending.value = true
            _pending.value = null
            _streaming.value = null
            val reply = withContext(Dispatchers.IO) {
                container.graus.confirm(
                    pending,
                    conversationId,
                    onDelta = { delta -> if (delta.isNotEmpty()) _streaming.value = delta },
                )
            }
            _streaming.value = null
            if (reply.error != null) {
                _error.value = reply.error
            } else {
                container.chat.appendMessage(conversationId, "assistant", reply.text)
                _toolNotes.value = reply.toolNotes
            }
            _sending.value = false
        }
    }

    fun cancelPending() {
        _pending.value = null
        _conversationId.value?.let { container.graus.cancelPending(it) }
    }

    fun dismissError() {
        _error.value = null
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            container.chat.deleteConversation(id)
            if (_conversationId.value == id) _conversationId.value = null
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { GrausViewModel(container()) }
        }
    }
}
