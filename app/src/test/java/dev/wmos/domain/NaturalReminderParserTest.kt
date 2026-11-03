package dev.wmos.domain

import dev.wmos.domain.reminder.NaturalReminderParser
import dev.wmos.domain.reminder.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NaturalReminderParserTest {
    private val zone = ZoneId.of("Europe/Moscow")

    private fun now(): ZonedDateTime =
        ZonedDateTime.of(LocalDate.parse("2026-10-29"), LocalTime.parse("14:00"), zone)

    @Test
    fun `tomorrow with explicit time produces a one shot reminder`() {
        val result = NaturalReminderParser.parse("завтра в 18:30 проверить сервер", now())
        assertTrue(result is NaturalReminderParser.Outcome.Success)
        val parsed = (result as NaturalReminderParser.Outcome.Success).parsed
        val schedule = parsed.schedule as NaturalReminderParser.Schedule.OneShot
        assertEquals(LocalDate.parse("2026-10-30"), schedule.at.toLocalDate())
        assertEquals(18, schedule.at.hour)
        assertEquals(30, schedule.at.minute)
        assertEquals("Проверить сервер", parsed.title)
    }

    @Test
    fun `relative minutes are added to now`() {
        val result = NaturalReminderParser.parse("через 45 минут", now())
        val schedule = ((result as NaturalReminderParser.Outcome.Success).parsed.schedule)
            as NaturalReminderParser.Schedule.OneShot
        assertEquals(now().plusMinutes(45), schedule.at)
    }

    @Test
    fun `every monday produces a weekly rule`() {
        val result = NaturalReminderParser.parse("каждый понедельник в 10:00", now())
        val parsed = (result as NaturalReminderParser.Outcome.Success).parsed
        val schedule = parsed.schedule as NaturalReminderParser.Schedule.Recurring
        assertEquals(RecurrenceRule.Frequency.WEEKLY, schedule.rule.frequency)
        assertEquals(setOf(1), schedule.rule.daysOfWeek)
        assertEquals(10, schedule.rule.hour)
        assertEquals(1, schedule.firstAt.dayOfWeek.value)
    }

    @Test
    fun `every day produces a daily rule`() {
        val result = NaturalReminderParser.parse("каждый день в 09:00", now())
        val schedule = ((result as NaturalReminderParser.Outcome.Success).parsed.schedule)
            as NaturalReminderParser.Schedule.Recurring
        assertEquals(RecurrenceRule.Frequency.DAILY, schedule.rule.frequency)
        assertEquals(9, schedule.rule.hour)
    }

    @Test
    fun `vague evening is reported as ambiguous instead of guessed`() {
        val result = NaturalReminderParser.parse("вечером позвонить маме", now())
        assertTrue(result is NaturalReminderParser.Outcome.NeedsClarification)
    }

    @Test
    fun `time in the past alone rolls to tomorrow`() {
        val result = NaturalReminderParser.parse("в 09:00 зарядка", now())
        val schedule = ((result as NaturalReminderParser.Outcome.Success).parsed.schedule)
            as NaturalReminderParser.Schedule.OneShot
        assertEquals(LocalDate.parse("2026-10-30"), schedule.at.toLocalDate())
    }

    @Test
    fun `a bare day without time asks for the time`() {
        val result = NaturalReminderParser.parse("завтра купить билеты", now())
        assertTrue(result is NaturalReminderParser.Outcome.NeedsClarification)
    }

    @Test
    fun `a phrase without a date reports no date`() {
        val result = NaturalReminderParser.parse("купить хлеб", now())
        assertTrue(result is NaturalReminderParser.Outcome.NoDate)
    }

    @Test
    fun `time range is ambiguous`() {
        val result = NaturalReminderParser.parse("с 10 до 12 разобрать почту", now())
        assertTrue(result is NaturalReminderParser.Outcome.NeedsClarification)
    }

    @Test
    fun `every month on the 15th produces a monthly rule`() {
        val result = NaturalReminderParser.parse("каждое 15 число в 12:00 оплатить счета", now())
        val schedule = ((result as NaturalReminderParser.Outcome.Success).parsed.schedule)
            as NaturalReminderParser.Schedule.Recurring
        assertEquals(RecurrenceRule.Frequency.MONTHLY, schedule.rule.frequency)
        assertEquals(15, schedule.rule.dayOfMonth)
    }

    @Test
    fun `evening with explicit hour keeps the hour`() {
        val result = NaturalReminderParser.parse("завтра в 8 вечера созвон", now())
        val schedule = ((result as NaturalReminderParser.Outcome.Success).parsed.schedule)
            as NaturalReminderParser.Schedule.OneShot
        assertEquals(20, schedule.at.hour)
    }
}
