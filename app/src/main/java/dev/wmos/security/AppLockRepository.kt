package dev.wmos.security

import android.content.Context
import dev.wmos.data.prefs.SecretStore

sealed interface PinCheck {
    data object Success : PinCheck

    data object NoPin : PinCheck

    data class Wrong(val attemptsLeft: Int) : PinCheck

    data class LockedOut(val remainingMs: Long) : PinCheck
}

/**
 * PIN and lockout state.
 *
 * Salt and hash live in the keystore-backed encrypted preferences, never in Room and never in a
 * backup. After [MAX_ATTEMPTS] wrong tries the input is throttled for [LOCKOUT_MS]; a restart does
 * not reset the counter, because the deadline is persisted.
 */
class AppLockRepository(context: Context, private val secrets: SecretStore) {
    private val prefs = context.applicationContext.getSharedPreferences("wm_app_lock", Context.MODE_PRIVATE)

    val isPinSet: Boolean
        get() = secrets.get(KEY_SALT) != null && secrets.get(KEY_HASH) != null

    fun setPin(pin: String): Result<Unit> {
        if (!PinHasher.isValidPin(pin)) {
            return Result.failure(
                IllegalArgumentException("PIN должен состоять из ${PinHasher.MIN_LENGTH}–${PinHasher.MAX_LENGTH} цифр"),
            )
        }
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        secrets.put(KEY_SALT, PinHasher.encode(salt))
        secrets.put(KEY_HASH, PinHasher.encode(hash))
        prefs.edit().putInt(KEY_ATTEMPTS, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply()
        return Result.success(Unit)
    }

    fun changePin(currentPin: String, newPin: String): Result<Unit> {
        return when (val check = check(currentPin)) {
            PinCheck.Success -> setPin(newPin)
            is PinCheck.Wrong -> Result.failure(IllegalArgumentException("Текущий PIN неверный"))
            is PinCheck.LockedOut -> Result.failure(IllegalStateException("Ввод временно заблокирован"))
            PinCheck.NoPin -> setPin(newPin)
        }
    }

    fun clearPin() {
        secrets.delete(KEY_SALT)
        secrets.delete(KEY_HASH)
        prefs.edit().putInt(KEY_ATTEMPTS, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply()
    }

    fun remainingLockoutMs(now: Long = System.currentTimeMillis()): Long {
        val until = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        return (until - now).coerceAtLeast(0L)
    }

    fun check(pin: String, now: Long = System.currentTimeMillis()): PinCheck {
        if (!isPinSet) return PinCheck.NoPin
        val lockout = remainingLockoutMs(now)
        if (lockout > 0L) return PinCheck.LockedOut(lockout)

        val salt = secrets.get(KEY_SALT)?.let(PinHasher::decode) ?: return PinCheck.NoPin
        val expected = secrets.get(KEY_HASH)?.let(PinHasher::decode) ?: return PinCheck.NoPin
        return if (PinHasher.verify(pin, salt, expected)) {
            prefs.edit().putInt(KEY_ATTEMPTS, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply()
            PinCheck.Success
        } else {
            val attempts = prefs.getInt(KEY_ATTEMPTS, 0) + 1
            if (attempts >= MAX_ATTEMPTS) {
                prefs.edit()
                    .putInt(KEY_ATTEMPTS, 0)
                    .putLong(KEY_LOCKED_UNTIL, now + LOCKOUT_MS)
                    .apply()
                PinCheck.LockedOut(LOCKOUT_MS)
            } else {
                prefs.edit().putInt(KEY_ATTEMPTS, attempts).apply()
                PinCheck.Wrong(attemptsLeft = MAX_ATTEMPTS - attempts)
            }
        }
    }

    fun biometricAllowed(): Boolean = prefs.getBoolean(KEY_BIOMETRIC, false)

    fun setBiometricAllowed(allowed: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, allowed).apply()
    }

    companion object {
        private const val KEY_SALT = "app_lock_salt"
        private const val KEY_HASH = "app_lock_hash"
        private const val KEY_ATTEMPTS = "app_lock_attempts"
        private const val KEY_LOCKED_UNTIL = "app_lock_locked_until"
        private const val KEY_BIOMETRIC = "app_lock_biometric"
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
    }
}
