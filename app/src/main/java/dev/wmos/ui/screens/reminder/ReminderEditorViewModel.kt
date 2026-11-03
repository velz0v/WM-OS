package dev.wmos.ui.screens.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.wmos.AppContainer
import dev.wmos.data.db.entity.ReminderEntity
import dev.wmos.domain.reminder.NaturalReminderParser
import dev.wmos.domain.reminder.RecurrenceCalculator
import dev.wmos.domain.reminder.RecurrenceRule
import dev.wmos.domain.reminder.SchedulePrecision
import dev.wmos.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ReminderMode { NATURAL, MANUAL }

enum class RepeatChoice(val label: String) {
    ONCE("Один раз"),
    DAILY("Каждый день"),
    WEEKLY("По дням недели"),
    MONTHLY("Каждый месяц"),
}

data class ReminderFormState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val mode: ReminderMode = ReminderMode.NATURAL,
    val naturalText: String = "",
    val title: String = "",
    val description: String = "",
    val date: LocalDate = LocalDate.now().plusDays(1),
    val time: LocalTime = LocalTime.of(9, 0),
    val repeat: RepeatChoice = RepeatChoice.ONCE,
    val weekdays: Set<Int> = emptySet(),
    val preview: PreviewState = PreviewState.Empty,
    val saving: Boolean = false,
    val saveResult: SaveFeedback? = null,
    val error: String? = null,
    val exactAllowed: Boolean = true,
    val notificationsAllowed: Boolean = true,
    val existing: ReminderEntity? = null,
)

sealed interface PreviewState {
    data object Empty : PreviewState

    data class Ok(val title: String, val whenText: String, val warnings: List<String>) : PreviewState

    data class NeedsClarification(val question: String) : PreviewState

    data class NoDate(val title: String) : PreviewState
}

data class SaveFeedback(
    val precision: SchedulePrecision,
    val message: String,
    val atMillis: Long,
)

