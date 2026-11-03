package dev.wmos.domain.reminder

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Russian natural-language reminder parsing.
 *
 * The parser never invents a time. When a phrase is genuinely ambiguous ("вечером"),
 * it returns [Outcome.NeedsClarification] so the UI can ask instead of guessing.
 */
object NaturalReminderParser {
    data class Parsed(
        val title: String,
        val schedule: Schedule,
        val warnings: List<String> = emptyList(),
    )

    sealed interface Schedule {
        data class OneShot(val at: ZonedDateTime) : Schedule

        data class Recurring(val firstAt: ZonedDateTime, val rule: RecurrenceRule) : Schedule
    }

    sealed interface Outcome {
        data class Success(val parsed: Parsed) : Outcome

        data class NeedsClarification(val question: String, val originalText: String) : Outcome

        data class NoDate(val title: String) : Outcome
    }

    private val weekdayNames = mapOf(
        "понедельник" to 1,
        "пн" to 1,
        "вторник" to 2,
        "вт" to 2,
        "среда" to 3,
        "среду" to 3,
        "ср" to 3,
        "четверг" to 4,
        "чт" to 4,
        "пятница" to 5,
        "пятницу" to 5,
        "пт" to 5,
        "суббота" to 6,
        "субботу" to 6,
        "сб" to 6,
        "воскресенье" to 7,
        "вс" to 7,
    )

    private val dayPartWords = listOf("утра", "утром", "дня", "днем", "вечера", "вечером", "ночи", "ночью")

    private val relativeRegex = Regex("""через\s+(\d{1,4})\s*(минут[уы]?|мин|час[ао]?в?|ч|день|дня|дней|дн)""")
    private val timeRegex = Regex("""(?:в|к)\s+(\d{1,2})(?::(\d{2}))?\s*(утра|вечера|дня|ночи)?""")
    private val dateWordRegex = Regex("""(сегодня|завтра|послезавтра)""")
    private val dailyRegex = Regex("""кажд(?:ый|ую|ое)\s+день""")
    private val weeklyDayRegex = Regex("""кажд(?:ый|ую)\s+([а-яё]+)""")
    private val everyWeekRegex = Regex("""кажд(?:ую|ую)\s+неделю""")
    private val monthlyRegex = Regex("""кажд(?:ое|ый)\s+(\d{1,2})\s*(?:-?е|ое)?\s*числ""")
    private val rangeRegex = Regex("""с\s+(\d{1,2})(?::(\d{2}))?\s+до\s+(\d{1,2})(?::(\d{2}))?""")

    fun parse(rawText: String, now: ZonedDateTime): Outcome {
        val text = rawText.trim()
        if (text.isEmpty()) return Outcome.NeedsClarification("Что напомнить и когда?", rawText)

        var working = text
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("""\s+"""), " ")

        val warnings = mutableListOf<String>()
        var consumedRanges = mutableListOf<IntRange>()

        fun consume(match: MatchResult) {
            consumedRanges.add(match.range)
            working = working.removeRange(match.range).replace(Regex("\\s+"), " ").trim()
        }

        // 1. Relative offsets: "через 45 минут".
        relativeRegex.find(working)?.let { m ->
            val amount = m.groupValues[1].toIntOrNull()
            val unit = m.groupValues[2]
            if (amount != null && amount > 0) {
                val target = when {
                    unit.startsWith("мин") -> now.plusMinutes(amount.toLong())
                    unit.startsWith("ч") -> now.plusHours(amount.toLong())
                    else -> now.plusDays(amount.toLong()).withSecond(0).withNano(0)
                }
                consume(m)
                if (target.isBefore(now)) warnings.add("Время уже прошло — проверьте дату")
                return Outcome.Success(
                    Parsed(title = buildTitle(working, rawText), schedule = Schedule.OneShot(target), warnings = warnings),
                )
            }
        }

