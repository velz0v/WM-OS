package dev.wmos.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Stores user-supplied provider API keys.
 *
 * The key material lives in [EncryptedSharedPreferences], whose AES-256-GCM master key is
 * generated and wrapped by the Android Keystore and never leaves it. The database only ever
 * stores a [String] alias; the plaintext key is never written to Room, logs, backups or the UI.
 */
class SecretStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "wm_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun put(alias: String, secret: String) {
        prefs.edit().putString(alias, secret).apply()
    }

    fun get(alias: String): String? = runCatching { prefs.getString(alias, null) }.getOrNull()

    fun delete(alias: String) {
        prefs.edit().remove(alias).apply()
    }

    fun exists(alias: String): Boolean = !get(alias).isNullOrBlank()

    fun mask(alias: String): String {
        val value = get(alias) ?: return ""
        if (value.length <= 8) return "••••••••"
        return value.take(4) + "…" + value.takeLast(4)
    }

    /** Diagnostic-safe fingerprint. Never reversible into the key. */
    fun fingerprint(alias: String): String {
        val value = get(alias) ?: return "absent"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP).take(8)
    }

    companion object {
        fun newAlias(providerId: String): String {
            val bytes = ByteArray(6)
            SecureRandom().nextBytes(bytes)
            return "prov_${providerId}_${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        }
    }
}
