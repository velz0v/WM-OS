package dev.wmos.domain.reminder

/** How precisely the OS agreed to deliver a reminder. */
enum class SchedulePrecision {
    /** Exact alarm armed. */
    EXACT,

    /** Armed, but the OS may delay it (no exact-alarm permission or battery saver). */
    INEXACT,

    /** Nothing was armed; the record stays saved and the UI must say so. */
    FAILED,
}

data class ScheduleResult(
    val outcome: SchedulePrecision,
    val message: String = "",
)

/** Platform-facing contract; the Android implementation lives in `dev.wmos.reminders`. */
interface ReminderScheduler {
    fun schedule(
        reminderId: String,
        triggerAtMillis: Long,
        title: String,
        body: String,
        notificationId: Int,
        timeZoneId: String,
    ): ScheduleResult

    fun cancel(reminderId: String, notificationId: Int)

    fun canScheduleExact(): Boolean

    fun notificationsEnabled(): Boolean
}