        // 2. Explicit clock time, optional day part.
        var hour: Int? = null
        var minute = 0
        timeRegex.find(working)?.let { m ->
            val h = m.groupValues[1].toIntOrNull()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val dayPart = m.groupValues[3]
            if (h != null && h in 0..23 && min in 0..59) {
                hour = applyDayPart(h, dayPart)
                minute = min
                consume(m)
            }
        }

        // 3. Explicit day word.
        var targetDate: LocalDate? = null
        dateWordRegex.find(working)?.let { m ->
            targetDate = when (m.groupValues[1]) {
                "сегодня" -> now.toLocalDate()
                "завтра" -> now.toLocalDate().plusDays(1)
                else -> now.toLocalDate().plusDays(2)
            }
            consume(m)
        }

        // 4. Recurrence.
        var rule: RecurrenceRule? = null
        if (dailyRegex.containsMatchIn(working)) {
            dailyRegex.find(working)?.let { consume(it) }
            rule = RecurrenceRule.daily(hour ?: 9, minute)
        }
        if (rule == null) {
            weeklyDayRegex.find(working)?.let { m ->
                val day = weekdayNames[m.groupValues[1]]
                if (day != null) {
                    consume(m)
                    rule = RecurrenceRule.weekly(setOf(day), hour ?: 9, minute)
                }
            }
        }
        if (rule == null && everyWeekRegex.containsMatchIn(working)) {
            everyWeekRegex.find(working)?.let { consume(it) }
            rule = RecurrenceRule.weekly(emptySet(), hour ?: 9, minute)
        }
        if (rule == null) {
            monthlyRegex.find(working)?.let { m ->
                val day = m.groupValues[1].toIntOrNull()
                if (day != null && day in 1..31) {
                    consume(m)
                    rule = RecurrenceRule.monthly(day, hour ?: 9, minute)
                }
            }
        }

        // 5. Time-of-day words without a clock ("вечером") are ambiguous on purpose.
        if (rule == null && hour == null) {
            val ambiguousWord = dayPartWords.firstOrNull { working.contains(it) }
            if (ambiguousWord != null) {
                val title = buildTitle(working.replace(ambiguousWord, " "), rawText)
                return Outcome.NeedsClarification(
                    "«$ambiguousWord» — это неоднозначно. Укажите точное время, например 18:30.",
                    title,
                )
            }
        }

        // 6. A time range without a chosen instant is ambiguous too.
        rangeRegex.find(working)?.let {
            return Outcome.NeedsClarification(
                "Указан интервал. На какое время поставить напоминание — на начало или на конец?",
                buildTitle(working.replace(it.value, " "), rawText),
            )
        }

