package dev.wmos.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.data.db.entity.ProjectEntity
import dev.wmos.data.db.entity.ReminderEntity
import dev.wmos.data.db.entity.TaskEntity
import dev.wmos.data.db.entity.TaskStatusValues
import dev.wmos.ui.anim.pressScale
import dev.wmos.ui.anim.rememberPressSource
import dev.wmos.ui.components.AnimatedProgressBar
import dev.wmos.ui.components.ConfirmDialog
import dev.wmos.ui.components.EmptyState
import dev.wmos.ui.components.StatusPill
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.container
import dev.wmos.ui.dueLabel
import dev.wmos.ui.screens.brain.NewFolderDialog
import dev.wmos.ui.theme.ChipBlue
import dev.wmos.ui.theme.ChipLime
import dev.wmos.ui.theme.ChipPurple
import dev.wmos.ui.theme.LocalWmColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Unified filters for tasks + reminders — they are one list now. */
enum class CaseFilter(val label: String) {
    TODAY("Сегодня"),
    ALL("Всё"),
    OVERDUE("Просрочено"),
    INBOX("Входящие"),
    DONE("Готово"),
}

/** One tappable line in the merged «Дела» list: a task or a reminder. */
data class CaseItem(
    val kind: Kind,
    val id: String,
    val title: String,
    val time: Long?,
    val done: Boolean,
    val overdue: Boolean,
    val priorityLabel: String?,
    val projectName: String?,
    val repeats: Boolean,
    val reminderStatus: String?,
    val updatedAt: Long,
) {
    enum class Kind { TASK, REMINDER }
}

data class PlanState(
    val loading: Boolean = true,
    val tasks: List<TaskEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
) {
    private fun taskCase(task: TaskEntity): CaseItem = CaseItem(
        kind = CaseItem.Kind.TASK,
        id = task.id,
        title = task.title,
        time = task.dueAt,
        done = task.status == TaskStatusValues.DONE || task.status == TaskStatusValues.CANCELLED,
        overdue = task.dueAt != null &&
            task.dueAt < System.currentTimeMillis() &&
            task.status in TaskStatusValues.open,
        priorityLabel = when (task.priority) {
            "HIGH" -> "Важно"
            "URGENT" -> "Срочно"
            else -> null
        },
        projectName = projects.firstOrNull { it.id == task.projectId }?.name,
        repeats = false,
        reminderStatus = null,
        updatedAt = task.updatedAt,
    )

    private fun reminderCase(reminder: ReminderEntity): CaseItem = CaseItem(
        kind = CaseItem.Kind.REMINDER,
        id = reminder.id,
        title = reminder.title,
        time = reminder.nextTriggerAt,
        done = reminder.status == "DONE" || reminder.status == "CANCELLED",
        overdue = reminder.nextTriggerAt < System.currentTimeMillis() &&
            reminder.status != "DONE" && reminder.status != "CANCELLED",
        priorityLabel = null,
        projectName = null,
        repeats = reminder.recurrenceRule != null,
        reminderStatus = reminder.status,
        updatedAt = reminder.nextTriggerAt,
    )

    private fun active(reminder: ReminderEntity): Boolean =
        reminder.status != "DONE" && reminder.status != "CANCELLED"

    /** The merged list: both kinds sorted by their trigger time, time-less items last. */
    fun cases(filter: CaseFilter): List<CaseItem> {
        val now = System.currentTimeMillis()
        val dayStart = dev.wmos.ui.startOfDay()
        val dayEnd = dev.wmos.ui.endOfDay()
        val taskCases = tasks.map { taskCase(it) }
        val reminderCases = reminders.map { reminderCase(it) }

        val selected = when (filter) {
            CaseFilter.TODAY ->
                taskCases.filter { !it.done && it.time != null && it.time in dayStart..dayEnd } +
                    reminderCases.filter { !it.done && it.time in dayStart..dayEnd }

            CaseFilter.ALL -> taskCases.filter { !it.done } + reminderCases.filter { !it.done }

            CaseFilter.OVERDUE ->
                taskCases.filter { it.overdue } + reminderCases.filter { it.overdue }

            CaseFilter.INBOX -> tasks.filter { it.status == TaskStatusValues.INBOX }.map { taskCase(it) }

            CaseFilter.DONE -> taskCases.filter { it.done } + reminderCases.filter { it.done }
        }
        return if (filter == CaseFilter.DONE) {
            selected.sortedWith(compareByDescending { it.updatedAt })
        } else {
            selected.sortedWith(compareBy { it.time ?: Long.MAX_VALUE })
        }
    }

    /** Deals scheduled for today (open or closed). */
    val todayPlanned: Int
        get() {
            val dayStart = dev.wmos.ui.startOfDay()
            val dayEnd = dev.wmos.ui.endOfDay()
            val open = tasks.count {
                it.status in TaskStatusValues.open && it.dueAt != null && it.dueAt in dayStart..dayEnd
            }
            val closed = tasks.count {
                it.status == TaskStatusValues.DONE && it.completedAt != null && it.completedAt in dayStart..dayEnd
            }
            return open + closed
        }

    /** Share of today's deals already closed. */
    val todayProgress: Float
        get() {
            val dayStart = dev.wmos.ui.startOfDay()
            val dayEnd = dev.wmos.ui.endOfDay()
            val open = tasks.count {
                it.status in TaskStatusValues.open && it.dueAt != null && it.dueAt in dayStart..dayEnd
            }
            val closed = tasks.count {
                it.status == TaskStatusValues.DONE && it.completedAt != null && it.completedAt in dayStart..dayEnd
            }
            val total = open + closed
            return if (total == 0) 0f else closed.toFloat() / total
        }

    val todayClosed: Int
        get() {
            val dayStart = dev.wmos.ui.startOfDay()
            val dayEnd = dev.wmos.ui.endOfDay()
            return tasks.count {
                it.status == TaskStatusValues.DONE && it.completedAt != null && it.completedAt in dayStart..dayEnd
            }
        }
}

