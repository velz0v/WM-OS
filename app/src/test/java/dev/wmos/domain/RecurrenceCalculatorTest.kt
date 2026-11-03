package dev.wmos.domain

import dev.wmos.domain.reminder.RecurrenceCalculator
import dev.wmos.domain.reminder.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceCalculatorTest {
    private val zone = ZoneId.of("Europe/Moscow")

    private fun at(date: String, time: String): ZonedDateTime =
        ZonedDateTime.of(LocalDate.parse(date), java.time.LocalTime.parse(time), zone)

    @Test
    fun `daily rule fires later the same day when the time has not passed`() {
        val rule = RecurrenceRule.daily(9, 0)
        val anchor = at("2026-03-10", "09:00")
        val after = at("2026-03-10", "08:00")
        val next = RecurrenceCalculator.nextAfter(rule, anchor, after)
        assertEquals(at("2026-03-10", "09:00"), next)
    }

    @Test
    fun `daily rule moves to the next day when the time has passed`() {
        val rule = RecurrenceRule.daily(9, 0)
        val anchor = at("2026-03-10", "09:00")
        val after = at("2026-03-10", "10:00")
        val next = RecurrenceCalculator.nextAfter(rule, anchor, after)
        assertEquals(at("2026-03-11", "09:00"), next)
    }

    @Test
    fun `weekly rule picks the next matching weekday`() {
        // 2026-03-02 is a Monday.
        val rule = RecurrenceRule.weekly(setOf(1), 10, 0)
        val anchor = at("2026-03-02", "10:00")
        val after = at("2026-03-03", "00:00")
        val next = RecurrenceCalculator.nextAfter(rule, anchor, after)
        assertEquals(at("2026-03-09", "10:00"), next)
    }

    @Test
    fun `weekly rule can fire later on the same weekday`() {
        val rule = RecurrenceRule.weekly(setOf(1), 10, 0)
        val anchor = at("2026-03-02", "10:00")
        val after = at("2026-03-02", "09:00")
        val next = RecurrenceCalculator.nextAfter(rule, anchor, after)
        assertEquals(at("2026-03-02", "10:00"), next)
    }

    @Test
    fun `monthly rule clamps the 31st to the last day of a short month`() {
        val rule = RecurrenceRule.monthly(31, 9, 0, interval = 1)
            .copy(monthlyPolicy = RecurrenceRule.MonthlyPolicy.CLAMP_TO_LAST_DAY)
        val anchor = at("2026-01-31", "09:00")
        val after = at("2026-04-01", "00:00")
        val next = RecurrenceCalculator.nextAfter(rule, anchor, after)
        assertEquals(at("2026-04-30", "09:00"), next)
    }

    @Test
    fun `monthly rule skips months without the requested day`() {
        val rule = RecurrenceRule.monthly(31, 9, 0, interval = 1)
            .copy(monthlyPolicy = RecurrenceRule.MonthlyPolicy.SKIP)
        val anchor = at("2026-01-31", "09:00")
        val after = at("2026-04-01", "00:00")
        val next = RecurrenceCalculator.nextAfter(rule, anchor, after)
        assertEquals(at("2026-05-31", "09:00"), next)
    }

    @Test
    fun `february 29 does not exist in a non leap year`() {
        val rule = RecurrenceRule.monthly(29, 9, 0)
            .copy(monthlyPolicy = RecurrenceRule.MonthlyPolicy.CLAMP_TO_LAST_DAY)
        val anchor = at("2024-02-29", "09:00")
        val after = at("2027-02-01", "00:00")
        val next = RecurrenceCalculator.nextAfter(rule, anchor, after)
        assertEquals(at("2027-02-28", "09:00"), next)
    }

    @Test
    fun `daily rule survives a daylight saving transition`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val rule = RecurrenceRule.daily(9, 0)
        val anchor = ZonedDateTime.of(LocalDate.parse("2026-03-28"), java.time.LocalTime.of(9, 0), berlin)
        val after = ZonedDateTime.of(LocalDate.parse("2026-03-28"), java.time.LocalTime.of(10, 0), berlin)
        val next = RecurrenceCalculator.nextAfter(rule, anchor, after)
        assertNotNull(next)
        assertEquals(9, next!!.hour)
        assertEquals(29, next.dayOfMonth)
    }

    @Test
    fun `every two weeks keeps the parity`() {
        val rule = RecurrenceRule.weekly(setOf(1), 10, 0, interval = 2)
        val anchor = at("2026-03-02", "10:00")
        val after = at("2026-03-03", "00:00")
        val next = RecurrenceCalculator.nextAfter(rule, anchor, after)
        assertEquals(at("2026-03-16", "10:00"), next)
    }

    @Test
    fun `weekly rule without explicit days falls back to the anchor weekday`() {
        val rule = RecurrenceRule.weekly(emptySet(), 10, 0)
        val anchor = at("2026-03-02", "10:00")
        val after = at("2026-03-03", "00:00")
        val next = RecurrenceCalculator.nextAfter(rule, anchor, after)
        assertNotNull(next)
        assertEquals(1, next!!.dayOfWeek.value)
    }

    @Test
    fun `encode and decode keep the rule intact`() {
        val rule = RecurrenceRule.weekly(setOf(1, 3, 5), 8, 30, interval = 2)
        val restored = RecurrenceRule.decode(RecurrenceRule.encode(rule))
        assertEquals(rule, restored)
    }

    @Test
    fun `decode of garbage returns null instead of throwing`() {
        assertNull(RecurrenceRule.decode("not a json"))
        assertNull(RecurrenceRule.decode(null))
    }
}
