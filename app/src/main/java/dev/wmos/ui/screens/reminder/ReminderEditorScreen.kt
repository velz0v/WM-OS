package dev.wmos.ui.screens.reminder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wmos.domain.reminder.RecurrenceCalculator
import dev.wmos.domain.reminder.SchedulePrecision
import dev.wmos.ui.components.ConfirmDialog
import dev.wmos.ui.components.StatusPill
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.theme.LocalWmColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun ReminderEditorScreen(
    reminderId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ReminderEditorViewModel = viewModel(factory = ReminderEditorViewModel.factory(reminderId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val extras = LocalWmColors.current
    var confirmDelete by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !state.notificationsAllowed) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Удалить напоминание?",
            message = "Запланированный сигнал будет отменён. Это действие нельзя отменить.",
            confirmLabel = "Удалить напоминание",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text(if (state.isNew) "Новое напоминание" else "Напоминание") },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            if (!state.notificationsAllowed) {
                PermissionCard(
                    title = "Уведомления выключены",
                    message = "Напоминание сохранится, но система не покажет сигнал. Разрешите уведомления в настройках.",
                    actionLabel = "Открыть настройки",
                    onAction = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        context.startActivity(intent)
                    },
                )
            }

            if (!state.exactAllowed) {
                PermissionCard(
                    title = "Точные будильники запрещены",
                    message = "Сигнал может прийти с задержкой: Android ограничивает точные будильники. " +
                        "Мы не будем обещать точность, которой нет.",
                    actionLabel = "Разрешить",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(Uri.parse("package:${context.packageName}")),
                                )
                            }
                        }
                        viewModel.refreshPermissions()
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.mode == ReminderMode.NATURAL,
                    onClick = { viewModel.setMode(ReminderMode.NATURAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Текстом") }
                SegmentedButton(
                    selected = state.mode == ReminderMode.MANUAL,
                    onClick = { viewModel.setMode(ReminderMode.MANUAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Вручную") }
            }

            Spacer(Modifier.height(16.dp))

            if (state.mode == ReminderMode.NATURAL) {
                OutlinedTextField(
                    value = state.naturalText,
                    onValueChange = viewModel::onNaturalTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Например: завтра в 18:30 проверить сервер") },
                    minLines = 2,
                    supportingText = {
                        Text("Поддерживается: «через 45 минут», «каждый понедельник в 10:00», «каждый день в 09:00»")
                    },
                )
                Spacer(Modifier.height(12.dp))
                PreviewCard(state)
            } else {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название") },
                    singleLine = true,
                    isError = state.error != null && state.title.isBlank(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Описание (необязательно)") },
                    minLines = 2,
                )
                Spacer(Modifier.height(16.dp))
                Text("Когда", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayChip("Сегодня", LocalDate.now(), state.date) { viewModel.setDate(it) }
                    DayChip("Завтра", LocalDate.now().plusDays(1), state.date) { viewModel.setDate(it) }
                    DayChip("+7 дней", LocalDate.now().plusDays(7), state.date) { viewModel.setDate(it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showTimePicker = true }) {
                        Icon(Icons.Outlined.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("%02d:%02d".format(state.time.hour, state.time.minute))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Повтор", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepeatChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = state.repeat == choice,
                            onClick = { viewModel.setRepeat(choice) },
                            label = { Text(choice.label) },
                        )
                    }
                }
                if (state.repeat == RepeatChoice.WEEKLY) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { day ->
                            FilterChip(
                                selected = state.weekdays.contains(day),
                                onClick = { viewModel.toggleWeekday(day) },
                                label = { Text(RecurrenceCalculator.dayLabel(day)) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                PreviewCard(state)
            }

            state.saveResult?.let { feedback ->
                Spacer(Modifier.height(12.dp))
                SaveFeedbackCard(feedback)
            }

            state.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                WmCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Warning, contentDescription = null, tint = extras.danger)
                        Spacer(Modifier.width(10.dp))
                        Text(error, style = MaterialTheme.typography.bodyMedium, color = extras.danger)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.save { ok -> if (ok) onSaved() } },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(if (state.saving) "Сохраняем…" else "Сохранить напоминание")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial = state.time,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                viewModel.setTime(it)
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun DayChip(
    label: String,
    date: LocalDate,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    FilterChip(
        selected = selectedDate == date,
        onClick = { onSelect(date) },
        label = { Text(label) },
    )
}

@Composable
private fun PreviewCard(state: ReminderFormState) {
    val extras = LocalWmColors.current
    when (val preview = state.preview) {
        is PreviewState.Ok -> {
            WmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = extras.success)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = preview.title.ifBlank { "Без названия" },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = preview.whenText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                preview.warnings.forEach { warning ->
                    Spacer(Modifier.height(6.dp))
                    Text(warning, style = MaterialTheme.typography.bodySmall, color = extras.warning)
                }
            }
        }

        is PreviewState.NeedsClarification -> {
            WmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = extras.warning)
                    Spacer(Modifier.width(10.dp))
                    Text(preview.question, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is PreviewState.NoDate -> {
            WmCard {
                Text(
                    "Время не распознано. Добавьте его словами или переключитесь на ручной режим.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        PreviewState.Empty -> Unit
    }
}

@Composable
private fun SaveFeedbackCard(feedback: SaveFeedback) {
    val extras = LocalWmColors.current
    val (label, color, background, icon) = when (feedback.precision) {
        SchedulePrecision.EXACT -> Quad(
            "Запланировано",
            extras.success,
            extras.successContainer,
            Icons.Outlined.CheckCircle,
        )

        SchedulePrecision.INEXACT -> Quad(
            "Запланировано неточно",
            extras.warning,
            extras.warningContainer,
            Icons.Outlined.Warning,
        )

        SchedulePrecision.FAILED -> Quad(
            "Сохранено, но не запланировано",
            extras.danger,
            extras.dangerContainer,
            Icons.Outlined.NotificationsOff,
        )
    }
    WmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = color)
                Text(
                    text = dev.wmos.ui.dueLabel(feedback.atMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (feedback.message.isNotBlank()) {
                    Text(
                        text = feedback.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusPill(text = feedback.precision.name, color = color, background = background)
        }
    }
}

private data class Quad(val a: String, val b: androidx.compose.ui.graphics.Color, val c: androidx.compose.ui.graphics.Color, val d: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun PermissionCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val extras = LocalWmColors.current
    Spacer(Modifier.height(8.dp))
    WmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Warning, contentDescription = null, tint = extras.warning)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    var hourText by remember { mutableStateOf("%02d".format(initial.hour)) }
    var minuteText by remember { mutableStateOf("%02d".format(initial.minute)) }
    val valid = hourText.toIntOrNull()?.let { it in 0..23 } == true &&
        minuteText.toIntOrNull()?.let { it in 0..59 } == true

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Время напоминания") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hourText,
                    onValueChange = { if (it.length <= 2) hourText = it.filter(Char::isDigit) },
                    label = { Text("Часы") },
                    singleLine = true,
                    isError = hourText.toIntOrNull()?.let { it in 0..23 } != true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.width(110.dp),
                )
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = { if (it.length <= 2) minuteText = it.filter(Char::isDigit) },
                    label = { Text("Минуты") },
                    singleLine = true,
                    isError = minuteText.toIntOrNull()?.let { it in 0..59 } != true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.width(110.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(hourText.toInt(), minuteText.toInt()))
                },
                enabled = valid,
            ) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