        val resolvedHour = hour
        return when {
            rule != null -> {
                val r = rule!!
                val zone = now.zone
                val firstAt = firstOccurrence(r, now) ?: return Outcome.NeedsClarification(
                    "Не удалось вычислить дату повтора.",
                    buildTitle(working, rawText),
                )
                Outcome.Success(
                    Parsed(
                        title = buildTitle(working, rawText),
                        schedule = Schedule.Recurring(firstAt.withZoneSameInstant(zone), r),
                        warnings = warnings,
                    ),
                )
            }

            resolvedHour != null -> {
                val date = targetDate ?: run {
                    val candidate = now.toLocalDate().atTime(LocalTime.of(resolvedHour, minute)).atZone(now.zone)
                    if (candidate.isBefore(now)) now.toLocalDate().plusDays(1) else now.toLocalDate()
                }
                var at = ZonedDateTime.of(date, LocalTime.of(resolvedHour, minute), now.zone)
                if (at.isBefore(now)) {
                    if (targetDate != null) {
                        warnings.add("Указанное время уже прошло — напоминание не сработает в прошлом")
                    } else {
                        at = at.plusDays(1)
                    }
                }
                Outcome.Success(
                    Parsed(title = buildTitle(working, rawText), schedule = Schedule.OneShot(at), warnings = warnings),
                )
            }

            targetDate != null -> Outcome.NeedsClarification(
                "Дата есть, а время не указано. На какое время поставить напоминание?",
                buildTitle(working, rawText),
            )

            else -> Outcome.NoDate(buildTitle(working, rawText))
        }
    }

    fun firstOccurrence(rule: RecurrenceRule, now: ZonedDateTime): ZonedDateTime? {
        val zone = now.zone
        val today = now.toLocalDate()
        val candidate = when (rule.frequency) {
            RecurrenceRule.Frequency.DAILY -> {
                var d = today
                var at = RecurrenceCalculator.atTime(d, rule, zone)
                if (!at.isAfter(now)) {
                    d = d.plusDays(rule.interval.coerceAtLeast(1).toLong())
                    at = RecurrenceCalculator.atTime(d, rule, zone)
                }
                at
            }

            RecurrenceRule.Frequency.WEEKLY -> {
                val days = if (rule.daysOfWeek.isEmpty()) setOf(now.dayOfWeek.value) else rule.daysOfWeek
                var at: ZonedDateTime? = null
                for (offset in 0..14) {
                    val d = today.plusDays(offset.toLong())
                    if (!days.contains(d.dayOfWeek.value)) continue
                    val candidateAt = RecurrenceCalculator.atTime(d, rule, zone)
                    if (candidateAt.isAfter(now)) {
                        at = candidateAt
                        break
                    }
                }
                at
            }

            RecurrenceRule.Frequency.MONTHLY -> {
                val day = rule.dayOfMonth ?: today.dayOfMonth
                var month = java.time.YearMonth.from(today)
                var result: ZonedDateTime? = null
                for (i in 0..24) {
                    val len = month.lengthOfMonth()
                    val d = when {
                        day <= len -> day
                        rule.monthlyPolicy == RecurrenceRule.MonthlyPolicy.CLAMP_TO_LAST_DAY -> len
                        else -> -1
                    }
                    if (d > 0) {
                        val at = RecurrenceCalculator.atTime(
                            LocalDate.of(month.year, month.monthValue, d),
                            rule,
                            zone,
                        )
                        if (at.isAfter(now)) {
                            result = at
                            break
                        }
                    }
                    month = month.plusMonths(rule.interval.coerceAtLeast(1).toLong())
                }
                result
            }
        }
        return candidate ?: nextWeekdayFallback(rule, now)
    }

    private fun nextWeekdayFallback(rule: RecurrenceRule, now: ZonedDateTime): ZonedDateTime? {
        val base = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        return RecurrenceCalculator.atTime(base, rule, now.zone)
    }

    private fun applyDayPart(hour: Int, dayPart: String): Int {
        if (dayPart.isBlank()) return hour
        return when {
            dayPart.startsWith("вечер") && hour in 1..11 -> hour + 12
            dayPart.startsWith("дня") && hour in 1..11 -> hour + 12
            dayPart.startsWith("ночи") && hour == 12 -> 0
            else -> hour
        }
    }

    private fun buildTitle(stripped: String, original: String): String {
        val cleaned = stripped
            .replace(Regex("""^(напомни|напомнить|напоминание|создай напоминание|поставь напоминание)\s*"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim(',', '.', '-', '—')
            .trim()
        return if (cleaned.isBlank()) original.trim() else cleaned.replaceFirstChar { it.uppercase() }
    }

    fun describeSchedule(schedule: Schedule): String = when (schedule) {
        is Schedule.OneShot -> "Один раз: " + formatDateTime(schedule.at)
        is Schedule.Recurring -> RecurrenceCalculator.describe(schedule.rule) + ", начиная с " + formatDateTime(schedule.firstAt)
    }

    fun formatDateTime(at: ZonedDateTime): String {
        val d = at.toLocalDate()
        val t = at.toLocalTime()
        return "%s %02d:%02d".format(formatDate(d), t.hour, t.minute)
    }

    fun formatDate(d: LocalDate): String = "%02d.%02d.%d".format(d.dayOfMonth, d.monthValue, d.year)

    fun formatDateTimeLocal(dt: LocalDateTime): String =
        "%02d.%02d.%d %02d:%02d".format(dt.dayOfMonth, dt.monthValue, dt.year, dt.hour, dt.minute)
}
