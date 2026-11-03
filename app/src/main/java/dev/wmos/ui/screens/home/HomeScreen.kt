package dev.wmos.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.data.db.entity.KnowledgeItemEntity
import dev.wmos.data.db.entity.ReminderEntity
import dev.wmos.data.db.entity.TaskEntity
import dev.wmos.data.db.entity.TaskStatusValues
import dev.wmos.ui.container
import dev.wmos.ui.dueLabel
import dev.wmos.ui.endOfDay
import dev.wmos.ui.formatFullDate
import dev.wmos.ui.formatTime
import dev.wmos.ui.localDate
import dev.wmos.ui.relativeDayLabel
import dev.wmos.ui.snippet
import dev.wmos.ui.startOfDay
import dev.wmos.ui.titleOrFallback
import dev.wmos.ui.components.AnimatedBarChart
import dev.wmos.ui.components.AnimatedProgressBar
import androidx.compose.foundation.clickable
import dev.wmos.ui.anim.pressScale
import dev.wmos.ui.anim.rememberPressSource
import dev.wmos.ui.components.EmptyState
import dev.wmos.ui.components.SectionHeader
import dev.wmos.ui.components.StatusPill
import dev.wmos.ui.components.WmCard
import dev.wmos.ui.screens.task.QuickTaskDialog
import dev.wmos.ui.theme.Ink100
import dev.wmos.ui.theme.Lime
import dev.wmos.ui.theme.LimeDark
import dev.wmos.ui.theme.LocalWmColors
import dev.wmos.ui.theme.OnLime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class HomeUiState(
    val loading: Boolean = true,
    val todayTasks: List<TaskEntity> = emptyList(),
    val overdueTasks: List<TaskEntity> = emptyList(),
    val nextReminders: List<ReminderEntity> = emptyList(),
    val recentNotes: List<KnowledgeItemEntity> = emptyList(),
    val inboxNew: Int = 0,
    val completedToday: Int = 0,
    val weeklyDone: List<Int> = List(7) { 0 },
) {
    /** Share of tasks closed today against everything scheduled for today. */
    val todayProgress: Float
        get() {
            val open = todayTasks.size
            val total = completedToday + open
            return if (total == 0) 0f else completedToday.toFloat() / total
        }

    val isEmpty: Boolean
        get() = !loading && todayTasks.isEmpty() && overdueTasks.isEmpty() &&
            nextReminders.isEmpty() && recentNotes.isEmpty() && inboxNew == 0
}

private data class OpenAndClosed(
    val open: List<TaskEntity>,
    val closed: List<TaskEntity>,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val refreshTick = MutableStateFlow(0L)

    private val openAndClosed = combine(
        container.tasks.observeOpen(),
        container.tasks.observeClosed(),
    ) { open, closed -> OpenAndClosed(open, closed) }

    val state: StateFlow<HomeUiState> = combine(
        openAndClosed,
        container.reminders.observeActive(),
        container.brain.observeRecent(limit = 6),
        container.inbox.observeNewCount(),
        refreshTick,
    ) { tasks, reminders, notes, inboxNew, _ ->
        val now = System.currentTimeMillis()
        val dayStart = startOfDay()
        val dayEnd = endOfDay()
        val overdue = tasks.open
            .filter { it.dueAt != null && it.dueAt < now && it.dueAt < dayStart }
            .sortedBy { it.dueAt }
        val todayTasks = tasks.open
            .filter { it.dueAt != null && it.dueAt in dayStart..dayEnd }
            .sortedBy { it.dueAt }
        val closedToday = tasks.closed
            .count { it.completedAt != null && it.completedAt in dayStart..dayEnd }
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val weekly = (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            tasks.closed.count { it.completedAt != null && it.completedAt in from until to }
        }
        HomeUiState(
            loading = false,
            todayTasks = todayTasks,
            overdueTasks = overdue,
            nextReminders = reminders.filter { it.nextTriggerAt >= now }.take(3),
            recentNotes = notes,
            inboxNew = inboxNew,
            completedToday = closedToday,
            weeklyDone = weekly,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        refreshTick.value = System.currentTimeMillis()
    }

    fun completeTask(id: String, done: Boolean) {
        viewModelScope.launch {
            container.tasks.setStatus(id, if (done) TaskStatusValues.DONE else TaskStatusValues.NEXT)
        }
    }

    fun createQuickTask(title: String, dueAt: Long?) {
        viewModelScope.launch {
            container.tasks.createTask(title = title, dueAt = dueAt)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(container()) }
        }
    }
}

