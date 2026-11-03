package dev.wmos.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Automation rule: trigger + allowed action + confirmation policy.
 *
 * The rule can only act inside WM OS through the same repositories the UI uses — it never executes
 * arbitrary code, never touches files outside private storage and never calls the network.
 */
@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val triggerType: String = AutomationTriggerValues.MANUAL,
    val actionType: String,
    /** Small JSON payload validated per action type; see [AutomationActions]. */
    val configJson: String = "{}",
    val enabled: Boolean = true,
    val requiresConfirmation: Boolean = true,
    val lastRunAt: Long? = null,
    val lastRunStatus: String? = null,
    val lastRunMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

object AutomationTriggerValues {
    const val MANUAL = "MANUAL"

    /** Stored and displayed, but not fired by WorkManager in this version. */
    const val SCHEDULE = "SCHEDULE"
    const val EVENT = "EVENT"

    val all = listOf(MANUAL, SCHEDULE, EVENT)
}

object AutomationActionValues {
    /** Creates one task from a fixed title. */
    const val CREATE_TASK = "CREATE_TASK"

    /** Creates a note with a fixed title and body. */
    const val CREATE_NOTE = "CREATE_NOTE"

    /** Builds a real summary of open tasks and upcoming reminders into a note. */
    const val DAILY_SUMMARY = "DAILY_SUMMARY"

    val all = listOf(CREATE_TASK, CREATE_NOTE, DAILY_SUMMARY)
}

object AutomationRunStatusValues {
    const val SUCCESS = "SUCCESS"
    const val SKIPPED = "SKIPPED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}
