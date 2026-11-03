package dev.wmos.ui.screens.autopilot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.data.db.entity.AutomationActionValues
import dev.wmos.data.db.entity.AutomationRuleEntity
import dev.wmos.data.db.entity.AutomationRunStatusValues
import dev.wmos.domain.automation.AutomationActions
import dev.wmos.ui.components.ConfirmDialog
import dev.wmos.ui.components.EmptyState
import dev.wmos.ui.components.StatusPill
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.container
import dev.wmos.ui.formatDateTime
import dev.wmos.ui.theme.LocalWmColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AutopilotViewModel(private val container: AppContainer) : ViewModel() {
    val rules: StateFlow<List<AutomationRuleEntity>> = container.automation.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun save(
        id: String?,
        name: String,
        actionType: String,
        configJson: String,
        requiresConfirmation: Boolean,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            container.automation.save(id, name, actionType, configJson, requiresConfirmation)
                .onSuccess {
                    _error.value = null
                    onDone()
                }
                .onFailure { _error.value = it.message ?: "Не удалось сохранить сценарий" }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { container.automation.setEnabled(id, enabled) }
    }

    fun delete(id: String) {
        viewModelScope.launch { container.automation.delete(id) }
    }

    fun run(rule: AutomationRuleEntity) {
        viewModelScope.launch {
            val result = container.automation.run(rule.id)
            _feedback.value = when (result.status) {
                AutomationRunStatusValues.SUCCESS -> "Готово: ${result.message}"
                AutomationRunStatusValues.SKIPPED -> "Пропущено: ${result.message}"
                else -> "Ошибка: ${result.message}"
            }
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        val Factory = viewModelFactory { initializer { AutopilotViewModel(container()) } }
    }
}

@Composable
fun AutopilotScreen(
    onBack: () -> Unit,
    viewModel: AutopilotViewModel = viewModel(factory = AutopilotViewModel.Factory),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val extras = LocalWmColors.current

    var editing by remember { mutableStateOf<AutomationRuleEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AutomationRuleEntity?>(null) }
    var confirmRun by remember { mutableStateOf<AutomationRuleEntity?>(null) }

    if (creating || editing != null) {
        RuleDialog(
            initial = editing,
            error = error,
            onDismiss = {
                creating = false
                editing = null
                viewModel.clearError()
            },
            onSave = { id, name, actionType, config, requiresConfirmation ->
                viewModel.save(id, name, actionType, config, requiresConfirmation) {
                    creating = false
                    editing = null
                }
            },
        )
    }

    deleting?.let { rule ->
        ConfirmDialog(
            title = "Удалить сценарий?",
            message = "«${rule.name}» больше не будет запускаться. Созданные им записи останутся.",
            confirmLabel = "Удалить сценарий",
            destructive = true,
            onConfirm = {
                viewModel.delete(rule.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    confirmRun?.let { rule ->
        ConfirmDialog(
            title = "Запустить сценарий?",
            message = buildString {
                append("«${rule.name}»\n\n")
                append(AutomationActions.describe(rule.actionType, rule.configJson))
                append("\n\nДействие изменит локальные данные приложения.")
            },
            confirmLabel = "Запустить",
            onConfirm = {
                viewModel.run(rule)
                confirmRun = null
            },
            onDismiss = { confirmRun = null },
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
                title = { Text("Autopilot") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Сценарий") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = extras.onAccent,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Text(
                    "Сценарий = триггер + разрешённое действие. Автоматизации работают только внутри WM OS: " +
                        "не запускают код, не читают произвольные файлы и не ходят в сеть. " +
                        "В этой версии триггер один — ручной запуск.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            feedback?.let { message ->
                item {
                    WmCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearFeedback() }) { Text("Ок") }
                        }
                    }
                }
            }

            if (rules.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.PlayArrow,
                        title = "Сценариев нет",
                        message = "Например: «вечерняя сводка задач» или «создать задачу раз в неделю».",
                        actionLabel = "Создать сценарий",
                        onAction = { creating = true },
                    )
                }
            }

            items(rules, key = { it.id }) { rule ->
                WmCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                AutomationActions.describe(rule.actionType, rule.configJson),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { viewModel.setEnabled(rule.id, it) },
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(0.dp),
                        )
                        Text(
                            "Триггер: ручной · " +
                                if (rule.requiresConfirmation) "нужно подтверждение" else "без подтверждения",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    rule.lastRunAt?.let { at ->
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val success = rule.lastRunStatus == AutomationRunStatusValues.SUCCESS
                            StatusPill(
                                text = when (rule.lastRunStatus) {
                                    AutomationRunStatusValues.SUCCESS -> "Успех"
                                    AutomationRunStatusValues.SKIPPED -> "Пропущено"
                                    AutomationRunStatusValues.FAILED -> "Ошибка"
                                    else -> rule.lastRunStatus ?: "—"
                                },
                                color = when {
                                    success -> extras.onSuccessContainer
                                    rule.lastRunStatus == AutomationRunStatusValues.FAILED -> extras.onDangerContainer
                                    else -> extras.onWarningContainer
                                },
                                background = when {
                                    success -> extras.successContainer
                                    rule.lastRunStatus == AutomationRunStatusValues.FAILED -> extras.dangerContainer
                                    else -> extras.warningContainer
                                },
                            )
                            Spacer(Modifier.height(0.dp))
                            Text(
                                "  ${formatDateTime(at)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        rule.lastRunMessage?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {
                                if (rule.requiresConfirmation) {
                                    confirmRun = rule
                                } else {
                                    viewModel.run(rule)
                                }
                            },
                            label = { Text("Запустить") },
                            leadingIcon = {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            },
                        )
                        AssistChip(
                            onClick = { editing = rule },
                            label = { Text("Изменить") },
                        )
                        AssistChip(
                            onClick = { deleting = rule },
                            label = { Text("Удалить") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleDialog(
    initial: AutomationRuleEntity?,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var action by remember { mutableStateOf(initial?.actionType ?: AutomationActionValues.DAILY_SUMMARY) }
    var taskTitle by remember { mutableStateOf("") }
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    var dueInDays by remember { mutableStateOf("") }
    var requiresConfirmation by remember { mutableStateOf(initial?.requiresConfirmation ?: true) }

    fun configJson(): String = when (action) {
        AutomationActionValues.CREATE_TASK ->
            AutomationActions.encodeTask(taskTitle, dueInDays.toIntOrNull())

        AutomationActionValues.CREATE_NOTE -> AutomationActions.encodeNote(noteTitle, noteBody)
        else -> "{}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый сценарий" else "Изменить сценарий") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название сценария") },
                    placeholder = { Text("Вечерняя сводка") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Действие", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        AutomationActionValues.DAILY_SUMMARY to "Сводка",
                        AutomationActionValues.CREATE_TASK to "Задача",
                        AutomationActionValues.CREATE_NOTE to "Заметка",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = action == value,
                            onClick = { action = value },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                when (action) {
                    AutomationActionValues.CREATE_TASK -> {
                        OutlinedTextField(
                            value = taskTitle,
                            onValueChange = { taskTitle = it },
                            label = { Text("Название задачи") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = dueInDays,
                            onValueChange = { dueInDays = it.filter(Char::isDigit) },
                            label = { Text("Срок через N дней (необязательно)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    AutomationActionValues.CREATE_NOTE -> {
                        OutlinedTextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            label = { Text("Заголовок заметки") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = noteBody,
                            onValueChange = { noteBody = it },
                            label = { Text("Текст заметки") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    else -> {
                        Text(
                            "Сценарий соберёт реальные открытые задачи и ближайшие напоминания в одну заметку. " +
                                "Если сводить нечего — ничего не создастся.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = requiresConfirmation,
                        onCheckedChange = { requiresConfirmation = it },
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(
                        "  Спрашивать подтверждение перед запуском",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = LocalWmColors.current.danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial?.id, name, action, configJson(), requiresConfirmation) },
                enabled = name.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
