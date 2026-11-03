package dev.wmos.ui.components

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide transient feedback with an optional undo action.
 *
 * A single host lives in [dev.wmos.ui.nav.WmApp], so any screen can report the outcome of an
 * action without owning its own snackbar surface. Undo callbacks are ordinary lambdas — they are
 * never persisted, which is correct: an undo that survives a restart would be a lie.
 */
data class SnackMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

class SnackbarController {
    private val _messages = MutableSharedFlow<SnackMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun show(message: SnackMessage) {
        _messages.tryEmit(message)
    }

    fun show(text: String) = show(SnackMessage(text))
}
