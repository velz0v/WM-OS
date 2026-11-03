package dev.wmos.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "wm_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLockEnabled: Boolean = false,
    val aiAutoSwitchEnabled: Boolean = false,
    val sendNoteContentToAi: Boolean = false,
    val defaultReminderMinutesBefore: Int = 0,
    val dailyBriefingEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val exportTreeUri: String? = null,
    val onboardingDone: Boolean = false,
    val trashRetentionDays: Int = 30,
    val compactLists: Boolean = false,
    val hapticsEnabled: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val appLock = booleanPreferencesKey("app_lock")
        val aiAutoSwitch = booleanPreferencesKey("ai_auto_switch")
        val sendNoteContent = booleanPreferencesKey("send_note_content")
        val defaultReminderMinutes = intPreferencesKey("default_reminder_minutes")
        val dailyBriefing = booleanPreferencesKey("daily_briefing")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val exportTreeUri = stringPreferencesKey("export_tree_uri")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val trashRetentionDays = intPreferencesKey("trash_retention_days")
        val compactLists = booleanPreferencesKey("compact_lists")
        val haptics = booleanPreferencesKey("haptics")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.themeMode] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM),
            appLockEnabled = prefs[Keys.appLock] ?: false,
            aiAutoSwitchEnabled = prefs[Keys.aiAutoSwitch] ?: false,
            sendNoteContentToAi = prefs[Keys.sendNoteContent] ?: false,
            defaultReminderMinutesBefore = prefs[Keys.defaultReminderMinutes] ?: 0,
            dailyBriefingEnabled = prefs[Keys.dailyBriefing] ?: false,
            lastBackupAt = prefs[Keys.lastBackupAt],
            exportTreeUri = prefs[Keys.exportTreeUri],
            onboardingDone = prefs[Keys.onboardingDone] ?: false,
            trashRetentionDays = prefs[Keys.trashRetentionDays] ?: 30,
            compactLists = prefs[Keys.compactLists] ?: false,
            hapticsEnabled = prefs[Keys.haptics] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.settingsDataStore.edit { it[Keys.themeMode] = mode.name }

    suspend fun setAppLock(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.appLock] = enabled }

    suspend fun setAiAutoSwitch(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.aiAutoSwitch] = enabled }

    suspend fun setSendNoteContentToAi(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.sendNoteContent] = enabled }

    suspend fun setDefaultReminderMinutes(minutes: Int) =
        context.settingsDataStore.edit { it[Keys.defaultReminderMinutes] = minutes }

    suspend fun setDailyBriefing(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.dailyBriefing] = enabled }

    suspend fun setLastBackupAt(at: Long) = context.settingsDataStore.edit { it[Keys.lastBackupAt] = at }

    suspend fun setExportTreeUri(uri: String?) = context.settingsDataStore.edit {
        if (uri == null) it.remove(Keys.exportTreeUri) else it[Keys.exportTreeUri] = uri
    }

    suspend fun setOnboardingDone(done: Boolean) = context.settingsDataStore.edit { it[Keys.onboardingDone] = done }

    suspend fun setTrashRetentionDays(days: Int) = context.settingsDataStore.edit { it[Keys.trashRetentionDays] = days }

    suspend fun setCompactLists(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.compactLists] = enabled }

    suspend fun setHaptics(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.haptics] = enabled }
}