class ReminderEditorViewModel(
    private val container: AppContainer,
    private val reminderId: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(ReminderFormState(isNew = reminderId == null))
    val state: StateFlow<ReminderFormState> = _state

    init {
        _state.value = _state.value.copy(
            exactAllowed = container.scheduler.canScheduleExact(),
            notificationsAllowed = container.scheduler.notificationsEnabled(),
        )
        if (reminderId != null) {
            viewModelScope.launch { load(reminderId) }
        } else {
            _state.value = _state.value.copy(loading = false)
        }
    }

    private suspend fun load(id: String) {
        val reminder = container.reminders.reminder(id)
        if (reminder == null) {
            _state.value = _state.value.copy(loading = false, error = "Напоминание не найдено")
            return
        }
        val zone = runCatching { ZoneId.of(reminder.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
        val at = Instant.ofEpochMilli(reminder.nextTriggerAt).atZone(zone)
        val rule = RecurrenceRule.decode(reminder.recurrenceRule)
        _state.value = _state.value.copy(
            loading = false,
            isNew = false,
            mode = ReminderMode.MANUAL,
            title = reminder.title,
            description = reminder.description,
            date = at.toLocalDate(),
            time = at.toLocalTime().withSecond(0).withNano(0),
            repeat = when (rule?.frequency) {
                RecurrenceRule.Frequency.DAILY -> RepeatChoice.DAILY
                RecurrenceRule.Frequency.WEEKLY -> RepeatChoice.WEEKLY
                RecurrenceRule.Frequency.MONTHLY -> RepeatChoice.MONTHLY
                null -> RepeatChoice.ONCE
            },
            weekdays = rule?.daysOfWeek ?: emptySet(),
            existing = reminder,
            preview = PreviewState.Ok(
                title = reminder.title,
                whenText = NaturalReminderParser.formatDateTime(at),
                warnings = emptyList(),
            ),
        )
    }

    fun setMode(mode: ReminderMode) {
        _state.value = _state.value.copy(mode = mode, saveResult = null, error = null)
        if (mode == ReminderMode.NATURAL) parseNatural()
    }

    fun onNaturalTextChange(text: String) {
        _state.value = _state.value.copy(naturalText = text, saveResult = null, error = null)
        parseNatural()
    }

    private fun parseNatural() {
        val text = _state.value.naturalText
        if (text.isBlank()) {
            _state.value = _state.value.copy(preview = PreviewState.Empty)
            return
        }
        val now = ZonedDateTime.now()
        when (val outcome = NaturalReminderParser.parse(text, now)) {
            is NaturalReminderParser.Outcome.Success -> {
                val parsed = outcome.parsed
                _state.value = _state.value.copy(
                    title = parsed.title,
                    preview = PreviewState.Ok(
                        title = parsed.title,
                        whenText = NaturalReminderParser.describeSchedule(parsed.schedule),
                        warnings = parsed.warnings,
                    ),
                )
            }

            is NaturalReminderParser.Outcome.NeedsClarification ->
                _state.value = _state.value.copy(
                    preview = PreviewState.NeedsClarification(outcome.question),
                )

            is NaturalReminderParser.Outcome.NoDate ->
                _state.value = _state.value.copy(preview = PreviewState.NoDate(outcome.title))
        }
    }

    fun setTitle(value: String) {
        _state.value = _state.value.copy(title = value)
    }

    fun setDescription(value: String) {
        _state.value = _state.value.copy(description = value)
    }

    fun setDate(date: LocalDate) {
        _state.value = _state.value.copy(date = date)
        refreshManualPreview()
    }

    fun setTime(time: LocalTime) {
        _state.value = _state.value.copy(time = time)
        refreshManualPreview()
    }

    fun setRepeat(repeat: RepeatChoice) {
        val weekdays = if (repeat == RepeatChoice.WEEKLY && _state.value.weekdays.isEmpty()) {
            setOf(_state.value.date.dayOfWeek.value)
        } else {
            _state.value.weekdays
        }
        _state.value = _state.value.copy(repeat = repeat, weekdays = weekdays)
        refreshManualPreview()
    }

    fun toggleWeekday(day: Int) {
        val current = _state.value.weekdays
        val next = if (current.contains(day)) current - day else current + day
        _state.value = _state.value.copy(weekdays = next.ifEmpty { setOf(day) })
        refreshManualPreview()
    }

    private fun refreshManualPreview() {
        val s = _state.value
        val rule = buildRule(s)
        val text = if (rule != null) {
            RecurrenceCalculator.describe(rule) + " в " + "%02d:%02d".format(s.time.hour, s.time.minute)
        } else {
            NaturalReminderParser.formatDateTime(
                ZonedDateTime.of(s.date, s.time, ZoneId.systemDefault()),
            )
        }
        _state.value = s.copy(
            preview = PreviewState.Ok(
                title = s.title,
                whenText = text,
                warnings = emptyList(),
            ),
        )
    }

    private fun buildRule(s: ReminderFormState): RecurrenceRule? = when (s.repeat) {
        RepeatChoice.ONCE -> null
        RepeatChoice.DAILY -> RecurrenceRule.daily(s.time.hour, s.time.minute)
        RepeatChoice.WEEKLY -> RecurrenceRule.weekly(s.weekdays, s.time.hour, s.time.minute)
        RepeatChoice.MONTHLY -> RecurrenceRule.monthly(s.date.dayOfMonth, s.time.hour, s.time.minute)
    }

    /** Resolves the actual instant the user is about to save. */
    fun resolvedTrigger(): Long? {
        val s = _state.value
        return when (s.mode) {
            ReminderMode.NATURAL -> {
                when (val p = s.preview) {
                    is PreviewState.Ok -> parseScheduleToMillis()
                    else -> null
                }
            }

            ReminderMode.MANUAL -> {
                val rule = buildRule(s)
                if (rule == null) {
                    var at = ZonedDateTime.of(s.date, s.time, ZoneId.systemDefault())
                    if (!at.isAfter(ZonedDateTime.now())) return null
                    at.toInstant().toEpochMilli()
                } else {
                    NaturalReminderParser.firstOccurrence(rule, ZonedDateTime.now())
                        ?.toInstant()?.toEpochMilli()
                }
            }
        }
    }

    private fun parseScheduleToMillis(): Long? {
        val now = ZonedDateTime.now()
        return when (val outcome = NaturalReminderParser.parse(_state.value.naturalText, now)) {
            is NaturalReminderParser.Outcome.Success ->
                when (val schedule = outcome.parsed.schedule) {
                    is NaturalReminderParser.Schedule.OneShot -> schedule.at.toInstant().toEpochMilli()
                    is NaturalReminderParser.Schedule.Recurring -> schedule.firstAt.toInstant().toEpochMilli()
                }

            else -> null
        }
    }

    private fun ruleForSave(): RecurrenceRule? {
        val s = _state.value
        if (s.mode == ReminderMode.MANUAL) return buildRule(s)
        val outcome = NaturalReminderParser.parse(s.naturalText, ZonedDateTime.now())
        return (outcome as? NaturalReminderParser.Outcome.Success)
            ?.parsed?.schedule
            ?.let { it as? NaturalReminderParser.Schedule.Recurring }
            ?.rule
    }

    fun save(onSaved: (Boolean) -> Unit) {
        val triggerAt = resolvedTrigger()
        if (triggerAt == null) {
            _state.value = _state.value.copy(error = "Укажите корректное будущее время напоминания")
            onSaved(false)
            return
        }
        val title = _state.value.title
        if (title.isBlank()) {
            _state.value = _state.value.copy(error = "Название напоминания обязательно")
            onSaved(false)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            val rule = ruleForSave()
            val result = if (_state.value.isNew) {
                container.reminders.create(
                    title = title,
                    triggerAtMillis = triggerAt,
                    timeZoneId = ZoneId.systemDefault().id,
                    description = _state.value.description,
                    rule = rule,
                )
            } else {
                container.reminders.update(
                    id = _state.value.existing?.id ?: return@launch,
                    title = title,
                    triggerAtMillis = triggerAt,
                    timeZoneId = ZoneId.systemDefault().id,
                    description = _state.value.description,
                    allDay = false,
                    rule = rule,
                )
            }
            result.onSuccess { saved ->
                _state.value = _state.value.copy(
                    saving = false,
                    saveResult = SaveFeedback(
                        precision = saved.scheduling.outcome,
                        message = saved.scheduling.message,
                        atMillis = saved.nextTriggerAt,
                    ),
                )
                onSaved(true)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    saving = false,
                    error = error.message ?: "Не удалось сохранить напоминание",
                )
                onSaved(false)
            }
        }
    }

    fun refreshPermissions() {
        _state.value = _state.value.copy(
            exactAllowed = container.scheduler.canScheduleExact(),
            notificationsAllowed = container.scheduler.notificationsEnabled(),
        )
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _state.value.existing?.id
        if (id == null) {
            onDeleted()
            return
        }
        viewModelScope.launch {
            container.reminders.delete(id)
            onDeleted()
        }
    }

    companion object {
        fun factory(reminderId: String?) = viewModelFactory {
            initializer { ReminderEditorViewModel(container(), reminderId) }
        }
    }
}
