package dev.wmos.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN hashing with PBKDF2-HMAC-SHA256.
 *
 * The PIN itself is never stored. Only a random salt and the derived hash are persisted, and the
 * comparison is constant-time. Keystore is not involved here on purpose: the hash must survive a
 * Keystore reset for the "wrong PIN after restore" case to be explainable.
 */
object PinHasher {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: ByteArray, expectedHash: ByteArray): Boolean {
        val actual = hash(pin, salt)
        return MessageDigest.isEqual(actual, expectedHash)
    }

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(value: String): ByteArray? = runCatching { Base64.getDecoder().decode(value) }.getOrNull()
}