@Composable
fun HomeScreen(
    onOpenBrain: () -> Unit,
    onOpenGraus: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenReminder: (String) -> Unit,
    onNewNote: () -> Unit,
    onNewReminder: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showQuickTask by remember { mutableStateOf(false) }
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as dev.wmos.WmApplication

    val completeTask: (TaskEntity) -> Unit = { task ->
        viewModel.completeTask(task.id, true)
        app.container.snackbar.show(
            dev.wmos.ui.components.SnackMessage(
                text = "«${task.title}» выполнена",
                actionLabel = "Отменить",
                onAction = { viewModel.completeTask(task.id, false) },
            ),
        )
    }

    if (showQuickTask) {
        QuickTaskDialog(
            onDismiss = { showQuickTask = false },
            onConfirm = { title, dueAt ->
                viewModel.createQuickTask(title, dueAt)
                showQuickTask = false
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item { GreetingHeader(onOpenSearch) }

        item {
            QuickActions(
                onNewNote = onNewNote,
                onOpenPlan = onOpenPlan,
                onOpenBrain = onOpenBrain,
                onOpenGraus = onOpenGraus,
            )
        }

        item { ProgressSection(state) }

        if (state.inboxNew > 0) {
            item {
                SectionHeader("Входящие", subtitle = "Материалы без разбора")
                WmCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = onOpenInbox,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Inbox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${state.inboxNew} ${if (state.inboxNew == 1) "материал ждёт" else "материалов ждут"} разбора",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "Сохранено локально, ничего не потеряно",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (state.overdueTasks.isNotEmpty()) {
            item {
                SectionHeader(
                    "Просрочено",
                    subtitle = "${state.overdueTasks.size} задач требуют внимания",
                )
            }
            items(state.overdueTasks.take(4), key = { "overdue-${it.id}" }) { task ->
                TaskRow(
                    task = task,
                    overdue = true,
                    onClick = { onOpenTask(task.id) },
                    onToggle = { completeTask(task) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
                )
            }
        }

        item {
            SectionHeader(
                "Сегодня",
                subtitle = formatFullDate(LocalDate.now()),
                actionLabel = if (state.todayTasks.isNotEmpty()) "Все" else null,
                onAction = onOpenPlan,
            )
        }

        if (state.todayTasks.isEmpty() && !state.loading) {
            item {
                EmptyState(
                    icon = Icons.Outlined.CheckCircle,
                    title = "На сегодня задач нет",
                    message = "Добавьте задачу или запланируйте напоминание — они появятся здесь.",
                    actionLabel = "Добавить задачу",
                    onAction = { showQuickTask = true },
                )
            }
        } else {
            items(state.todayTasks.take(6), key = { "today-${it.id}" }) { task ->
                TaskRow(
                    task = task,
                    overdue = false,
                    onClick = { onOpenTask(task.id) },
                    onToggle = { completeTask(task) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
                )
            }
        }

        if (state.nextReminders.isNotEmpty()) {
            item {
                SectionHeader(
                    "Ближайшие напоминания",
                    actionLabel = "Все",
                    onAction = onOpenPlan,
                )
            }
            items(state.nextReminders, key = { "rem-${it.id}" }) { reminder ->
                ReminderRow(
                    reminder = reminder,
                    onClick = { onOpenReminder(reminder.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
                )
            }
        }

        if (state.recentNotes.isNotEmpty()) {
            item {
                SectionHeader("Недавнее", actionLabel = "Все", onAction = onOpenBrain)
            }
            items(state.recentNotes, key = { "note-${it.id}" }) { note ->
                RecentNoteRow(
                    note = note,
                    onClick = { onOpenNote(note.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
                )
            }
        }

        item { GrausPromo(onOpenGraus) }

        if (state.loading) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** Топ-секция: приветствие + продуктовый прогресс дня. */
@Composable
private fun ProgressSection(state: HomeUiState) {
    val labels = remember(state.weeklyDone.size) {
        val today = LocalDate.now()
        val ru = java.util.Locale.forLanguageTag("ru")
        (6 downTo 0).map { back ->
            today.minusDays(back.toLong())
                .dayOfWeek
                .getDisplayName(java.time.format.TextStyle.SHORT, ru)
                .lowercase()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        WmCard(contentPadding = PaddingValues(18.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Прогресс дня",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (state.todayProgress >= 1f && state.todayTasks.isNotEmpty()) {
                                "Всё закрыто — отличный день!"
                            } else {
                                "Выполнено ${state.completedToday} из " +
                                    "${state.completedToday + state.todayTasks.size} задач"
                            },
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
                Spacer(Modifier.height(16.dp))
                AnimatedBarChart(
                    values = state.weeklyDone.map { it.toFloat() },
                    labels = labels,
                    highlightIndex = state.weeklyDone.lastIndex,
                    chartHeight = 56.dp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Производительность за 7 дней · максимум ${state.weeklyDone.maxOrNull() ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GreetingHeader(onSearch: () -> Unit) {
    val hour = java.time.LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Ночная смена"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "$greeting, ${relativeDayLabel(LocalDate.now()).lowercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildAnnotatedString {
                        append("Собери ")
                        withStyle(SpanStyle(color = Lime)) { append("моментум") }
                        append(" сегодня")
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatFullDate(LocalDate.now()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "Глобальный поиск")
            }
        }
    }
}

@Composable
private fun QuickActions(
    onNewNote: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenBrain: () -> Unit,
    onOpenGraus: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickAction("Заметка", Icons.Outlined.EditNote, onNewNote, Modifier.weight(1f))
        QuickAction("Дела", Icons.Outlined.TaskAlt, onOpenPlan, Modifier.weight(1f))
        QuickAction("Знания", Icons.Outlined.Description, onOpenBrain, Modifier.weight(1f))
        QuickAction("Граус", Icons.Outlined.SmartToy, onOpenGraus, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WmCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(vertical = 14.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun GrausPromo(onOpenGraus: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = rememberPressSource()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pressScale(interaction)
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(Lime, LimeDark)),
            )
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onOpenGraus)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(OnLime),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = Lime)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Твой AI-помощник",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnLime,
                )
                Text(
                    "Разложит день, подскажет по заметкам и напомнит о важном",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnLime.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                color = Ink100,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    "Спросить",
                    style = MaterialTheme.typography.labelLarge,
                    color = OnLime,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Small rounded icon badge used by rows to give the lists a consistent visual rhythm. */
@Composable
private fun IconChip(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    overdue: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalWmColors.current
    WmCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val interaction = rememberPressSource()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .pressScale(interaction, pressedScale = 0.85f)
                    .clip(CircleShape)
                    .semantics { contentDescription = "Отметить выполненной" }
                    .clickable(interactionSource = interaction, indication = ripple(), onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (overdue) extras.danger else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                )
                if (task.dueAt != null) {
                    Text(
                        text = if (overdue) "Просрочено: ${dueLabel(task.dueAt)}" else dueLabel(task.dueAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) extras.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (overdue) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = "Просрочено",
                    tint = extras.danger,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: ReminderEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalWmColors.current
    WmCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(
                icon = Icons.Outlined.NotificationsActive,
                tint = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(reminder.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text(
                    text = dueLabel(reminder.nextTriggerAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (reminder.status == "NOT_SCHEDULED") {
                StatusPill(
                    text = "Не запланировано",
                    color = extras.onWarningContainer,
                    background = extras.warningContainer,
                )
            }
        }
    }
}

@Composable
private fun RecentNoteRow(
    note: KnowledgeItemEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WmCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = titleOrFallback(note.title, note.content),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
        )
        if (note.content.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = snippet(note.content),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatTime(note.updatedAt) + " · " + localDate(note.updatedAt).let { relativeDayLabel(it) },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
