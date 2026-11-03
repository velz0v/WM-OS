package dev.wmos.domain

import dev.wmos.data.db.entity.AutomationActionValues
import dev.wmos.domain.automation.AutomationActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationActionsTest {
    @Test
    fun `task config round-trips through the encoder`() {
        val json = AutomationActions.encodeTask("Проверить сервер", 2)
        val validated = AutomationActions.validate(AutomationActionValues.CREATE_TASK, json).getOrThrow()
        assertTrue(validated is AutomationActions.Validated.Task)
        val config = (validated as AutomationActions.Validated.Task).config
        assertEquals("Проверить сервер", config.title)
        assertEquals(2, config.dueInDays)
    }

    @Test
    fun `task without a title is rejected`() {
        val json = AutomationActions.encodeTask("   ", null)
        assertTrue(AutomationActions.validate(AutomationActionValues.CREATE_TASK, json).isFailure)
    }

    @Test
    fun `too long task title is rejected`() {
        val json = AutomationActions.encodeTask("a".repeat(400), null)
        assertTrue(AutomationActions.validate(AutomationActionValues.CREATE_TASK, json).isFailure)
    }

    @Test
    fun `negative due days are ignored instead of trusted`() {
        val json = AutomationActions.encodeTask("Задача", -5)
        val validated = AutomationActions.validate(AutomationActionValues.CREATE_TASK, json).getOrThrow()
        val config = (validated as AutomationActions.Validated.Task).config
        assertEquals(null, config.dueInDays)
    }

    @Test
    fun `note needs title or body`() {
        val empty = AutomationActions.encodeNote("", "")
        assertTrue(AutomationActions.validate(AutomationActionValues.CREATE_NOTE, empty).isFailure)
        val filled = AutomationActions.encodeNote("Идея", "Текст")
        assertTrue(AutomationActions.validate(AutomationActionValues.CREATE_NOTE, filled).isSuccess)
    }

    @Test
    fun `daily summary needs no config`() {
        val validated = AutomationActions.validate(AutomationActionValues.DAILY_SUMMARY, "{}").getOrThrow()
        assertEquals(AutomationActions.Validated.DailySummary, validated)
    }

    @Test
    fun `broken json is rejected instead of crashing`() {
        assertTrue(AutomationActions.validate(AutomationActionValues.CREATE_TASK, "not json").isFailure)
        assertTrue(AutomationActions.validate(AutomationActionValues.CREATE_TASK, "[]").isFailure)
    }

    @Test
    fun `unknown action is rejected`() {
        assertTrue(AutomationActions.validate("DELETE_EVERYTHING", "{}").isFailure)
    }

    @Test
    fun `describe never returns the raw json`() {
        val json = AutomationActions.encodeTask("Полить цветы", 1)
        val description = AutomationActions.describe(AutomationActionValues.CREATE_TASK, json)
        assertTrue(description.contains("Полить цветы"))
        assertTrue(!description.contains("{"))
    }
}
