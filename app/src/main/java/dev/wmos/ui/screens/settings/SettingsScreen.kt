package dev.wmos.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.data.prefs.AppSettings
import dev.wmos.data.prefs.ThemeMode
import dev.wmos.security.PinHasher
import dev.wmos.ui.components.SectionHeader
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.container
import dev.wmos.ui.theme.LocalWmColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun pinIsSet(): Boolean = container.appLock.hasPin()

    fun biometricAllowed(): Boolean = container.appLockRepository.biometricAllowed()

    fun setupPin(pin: String, repeat: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = when {
                pin != repeat -> "PIN-коды не совпадают"
                else -> container.appLockRepository.setPin(pin).exceptionOrNull()?.message
            }
            if (error == null) {
                container.settings.setAppLock(true)
                container.appLock.syncEnabled(true)
                container.appLock.unlock()
            }
            onResult(error)
        }
    }

    fun changePin(current: String, newPin: String, repeat: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = when {
                newPin != repeat -> "Новые PIN-коды не совпадают"
                else -> container.appLockRepository.changePin(current, newPin).exceptionOrNull()?.message
            }
            onResult(error)
        }
    }

    fun disableLock(currentPin: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = when (val check = container.appLockRepository.check(currentPin)) {
                dev.wmos.security.PinCheck.Success -> {
                    container.appLockRepository.clearPin()
                    container.settings.setAppLock(false)
                    null
                }

                dev.wmos.security.PinCheck.NoPin -> {
                    container.settings.setAppLock(false)
                    null
                }

                is dev.wmos.security.PinCheck.Wrong -> "Текущий PIN неверный"
                is dev.wmos.security.PinCheck.LockedOut -> "Подождите ${check.remainingMs / 1000} с"
            }
            onResult(error)
        }
    }

    fun setBiometric(enabled: Boolean, onResult: (String?) -> Unit) {
        val error = if (enabled && !container.appLock.hasPin()) "Сначала настройте PIN" else null
        if (error == null) {
            container.appLockRepository.setBiometricAllowed(enabled)
        }
        onResult(error)
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { container.settings.setThemeMode(mode) }
    }

    fun setSendNoteContent(enabled: Boolean) {
        viewModelScope.launch { container.settings.setSendNoteContentToAi(enabled) }
    }

    fun setAiAutoSwitch(enabled: Boolean) {
        viewModelScope.launch { container.settings.setAiAutoSwitch(enabled) }
    }

    fun setCompact(enabled: Boolean) {
        viewModelScope.launch { container.settings.setCompactLists(enabled) }
    }

    fun setTrashRetention(days: Int) {
        viewModelScope.launch { container.settings.setTrashRetentionDays(days) }
    }

    companion object {
        val Factory = viewModelFactory { initializer { SettingsViewModel(container()) } }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text("Настройки") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { SectionHeader("Внешний вид") }
            item {
                WmCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Тема", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setTheme(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> "Системная"
                                            ThemeMode.DARK -> "Тёмная"
                                            ThemeMode.LIGHT -> "Светлая"
                                        },
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        label = "Компактные списки",
                        checked = settings.compactLists,
                        onChange = viewModel::setCompact,
                    )
                }
            }

            item { SectionHeader("Напоминания") }
            item {
                WmCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Системные уведомления", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Разрешение запрашивается при создании первого напоминания. " +
                            "Если оно отключено, напоминание сохранится, но сигнала не будет.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            } else {
                                Intent(Settings.ACTION_SETTINGS)
                            }
                            runCatching { context.startActivity(intent) }
                        },
                    ) { Text("Открыть настройки уведомлений") }
                }
            }

            item { SectionHeader("Данные") }
            item {
                WmCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Корзина", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Удалённые записи хранятся до очистки корзины. Автоматическое удаление по таймеру пока не включено — " +
                            "записи не исчезают скрыто.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionHeader("Граус и приватность") }
            item {
                WmCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SwitchRow(
                        label = "Отправлять текст заметок в контексте",
                        checked = settings.sendNoteContentToAi,
                        onChange = viewModel::setSendNoteContent,
                    )
                    Text(
                        "По умолчанию выключено: в модель уходят только ваши сообщения и результаты поиска по ID.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    SwitchRow(
                        label = "Автопереключение провайдера",
                        checked = settings.aiAutoSwitchEnabled,
                        onChange = viewModel::setAiAutoSwitch,
                    )
                    Text(
                        "Выключено по умолчанию. Смена провайдера меняет то, кто получает ваши данные, поэтому " +
                            "не должна происходить незаметно.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionHeader("Безопасность") }
            item {
                LockSection(viewModel = viewModel)
            }

            item { SectionHeader("Приложение") }
            item {
                WmCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = onOpenAbout,
                ) {
                    Text("О приложении", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "WM OS 0.1.0 · MIT",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(top = 12.dp),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private enum class PinDialogMode { SETUP, CHANGE, DISABLE }

@Composable
private fun LockSection(viewModel: SettingsViewModel) {
    val extras = LocalWmColors.current
    var pinIsSet by remember { mutableStateOf(viewModel.pinIsSet()) }
    var dialog by remember { mutableStateOf<PinDialogMode?>(null) }
    var biometricEnabled by remember { mutableStateOf(false) }
    var bioError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pinIsSet) {
        biometricEnabled = if (pinIsSet) viewModel.biometricAllowed() else false
    }

    WmCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Блокировка приложения", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "PIN хранится как хэш в Keystore-контейнере. После пяти неверных попыток ввод блокируется на 30 секунд.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        if (!pinIsSet) {
            TextButton(onClick = { dialog = PinDialogMode.SETUP }) {
                Text("Настроить PIN")
            }
        } else {
            SwitchRow(
                label = "Разблокировка по биометрии",
                checked = biometricEnabled,
                onChange = { enabled ->
                    viewModel.setBiometric(enabled) { error ->
                        bioError = error
                        if (error == null) biometricEnabled = enabled
                    }
                },
            )
            bioError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.danger,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = { dialog = PinDialogMode.CHANGE }) {
                    Text("Сменить PIN")
                }
                TextButton(onClick = { dialog = PinDialogMode.DISABLE }) {
                    Text("Отключить блокировку", color = extras.danger)
                }
            }
        }
    }

    dialog?.let { mode ->
        PinDialog(
            mode = mode,
            viewModel = viewModel,
            onDismiss = { dialog = null },
            onSuccess = { pinIsSet = viewModel.pinIsSet() },
        )
    }
}

@Composable
private fun PinDialog(
    mode: PinDialogMode,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val extras = LocalWmColors.current
    val keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)

    fun acceptPin(value: String): String {
        val filtered = value.filter { it.isDigit() }
        return if (filtered.length > PinHasher.MAX_LENGTH) {
            filtered.take(PinHasher.MAX_LENGTH)
        } else {
            filtered
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (mode) {
                    PinDialogMode.SETUP -> "Новый PIN"
                    PinDialogMode.CHANGE -> "Сменить PIN"
                    PinDialogMode.DISABLE -> "Отключить блокировку"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                if (mode != PinDialogMode.SETUP) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = acceptPin(it) },
                        label = { Text("Текущий PIN") },
                        singleLine = true,
                        keyboardOptions = keyboardOptions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (mode != PinDialogMode.DISABLE) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = acceptPin(it) },
                        label = { Text("Новый PIN (${PinHasher.MIN_LENGTH}–${PinHasher.MAX_LENGTH} цифр)") },
                        singleLine = true,
                        keyboardOptions = keyboardOptions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = acceptPin(it) },
                        label = { Text("Повторите PIN") },
                        singleLine = true,
                        keyboardOptions = keyboardOptions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = extras.danger)
                }
            }
        },
        confirmButton = {
            val callback: (String?) -> Unit = { err ->
                if (err == null) {
                    onSuccess()
                    onDismiss()
                } else {
                    error = err
                }
            }
            TextButton(
                onClick = {
                    when (mode) {
                        PinDialogMode.SETUP -> viewModel.setupPin(newPin, repeat, callback)
                        PinDialogMode.CHANGE -> viewModel.changePin(current, newPin, repeat, callback)
                        PinDialogMode.DISABLE -> viewModel.disableLock(current, callback)
                    }
                },
            ) {
                Text(if (mode == PinDialogMode.DISABLE) "Отключить" else "Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text("О приложении") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                WmCard {
                    Text("WM OS 0.1.0", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Локальное пространство знаний, задач и напоминаний с помощником Граусом.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Что работает")
                WmCard {
                    Text("• Заметки, памятки, ссылки: создание, автосохранение, поиск, теги, корзина")
                    Text("• Папки с защитой от циклов")
                    Text("• Задачи и проекты с представлениями")
                    Text("• Напоминания: AlarmManager, повторы, действия из уведомления")
                    Text("• Входящие: ручное добавление и Android Share")
                    Text("• AI Lab: свои ключи, проверка подключения, Keystore")
                    Text("• Граус: чат с инструментами и подтверждениями")
                    Text("• Экспорт JSON и Markdown")
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Ограничения этой версии")
                WmCard {
                    Text("• Импорт/восстановление не реализованы")
                    Text("• Нет полного шифрования базы")
                    Text("• Нет семантического поиска, OCR и Autopilot")
                    Text("• Точность доставки напоминаний зависит от Android")
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Лицензия MIT. Сторонние библиотеки: Jetpack Compose, Room, OkHttp, kotlinx.serialization.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
