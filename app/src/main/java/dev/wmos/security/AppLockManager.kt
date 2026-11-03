package dev.wmos.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory lock state. It is intentionally not persisted: after a cold start the app must be
 * locked again, and an unlocked flag on disk would be a security hole, not a convenience.
 */
class AppLockManager(private val repository: AppLockRepository) {
    private val _enabled = MutableStateFlow(false)
    private val _locked = MutableStateFlow(false)

    /** True while the unlock screen must be shown. */
    val locked: StateFlow<Boolean> = _locked

    val enabled: Boolean
        get() = _enabled.value

    /** Called when the setting or the stored PIN changes. */
    fun syncEnabled(enabledNow: Boolean) {
        val wasEnabled = _enabled.value
        _enabled.value = enabledNow
        when {
            !enabledNow -> _locked.value = false
            !wasEnabled -> _locked.value = true
        }
    }

    /** Called when the app goes to the background. */
    fun lock() {
        if (_enabled.value) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    fun hasPin(): Boolean = repository.isPinSet
}
