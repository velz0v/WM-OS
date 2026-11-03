package dev.wmos.data.repo

import dev.wmos.data.db.WmDatabase
import dev.wmos.data.db.entity.ReminderEntity
import dev.wmos.data.db.entity.ReminderOccurrenceEntity
import dev.wmos.data.db.entity.ReminderStatusValues
import dev.wmos.domain.reminder.RecurrenceCalculator
import dev.wmos.domain.reminder.RecurrenceRule
import dev.wmos.domain.reminder.ReminderScheduler
import dev.wmos.domain.reminder.SchedulePrecision
import dev.wmos.domain.reminder.ScheduleResult
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.math.absoluteValue

data class ReminderSaveResult(
    val reminderId: String,
    val scheduling: ScheduleResult,
    val nextTriggerAt: Long,
)

/**
 * Reminders are first-class entities, not a date field on a task.
 *
 * Saving is two-phase on purpose: the row is committed, then the alarm is armed, then the row is
 * updated with the real scheduling status. A save therefore never claims success when the OS
 * refused to arm the notification.
 */
class ReminderRepository(
    private val db: WmDatabase,
    private val scheduler: ReminderScheduler,
) {
    private val dao = db.reminderDao()

    fun observeAll(): Flow<List<ReminderEntity>> = dao.observeAll()

    fun observeActive(): Flow<List<ReminderEntity>> = dao.observeActive()

    fun observeReminder(id: String): Flow<ReminderEntity?> = dao.observeById(id)

    fun observeOccurrences(id: String): Flow<List<ReminderOccurrenceEntity>> = dao.observeOccurrences(id)

    suspend fun reminder(id: String): ReminderEntity? = dao.getById(id)

    suspend fun create(
        title: String,
        triggerAtMillis: Long,
        timeZoneId: String = ZoneId.systemDefault().id,
        description: String = "",
        allDay: Boolean = false,
        rule: RecurrenceRule? = null,
        linkedEntityId: String? = null,
        linkedEntityType: String? = null,
    ): Result<ReminderSaveResult> {
        val clean = title.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Название напоминания обязательно"))
        if (triggerAtMillis <= System.currentTimeMillis()) {
            return Result.failure(IllegalArgumentException("Время напоминания уже прошло"))
        }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val notificationId = (id.hashCode().absoluteValue % 100_000) + 1000
        val entity = ReminderEntity(
            id = id,
            title = clean,
            description = description.trim(),
            nextTriggerAt = triggerAtMillis,
            timeZoneId = timeZoneId,
            allDay = allDay,
            recurrenceRule = rule?.let { RecurrenceRule.encode(it) },
            status = ReminderStatusValues.SCHEDULED,
            linkedEntityId = linkedEntityId,
            linkedEntityType = linkedEntityType,
            notificationId = notificationId,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(entity)
        val result = scheduler.schedule(
            reminderId = id,
            triggerAtMillis = triggerAtMillis,
            title = clean,
            body = description,
            notificationId = notificationId,
            timeZoneId = timeZoneId,
        )
        applySchedulingOutcome(entity, result)
        return Result.success(ReminderSaveResult(id, result, triggerAtMillis))
    }

    suspend fun update(
        id: String,
        title: String,
        triggerAtMillis: Long,
        timeZoneId: String,
        description: String,
        allDay: Boolean,
        rule: RecurrenceRule?,
    ): Result<ReminderSaveResult> {
        val existing = dao.getById(id) ?: return Result.failure(IllegalStateException("Напоминание не найдено"))
        val clean = title.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Название обязательно"))
        scheduler.cancel(id, existing.notificationId)
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            title = clean,
            description = description.trim(),
            nextTriggerAt = triggerAtMillis,
            timeZoneId = timeZoneId,
            allDay = allDay,
            recurrenceRule = rule?.let { RecurrenceRule.encode(it) },
            status = ReminderStatusValues.SCHEDULED,
            revision = existing.revision + 1,
            updatedAt = now,
        )
        dao.upsert(updated)
        val result = scheduler.schedule(
            reminderId = id,
            triggerAtMillis = triggerAtMillis,
            title = clean,
            body = description,
            notificationId = existing.notificationId,
            timeZoneId = timeZoneId,
        )
        applySchedulingOutcome(updated, result)
        return Result.success(ReminderSaveResult(id, result, triggerAtMillis))
    }

    private suspend fun applySchedulingOutcome(entity: ReminderEntity, result: ScheduleResult) {
        val status = when (result.outcome) {
            SchedulePrecision.FAILED -> ReminderStatusValues.NOT_SCHEDULED
            else -> ReminderStatusValues.SCHEDULED
        }
        if (entity.status != status) {
            dao.upsert(entity.copy(status = status, updatedAt = System.currentTimeMillis()))
        }
    }

    /** Marks one occurrence done. A repeating series keeps its future firings. */
    suspend fun complete(id: String): Result<Unit> {
        val existing = dao.getById(id) ?: return Result.failure(IllegalStateException("Напоминание не найдено"))
        scheduler.cancel(id, existing.notificationId)
        val rule = RecurrenceRule.decode(existing.recurrenceRule)
        if (rule == null) {
            dao.upsert(existing.copy(status = ReminderStatusValues.DONE, updatedAt = System.currentTimeMillis()))
            return Result.success(Unit)
        }
        val next = nextOccurrence(existing, rule)
        if (next == null) {
            dao.upsert(existing.copy(status = ReminderStatusValues.DONE, updatedAt = System.currentTimeMillis()))
            return Result.success(Unit)
        }
        val now = System.currentTimeMillis()
        dao.upsert(
            existing.copy(
                nextTriggerAt = next,
                status = ReminderStatusValues.SCHEDULED,
                lastFiredAt = now,
                revision = existing.revision + 1,
                updatedAt = now,
            ),
        )
        val outcome = scheduler.schedule(
            reminderId = id,
            triggerAtMillis = next,
            title = existing.title,
            body = existing.description,
            notificationId = existing.notificationId,
            timeZoneId = existing.timeZoneId,
        )
        if (outcome.outcome == SchedulePrecision.FAILED) {
            dao.upsert(existing.copy(status = ReminderStatusValues.NOT_SCHEDULED, updatedAt = now))
        }
        return Result.success(Unit)
    }

    /**
     * Snoozes only the current firing. The repetition rule is untouched: after the snooze fires,
     * the series continues on its own schedule.
     */
    suspend fun snooze(id: String, minutes: Int = 10): Result<Long> {
        val existing = dao.getById(id) ?: return Result.failure(IllegalStateException("Напоминание не найдено"))
        val until = System.currentTimeMillis() + minutes * 60_000L
        scheduler.cancel(id, existing.notificationId)
        val updated = existing.copy(
            nextTriggerAt = until,
            status = ReminderStatusValues.SNOOZED,
            revision = existing.revision + 1,
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(updated)
        val outcome = scheduler.schedule(
            reminderId = id,
            triggerAtMillis = until,
            title = existing.title,
            body = existing.description,
            notificationId = existing.notificationId,
            timeZoneId = existing.timeZoneId,
        )
        if (outcome.outcome == SchedulePrecision.FAILED) {
            dao.upsert(updated.copy(status = ReminderStatusValues.NOT_SCHEDULED))
            return Result.failure(IllegalStateException(outcome.message.ifBlank { "Не удалось отложить" }))
        }
        return Result.success(until)
    }

    suspend fun cancel(id: String) {
        val existing = dao.getById(id) ?: return
        scheduler.cancel(id, existing.notificationId)
        dao.upsert(existing.copy(status = ReminderStatusValues.CANCELLED, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) {
        val existing = dao.getById(id) ?: return
        scheduler.cancel(id, existing.notificationId)
        dao.delete(id)
    }

    suspend fun markFired(id: String) {
        val now = System.currentTimeMillis()
        dao.markFired(id, ReminderStatusValues.FIRED, now, now)
    }

    suspend fun recordOccurrence(reminderId: String, scheduledAt: Long, state: String = "PENDING") {
        dao.upsertOccurrence(
            ReminderOccurrenceEntity(
                id = UUID.randomUUID().toString(),
                reminderId = reminderId,
                scheduledAt = scheduledAt,
                state = state,
            ),
        )
    }

    /**
     * Re-arms every active reminder. Called after reboot, package replace and time-zone changes.
     * The database is the source of truth, so repeated calls are idempotent: the alarm manager
     * replaces an existing PendingIntent with the same request code instead of duplicating it.
     */
    suspend fun rescheduleAll(): Int {
        var armed = 0
        val now = System.currentTimeMillis()
        for (reminder in dao.activeReminders()) {
            val rule = RecurrenceRule.decode(reminder.recurrenceRule)
            var target = reminder.nextTriggerAt
            if (target <= now) {
                val next = if (rule != null) nextOccurrence(reminder, rule) else null
                if (next != null) {
                    target = next
                    dao.upsert(
                        reminder.copy(
                            nextTriggerAt = next,
                            status = ReminderStatusValues.SCHEDULED,
                            updatedAt = now,
                            revision = reminder.revision + 1,
                        ),
                    )
                } else {
                    dao.upsert(
                        reminder.copy(status = ReminderStatusValues.MISSED, updatedAt = now),
                    )
                    continue
                }
            }
            val outcome = scheduler.schedule(
                reminderId = reminder.id,
                triggerAtMillis = target,
                title = reminder.title,
                body = reminder.description,
                notificationId = reminder.notificationId,
                timeZoneId = reminder.timeZoneId,
            )
            when (outcome.outcome) {
                SchedulePrecision.EXACT, SchedulePrecision.INEXACT -> armed++
                SchedulePrecision.FAILED -> dao.upsert(
                    reminder.copy(status = ReminderStatusValues.NOT_SCHEDULED, updatedAt = now),
                )
            }
        }
        return armed
    }

    /** Next firing after "now", evaluated in the reminder's own zone so travel keeps intent. */
    fun nextOccurrence(
        reminder: ReminderEntity,
        rule: RecurrenceRule,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Long? {
        val zone = runCatching { ZoneId.of(reminder.timeZoneId) }.getOrDefault(now.zone)
        val after = now.withZoneSameInstant(zone)
        val anchor = Instant.ofEpochMilli(reminder.nextTriggerAt).atZone(zone)
        return RecurrenceCalculator.nextAfter(rule, anchor, after)?.toInstant()?.toEpochMilli()
    }
}
