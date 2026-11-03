package dev.wmos.ui.screens.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.wmos.security.AppLockManager
import dev.wmos.security.AppLockRepository
import dev.wmos.security.PinCheck
import dev.wmos.security.PinHasher
import dev.wmos.ui.theme.LocalWmColors
import kotlinx.coroutines.delay

/**
 * Unlock screen: PIN pad plus optional biometrics.
 *
 * The biometric prompt is offered only when the user enabled it and the device supports it.
 * Failed attempts and the lockout deadline live in [AppLockRepository], so killing the app does
 * not reset them.
 */
@Composable
fun LockScreen(
    repository: AppLockRepository,
    manager: AppLockManager,
    onUnlocked: () -> Unit,
) {
    val extras = LocalWmColors.current
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockoutMs by remember { mutableLongStateOf(repository.remainingLockoutMs()) }
    var attemptsLeft by remember { mutableIntStateOf(-1) }
    val biometricAllowed = remember { repository.biometricAllowed() }

    LaunchedEffect(lockoutMs) {
        if (lockoutMs > 0L) {
            delay(1_000)
            lockoutMs = repository.remainingLockoutMs()
        }
    }

    val canUseBiometrics = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun submit() {
        when (val result = repository.check(pin)) {
            PinCheck.Success -> {
                pin = ""
                error = null
                manager.unlock()
                onUnlocked()
            }

            PinCheck.NoPin -> {
                manager.unlock()
                onUnlocked()
            }

            is PinCheck.Wrong -> {
                attemptsLeft = result.attemptsLeft
                error = "Неверный PIN. Осталось попыток: ${result.attemptsLeft}"
                pin = ""
            }

            is PinCheck.LockedOut -> {
                lockoutMs = result.remainingMs
                error = "Слишком много попыток. Подождите ${result.remainingMs / 1000} с."
                pin = ""
            }
        }
    }

    fun launchBiometrics() {
        val host = activity ?: return
        val prompt = BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    manager.unlock()
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    error = "Биометрия недоступна: $errString"
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Разблокировать WM OS")
            .setSubtitle("Подтвердите личность")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        prompt.authenticate(info)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text("WM OS заблокирован", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Введите PIN, чтобы продолжить",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(PinHasher.MAX_LENGTH) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                color = if (index < pin.length) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                                shape = CircleShape,
                            )
                            .border(1.dp, extras.outlineSubtle, CircleShape),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = error ?: if (attemptsLeft > 0) "Осталось попыток: $attemptsLeft" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) extras.danger else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            Keypad(
                enabled = lockoutMs <= 0L,
                onDigit = { digit ->
                    if (pin.length < PinHasher.MAX_LENGTH) {
                        pin += digit
                        error = null
                    }
                },
                onBackspace = { pin = pin.dropLast(1) },
                onConfirm = { submit() },
                confirmEnabled = pin.length >= PinHasher.MIN_LENGTH,
            )

            if (canUseBiometrics && biometricAllowed) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { launchBiometrics() }) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                    Text("  Войти по биометрии")
                }
            }

            if (lockoutMs > 0L) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Блокировка ввода: ${lockoutMs / 1000} с",
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.warning,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Данные хранятся только на этом устройстве",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Keypad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(76.dp))
                        "⌫" -> KeyButton(
                            label = null,
                            icon = true,
                            enabled = enabled,
                            onClick = onBackspace,
                        )

                        else -> KeyButton(label = key, enabled = enabled, onClick = { onDigit(key) })
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onConfirm, enabled = confirmEnabled && enabled) {
            Text("Разблокировать", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun KeyButton(
    label: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(22.dp),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            Icon(
                Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Стереть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = label.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
