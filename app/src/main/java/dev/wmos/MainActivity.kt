package dev.wmos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.wmos.data.prefs.AppSettings
import dev.wmos.data.prefs.ThemeMode
import dev.wmos.ui.nav.WmApp
import dev.wmos.ui.screens.lock.LockScreen
import dev.wmos.ui.theme.WmTheme
import kotlinx.coroutines.launch

/**
 * A FragmentActivity (not a plain ComponentActivity) because the biometric unlock prompt
 * from androidx.biometric has to be hosted inside a FragmentActivity.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WmApplication).container
        handleShareIntent(intent)

        // Lock the app whenever it leaves the foreground; the PIN is checked back in LockScreen.
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    container.appLock.lock()
                }
            },
        )

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val locked by container.appLock.locked.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            LaunchedEffect(settings.appLockEnabled) {
                container.appLock.syncEnabled(settings.appLockEnabled)
            }
            WmTheme(darkTheme = dark) {
                if (locked) {
                    LockScreen(
                        repository = container.appLockRepository,
                        manager = container.appLock,
                        onUnlocked = { /* manager state already flipped to unlocked */ },
                    )
                } else {
                    WmApp(container)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * Share capture is intentionally "save first, think later": the material is written to Inbox
     * before any analysis, so a dead network or a missing API key never loses it.
     */
    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val container = (application as WmApplication).container
        val source = runCatching { referrer?.authority ?: referrer?.host }.getOrNull()

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val html = intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
                val stream: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                val type = intent.type
                if (stream != null) {
                    lifecycleScope.launch {
                        container.inbox.captureUri(stream, type, source)
                        container.openInboxSignal.value = true
                    }
                } else if (!text.isNullOrBlank()) {
                    lifecycleScope.launch {
                        container.inbox.captureText(text, source)
                        container.openInboxSignal.value = true
                    }
                } else if (!html.isNullOrBlank()) {
                    lifecycleScope.launch {
                        container.inbox.captureText(html, source)
                        container.openInboxSignal.value = true
                    }
                }
            }

            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                if (!text.isNullOrBlank()) {
                    lifecycleScope.launch {
                        container.inbox.captureText(text, source)
                        container.openInboxSignal.value = true
                    }
                }
            }
        }
    }
}