class PlanViewModel(private val container: AppContainer) : androidx.lifecycle.ViewModel() {
    private val tick = MutableStateFlow(0)

    val state: StateFlow<PlanState> = combine(
        container.tasks.observeAll(),
        container.reminders.observeAll(),
        container.tasks.observeProjects(),
        tick,
    ) { tasks, reminders, projects, _ ->
        PlanState(loading = false, tasks = tasks, reminders = reminders, projects = projects)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanState())

    fun setDone(id: String, done: Boolean) {
        viewModelScope.launch {
            container.tasks.setStatus(id, if (done) TaskStatusValues.DONE else TaskStatusValues.NEXT)
        }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch { container.tasks.delete(id) }
    }

    fun createProject(name: String) {
        viewModelScope.launch { container.tasks.createProject(name) }
    }

    fun completeReminder(id: String) {
        viewModelScope.launch { container.reminders.complete(id) }
    }

    fun refresh() {
        tick.value += 1
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PlanViewModel(container()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    onOpenReminder: (String) -> Unit,
    onNewReminder: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onNewTask: () -> Unit,
    viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(CaseFilter.TODAY) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showProjectDialog by remember { mutableStateOf(false) }
    var deletingTask by remember { mutableStateOf<TaskEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun completeWithUndo(task: TaskEntity) {
        viewModel.setDone(task.id, true)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "«${task.title}» закрыто",
                actionLabel = "Отменить",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.setDone(task.id, false)
            }
        }
    }

    if (showProjectDialog) {
        NewFolderDialog(
            onDismiss = { showProjectDialog = false },
            onConfirm = { name ->
                viewModel.createProject(name)
                showProjectDialog = false
            },
        )
    }

    deletingTask?.let { task ->
        ConfirmDialog(
            title = "Удалить задачу?",
            message = "«${task.title}» будет убрана из списков. Заметки и напоминания не пострадают.",
            confirmLabel = "Удалить задачу",
            destructive = true,
            onConfirm = {
                viewModel.deleteTask(task.id)
                deletingTask = null
            },
            onDismiss = { deletingTask = null },
        )
    }

    if (showCreateSheet) {
        ModalBottomSheet(onDismissRequest = { showCreateSheet = false }) {
            CreateSheet(
                onTask = {
                    showCreateSheet = false
                    onNewTask()
                },
                onReminder = {
                    showCreateSheet = false
                    onNewReminder()
                },
                onProject = {
                    showCreateSheet = false
                    showProjectDialog = true
                },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = LocalWmColors.current.onAccent,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Новое дело")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                CasesHeader(
                    activeCount = state.cases(CaseFilter.ALL).size,
                    todayCount = state.cases(CaseFilter.TODAY).size,
                )
            }

            item {
                FilterChipsRow(
                    selected = filter,
                    onSelect = { filter = it },
                )
            }

            if (filter == CaseFilter.TODAY && state.todayPlanned > 0) {
                item { FocusCard(state) }
            }

            val caseItems = state.cases(filter)
            if (caseItems.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.TaskAlt,
                        title = when (filter) {
                            CaseFilter.DONE -> "Здесь пока пусто"
                            CaseFilter.OVERDUE -> "Просроченных дел нет"
                            else -> "Пока здесь ничего нет"
                        },
                        message = when (filter) {
                            CaseFilter.DONE -> "Закрытые дела появятся в этом списке."
                            CaseFilter.OVERDUE -> "Так держать — всё в графике."
                            CaseFilter.INBOX -> "Быстрые записи из шеринга попадут сюда."
                            else -> "Задачи и напоминания живут в одном списке. Создай дело кнопкой ниже."
                        },
                        actionLabel = if (filter != CaseFilter.DONE) "Новое дело" else null,
                        onAction = if (filter != CaseFilter.DONE) {
                            { showCreateSheet = true }
                        } else {
                            null
                        },
                    )
                }
            }

