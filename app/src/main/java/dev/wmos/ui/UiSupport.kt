package dev.wmos.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import dev.wmos.AppContainer
import dev.wmos.WmApplication
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Gives every ViewModel initializer the manual dependency container. */
fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WmApplication).container

private val RU = Locale("ru", "RU")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", RU)
private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM", RU)
private val dateWithYear = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)
private val fullDateTime = DateTimeFormatter.ofPattern("d MMM, HH:mm", RU)

private val monthGenitive = listOf(
    "января",
    "февраля",
    "марта",
    "апреля",
    "мая",
    "июня",
    "июля",
    "августа",
    "сентября",
    "октября",
    "ноября",
    "декабря",
)

fun zone(): ZoneId = ZoneId.systemDefault()

fun today(): LocalDate = LocalDate.now(zone())

fun startOfDay(date: LocalDate = today()): Long =
    date.atStartOfDay(zone()).toInstant().toEpochMilli()

fun endOfDay(date: LocalDate = today()): Long =
    date.atTime(LocalTime.MAX).atZone(zone()).toInstant().toEpochMilli()

fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()

fun localDateTime(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(zone())

fun formatTime(millis: Long): String = localDateTime(millis).format(timeFormatter)

fun formatDate(millis: Long): String = localDateTime(millis).format(dateFormatter)

fun formatDateTime(millis: Long): String = localDateTime(millis).format(fullDateTime)

fun formatMonthYear(date: LocalDate): String = "${date.dayOfMonth} ${monthGenitive[date.monthValue - 1]}"

fun formatFullDate(date: LocalDate): String {
    val base = "${date.dayOfMonth} ${monthGenitive[date.monthValue - 1]}"
    return if (date.year == today().year) base else "$base ${date.year}"
}

fun weekdayNameRu(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "понедельник"
    DayOfWeek.TUESDAY -> "вторник"
    DayOfWeek.WEDNESDAY -> "среда"
    DayOfWeek.THURSDAY -> "четверг"
    DayOfWeek.FRIDAY -> "пятница"
    DayOfWeek.SATURDAY -> "суббота"
    DayOfWeek.SUNDAY -> "воскресенье"
}

/** "Сегодня", "Завтра", "Вчера" or a real date — never an ambiguous relative label. */
fun relativeDayLabel(date: LocalDate): String {
    val delta = ChronoUnit.DAYS.between(today(), date)
    return when (delta) {
        0L -> "Сегодня"
        1L -> "Завтра"
        2L -> "Послезавтра"
        -1L -> "Вчера"
        else -> formatFullDate(date)
    }
}

fun dueLabel(millis: Long): String {
    val date = localDate(millis)
    val time = formatTime(millis)
    return "${relativeDayLabel(date)}, $time"
}

fun humanizePast(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 60 * 24 -> "${minutes / 60} ч назад"
        else -> "${minutes / (60 * 24)} дн назад"
    }
}

fun isToday(millis: Long): Boolean = localDate(millis) == today()

fun plusDays(days: Int, from: Long = System.currentTimeMillis()): Long =
    Instant.ofEpochMilli(from).atZone(zone()).plusDays(days.toLong()).toInstant().toEpochMilli()

fun ZonedDateTime.toDateString(): String = DateTimeFormatter.ofPattern("d MMM yyyy", RU).format(this)

fun pluralizeRu(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "$count $many"
        mod10 == 1 -> "$count $one"
        mod10 in 2..4 -> "$count $few"
        else -> "$count $many"
    }
}

fun snippet(text: String, limit: Int = 140): String {
    val collapsed = text.replace(Regex("\\s+"), " ").trim()
    return if (collapsed.length <= limit) collapsed else collapsed.take(limit).trimEnd() + "…"
}

fun titleOrFallback(title: String, content: String): String {
    if (title.isNotBlank()) return title.trim()
    val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    return when {
        firstLine.isEmpty() -> "Без названия"
        firstLine.length <= 60 -> firstLine
        else -> firstLine.take(60).trimEnd() + "…"
    }
}

fun LocalDateTime.toMillis(): Long = atZone(zone()).toInstant().toEpochMilli()
