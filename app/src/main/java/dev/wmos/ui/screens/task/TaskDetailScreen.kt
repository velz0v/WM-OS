package dev.wmos.ui.screens.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wmos.data.db.entity.TaskPriorityValues
import dev.wmos.data.db.entity.TaskStatusValues
import dev.wmos.ui.components.ConfirmDialog
import dev.wmos.ui.components.EmptyState
import dev.wmos.ui.components.LoadingState
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.theme.LocalWmColors
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TaskDetailScreen(
    taskId: String?,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: TaskDetailViewModel = viewModel(factory = TaskDetailViewModel.factory(taskId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var projectDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    val extras = LocalWmColors.current

    if (confirmDelete) {
        ConfirmDialog(
            title = "Удалить задачу?",
            message = "«${state.title.ifBlank { "Без названия" }}» будет скрыта из списков. " +
                "Связанные заметки и напоминания останутся.",
            confirmLabel = "Удалить задачу",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDeleted)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (projectDialog) {
        ProjectPicker(
            projects = projects,
            currentId = state.projectId,
            onDismiss = { projectDialog = false },
            onPick = { project ->
                viewModel.setProject(project?.id, project?.name)
                projectDialog = false
            },
        )
    }

    if (timeDialog) {
        TimeDialog(
            initial = state.dueTime,
            onDismiss = { timeDialog = false },
            onConfirm = {
                viewModel.setDueTime(it)
                timeDialog = false
            },
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
                title = { Text(if (taskId == null) "Новая задача" else "Задача") },
                actions = {
                    if (taskId != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить задачу")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(inner), horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingState()
            }

            state.missing -> EmptyState(
                icon = Icons.Outlined.Delete,
                title = "Задача не найдена",
                message = "Возможно, она была удалена.",
                actionLabel = "Назад",
                onAction = onBack,
                modifier = Modifier.padding(inner),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp),
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название") },
                    singleLine = true,
                    isError = state.error != null && state.title.isBlank(),
                    supportingText = if (state.error != null && state.title.isBlank()) {
                        { Text("Название обязательно") }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Описание (необязательно)") },
                    minLines = 3,
                )

                Spacer(Modifier.height(16.dp))
                Text("Статус", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        TaskStatusValues.INBOX to "Входящая",
                        TaskStatusValues.NEXT to "Следующая",
                        TaskStatusValues.IN_PROGRESS to "В работе",
                        TaskStatusValues.WAITING to "Ожидает",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = state.status == value,
                            onClick = { viewModel.setStatus(value) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.status == TaskStatusValues.DONE,
                        onClick = { viewModel.setStatus(TaskStatusValues.DONE) },
                        label = { Text("Выполнена") },
                    )
                    FilterChip(
                        selected = state.status == TaskStatusValues.CANCELLED,
                        onClick = { viewModel.setStatus(TaskStatusValues.CANCELLED) },
                        label = { Text("Отменена") },
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("Приоритет", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        TaskPriorityValues.LOW to "Низкий",
                        TaskPriorityValues.NORMAL to "Обычный",
                        TaskPriorityValues.HIGH to "Высокий",
                        TaskPriorityValues.URGENT to "Срочный",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = state.priority == value,
                            onClick = { viewModel.setPriority(value) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Срок", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.dueDate == null,
                        onClick = { viewModel.setDueDate(null) },
                        label = { Text("Без срока") },
                    )
                    FilterChip(
                        selected = state.dueDate == LocalDate.now(),
                        onClick = { viewModel.setDueDate(LocalDate.now()) },
                        label = { Text("Сегодня") },
                    )
                    FilterChip(
                        selected = state.dueDate == LocalDate.now().plusDays(1),
                        onClick = { viewModel.setDueDate(LocalDate.now().plusDays(1)) },
                        label = { Text("Завтра") },
                    )
                    FilterChip(
                        selected = state.dueDate == LocalDate.now().plusDays(7),
                        onClick = { viewModel.setDueDate(LocalDate.now().plusDays(7)) },
                        label = { Text("+7") },
                    )
                }
                if (state.dueDate != null) {
                    Spacer(Modifier.height(6.dp))
                    AssistChip(
                        onClick = { timeDialog = true },
                        label = {
                            Text(
                                "%02d:%02d".format(state.dueTime.hour, state.dueTime.minute),
                            )
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("Проект", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                AssistChip(
                    onClick = { projectDialog = true },
                    label = { Text(state.projectName ?: "Без проекта") },
                    leadingIcon = {
                        Icon(Icons.Outlined.FolderSpecial, contentDescription = null)
                    },
                )

                state.error?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    WmCard {
                        Text(message, style = MaterialTheme.typography.bodySmall, color = extras.danger)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.save { onBack() } },
                    enabled = !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(if (state.saving) "Сохраняем…" else "Сохранить")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ProjectPicker(
    projects: List<dev.wmos.data.db.entity.ProjectEntity>,
    currentId: String?,
    onDismiss: () -> Unit,
    onPick: (dev.wmos.data.db.entity.ProjectEntity?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Проект") },
        text = {
            Column(Modifier.height(280.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Без проекта")
                }
                projects.forEach { project ->
                    TextButton(onClick = { onPick(project) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (currentId == project.id) "• ${project.name}" else project.name)
                        }
                    }
                }
                if (projects.isEmpty()) {
                    Text(
                        "Проектов пока нет. Создайте их на вкладке «План».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun TimeDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    var hour by remember { mutableStateOf("%02d".format(initial.hour)) }
    var minute by remember { mutableStateOf("%02d".format(initial.minute)) }
    val valid = hour.toIntOrNull()?.let { it in 0..23 } == true &&
        minute.toIntOrNull()?.let { it in 0..59 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Время срока") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hour,
                    onValueChange = { if (it.length <= 2) hour = it.filter(Char::isDigit) },
                    label = { Text("Часы") },
                    isError = hour.toIntOrNull()?.let { it in 0..23 } != true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.width(110.dp),
                )
                OutlinedTextField(
                    value = minute,
                    onValueChange = { if (it.length <= 2) minute = it.filter(Char::isDigit) },
                    label = { Text("Минуты") },
                    isError = minute.toIntOrNull()?.let { it in 0..59 } != true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.width(110.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(LocalTime.of(hour.toInt(), minute.toInt())) },
                enabled = valid,
            ) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
