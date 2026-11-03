package dev.wmos.domain.reminder

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A repetition *rule* is stored on the reminder; concrete firings are materialised as
 * [dev.wmos.data.db.entity.ReminderOccurrenceEntity] rows. Finishing one occurrence never
 * ends the series, and snoozing never rewrites the rule.
 */
@Serializable
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    /** ISO weekday numbers (Mon = 1 … Sun = 7). Only meaningful for WEEKLY. */
    val daysOfWeek: Set<Int> = emptySet(),
    /** Day of month 1..31. Only meaningful for MONTHLY. */
    val dayOfMonth: Int? = null,
    val monthlyPolicy: MonthlyPolicy = MonthlyPolicy.CLAMP_TO_LAST_DAY,
    /** Local wall-clock time the series fires at. */
    val hour: Int = 9,
    val minute: Int = 0,
) {
    @Serializable
    enum class Frequency {
        @SerialName("DAILY")
        DAILY,

        @SerialName("WEEKLY")
        WEEKLY,

        @SerialName("MONTHLY")
        MONTHLY,
    }

    @Serializable
    enum class MonthlyPolicy {
        /** 31st in a 30-day month fires on the 30th instead of skipping. */
        @SerialName("CLAMP_TO_LAST_DAY")
        CLAMP_TO_LAST_DAY,

        /** 31st in a 30-day month is skipped entirely. */
        @SerialName("SKIP")
        SKIP,
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(rule: RecurrenceRule): String = json.encodeToString(serializer(), rule)

        fun decode(raw: String?): RecurrenceRule? {
            if (raw.isNullOrBlank()) return null
            return runCatching { json.decodeFromString(serializer(), raw) }.getOrNull()
        }

        fun daily(hour: Int, minute: Int, interval: Int = 1) = RecurrenceRule(
            frequency = Frequency.DAILY,
            interval = interval,
            hour = hour,
            minute = minute,
        )

        fun weekly(days: Set<Int>, hour: Int, minute: Int, interval: Int = 1) = RecurrenceRule(
            frequency = Frequency.WEEKLY,
            interval = interval,
            daysOfWeek = days,
            hour = hour,
            minute = minute,
        )

        fun monthly(day: Int, hour: Int, minute: Int, interval: Int = 1) = RecurrenceRule(
            frequency = Frequency.MONTHLY,
            interval = interval,
            dayOfMonth = day,
            hour = hour,
            minute = minute,
        )
    }
}

/**
 * Pure, deterministic recurrence arithmetic. Everything here is unit-testable without
 * Android: no clock, no scheduler, no database.
 */
object RecurrenceCalculator {
    /**
     * Next firing strictly after [after], or null when the rule can never fire again
     * (e.g. MONTHLY/SKIP on the 31st with only 30-day months ahead — bounded search).
     */
    fun nextAfter(
        rule: RecurrenceRule,
        anchor: ZonedDateTime,
        after: ZonedDateTime,
    ): ZonedDateTime? = when (rule.frequency) {
        RecurrenceRule.Frequency.DAILY -> nextDaily(rule, anchor, after)
        RecurrenceRule.Frequency.WEEKLY -> nextWeekly(rule, anchor, after)
        RecurrenceRule.Frequency.MONTHLY -> nextMonthly(rule, anchor, after)
    }

    private fun nextDaily(rule: RecurrenceRule, anchor: ZonedDateTime, after: ZonedDateTime): ZonedDateTime {
        val step = rule.interval.coerceAtLeast(1)
        var candidate = atTime(after.withZoneSameInstant(anchor.zone).toLocalDate(), rule)
        if (!candidate.isAfter(after)) {
            candidate = atTime(candidate.toLocalDate().plusDays(step.toLong()), rule)
        }
        var guard = 0
        while (!candidate.isAfter(after) && guard++ < 4000) {
            candidate = atTime(candidate.toLocalDate().plusDays(step.toLong()), rule)
        }
        return candidate
    }

    private fun nextWeekly(rule: RecurrenceRule, anchor: ZonedDateTime, after: ZonedDateTime): ZonedDateTime? {
        val days = if (rule.daysOfWeek.isEmpty()) setOf(anchor.dayOfWeek.value) else rule.daysOfWeek
        val step = rule.interval.coerceAtLeast(1)
        val anchorWeek = anchor.toLocalDate().let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
        var cursor = after.withZoneSameInstant(anchor.zone).toLocalDate()
        var guard = 0
        while (guard++ < 400) {
            val candidate = atTime(cursor, rule)
            if (candidate.isAfter(after) && days.contains(cursor.dayOfWeek.value)) {
                val weekStart = cursor.minusDays((cursor.dayOfWeek.value - 1).toLong())
                val weeks = java.time.temporal.ChronoUnit.WEEKS.between(anchorWeek, weekStart)
                if (weeks % step == 0L) return candidate
            }
            cursor = cursor.plusDays(1)
        }
        return null
    }

    private fun nextMonthly(rule: RecurrenceRule, anchor: ZonedDateTime, after: ZonedDateTime): ZonedDateTime? {
        val step = rule.interval.coerceAtLeast(1)
        val targetDay = rule.dayOfMonth ?: anchor.dayOfMonth
        var month = YearMonth.from(after.withZoneSameInstant(anchor.zone).toLocalDate())
        var guard = 0
        while (guard++ < 120) {
            val length = month.lengthOfMonth()
            val exists = targetDay <= length
            val day = when {
                exists -> targetDay
                rule.monthlyPolicy == RecurrenceRule.MonthlyPolicy.CLAMP_TO_LAST_DAY -> length
                else -> -1
            }
            if (day > 0) {
                val candidate = atTime(LocalDate.of(month.year, month.monthValue, day), rule)
                if (candidate.isAfter(after)) return candidate
            }
            month = month.plusMonths(step.toLong())
        }
        return null
    }

    /** Applies wall-clock time in the rule's zone; DST gaps and overlaps resolve to a real instant. */
    fun atTime(date: LocalDate, rule: RecurrenceRule, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(date, java.time.LocalTime.of(rule.hour % 24, rule.minute % 60)), zone)

    fun atTime(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)), zone)

    fun describe(rule: RecurrenceRule): String = when (rule.frequency) {
        RecurrenceRule.Frequency.DAILY ->
            if (rule.interval == 1) "каждый день" else "каждые ${rule.interval} дн."

        RecurrenceRule.Frequency.WEEKLY ->
            if (rule.daysOfWeek.isEmpty()) {
                "каждую неделю"
            } else {
                "по " + rule.daysOfWeek.sorted().joinToString(", ") { dayLabel(it) }
            }

        RecurrenceRule.Frequency.MONTHLY -> "каждое ${rule.dayOfMonth ?: 1} число"
    }

    fun dayLabel(isoDay: Int): String = when (isoDay) {
        DayOfWeek.MONDAY.value -> "пн"
        DayOfWeek.TUESDAY.value -> "вт"
        DayOfWeek.WEDNESDAY.value -> "ср"
        DayOfWeek.THURSDAY.value -> "чт"
        DayOfWeek.FRIDAY.value -> "пт"
        DayOfWeek.SATURDAY.value -> "сб"
        else -> "вс"
    }
}