            items(caseItems, key = { "${it.kind}-${it.id}" }) { case ->
                when (case.kind) {
                    CaseItem.Kind.TASK -> {
                        val task = state.tasks.first { it.id == case.id }
                        CaseRow(
                            item = case,
                            onOpen = { onOpenTask(task.id) },
                            onToggle = { completeWithUndo(task) },
                            onDelete = { deletingTask = task },
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .animateItem(),
                        )
                    }

                    CaseItem.Kind.REMINDER -> {
                        val reminder = state.reminders.first { it.id == case.id }
                        CaseRow(
                            item = case,
                            onOpen = { onOpenReminder(reminder.id) },
                            onToggle = { viewModel.completeReminder(reminder.id) },
                            onDelete = null,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .animateItem(),
                        )
                    }
                }
            }

            if (filter == CaseFilter.ALL && state.projects.isNotEmpty()) {
                item { ProjectsStrip(state, onOpenProject) { showProjectDialog = true } }
            }
        }
    }
}

@Composable
private fun CasesHeader(activeCount: Int, todayCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
    ) {
        Text(
            text = "Дела",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$activeCount активных · $todayCount на сегодня",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterChipsRow(
    selected: CaseFilter,
    onSelect: (CaseFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(CaseFilter.entries.toList()) { entry ->
            FilterChip(
                selected = selected == entry,
                onClick = { onSelect(entry) },
                label = { Text(if (entry == CaseFilter.ALL) "Всё" else entry.label) },
            )
        }
    }
}

/** Big summary card in the "Great progress" style of the reference screens. */
@Composable
private fun FocusCard(state: PlanState) {
    WmCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentPadding = PaddingValues(18.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Фокус дня",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Закрыто ${state.todayClosed} из ${state.todayPlanned} дел сегодня",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${(state.todayProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            AnimatedProgressBar(
                progress = state.todayProgress,
                modifier = Modifier.fillMaxWidth(),
                height = 14.dp,
            )
        }
    }
}

/** One row: a task or a reminder, visually unified but each kind keeps its affordance. */
@Composable
private fun CaseRow(
    item: CaseItem,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val extras = LocalWmColors.current
    val interaction = rememberPressSource()
    WmCard(
        modifier = modifier,
        onClick = onOpen,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val isTask = item.kind == CaseItem.Kind.TASK
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .pressScale(interaction, pressedScale = 0.85f)
                    .clip(if (isTask) CircleShape else RoundedCornerShape(13.dp))
                    .then(
                        if (isTask) {
                            Modifier.border(
                                width = 2.dp,
                                color = when {
                                    item.done -> extras.success
                                    item.overdue -> extras.danger
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                        } else {
                            Modifier.background(
                                if (item.done) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    extras.accentSoft
                                },
                            )
                        },
                    )
                    .semantics { contentDescription = if (item.done) "Вернуть в работу" else "Отметить выполненным" }
                    .clickable(interactionSource = interaction, indication = ripple(), onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (isTask && item.done) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = extras.success,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (!isTask) {
                    Icon(
                        Icons.Outlined.Alarm,
                        contentDescription = null,
                        tint = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    color = if (item.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                )
                if (item.time != null || item.priorityLabel != null || item.projectName != null ||
                    item.repeats || item.reminderStatus == "NOT_SCHEDULED"
                ) {
                    Spacer(Modifier.height(3.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item.time?.let { time ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = when {
                                        item.done -> MaterialTheme.colorScheme.onSurfaceVariant
                                        item.overdue -> extras.danger
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = dueLabel(time),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        item.done -> MaterialTheme.colorScheme.onSurfaceVariant
                                        item.overdue -> extras.danger
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                        item.priorityLabel?.let { label ->
                            StatusPill(
                                text = label,
                                color = extras.onWarningContainer,
                                background = extras.warningContainer,
                            )
                        }
                        item.projectName?.let { name ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (item.repeats) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Repeat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "Повтор",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (item.reminderStatus == "NOT_SCHEDULED") {
                            StatusPill(
                                text = "Не запланировано",
                                color = extras.onWarningContainer,
                                background = extras.warningContainer,
                            )
                        }
                    }
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Удалить задачу",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Projects live in the same list as a horizontal strip — no separate tab needed. */
@Composable
private fun ProjectsStrip(
    state: PlanState,
    onOpenProject: (String) -> Unit,
    onCreateProject: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Проекты",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.projects, key = { it.id }) { project ->
                val projectTasks = state.tasks.filter { it.projectId == project.id }
                val done = projectTasks.count { it.status == TaskStatusValues.DONE }
                val total = projectTasks.size
                val progress = if (total == 0) 0f else done.toFloat() / total
                WmCard(
                    modifier = Modifier.width(210.dp),
                    onClick = { onOpenProject(project.id) },
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            project.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (total == 0) "нет задач" else "$done/$total · ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    AnimatedProgressBar(progress = progress, modifier = Modifier.fillMaxWidth(), height = 6.dp)
                }
            }
            item {
                WmCard(
                    modifier = Modifier.width(120.dp),
                    onClick = onCreateProject,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Новый проект", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateSheet(
    onTask: () -> Unit,
    onReminder: () -> Unit,
    onProject: () -> Unit,
) {
    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 28.dp)) {
        Text(
            text = "Новое дело",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        CreateOption(
            icon = Icons.Outlined.TaskAlt,
            chipColor = ChipLime,
            title = "Задача",
            subtitle = "Название, срок и проект",
            onClick = onTask,
        )
        CreateOption(
            icon = Icons.Outlined.Alarm,
            chipColor = ChipBlue,
            title = "Напоминание",
            subtitle = "Точное время и повтор",
            onClick = onReminder,
        )
        CreateOption(
            icon = Icons.Outlined.FolderSpecial,
            chipColor = ChipPurple,
            title = "Проект",
            subtitle = "Группа задач с общим прогрессом",
            onClick = onProject,
        )
    }
}

@Composable
private fun CreateOption(
    icon: ImageVector,
    chipColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(chipColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF10130A))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
