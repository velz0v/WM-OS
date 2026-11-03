package dev.wmos.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations. Destructive fallback is deliberately not enabled: an upgrade must never
 * drop user notes. Every migration needs a test before release (see docs/DATA_MODEL.md).
 */
object WmMigrations {
    /** v1 -> v2: automation rules (Autopilot). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `automation_rules` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `triggerType` TEXT NOT NULL,
                    `actionType` TEXT NOT NULL,
                    `configJson` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `requiresConfirmation` INTEGER NOT NULL,
                    `lastRunAt` INTEGER,
                    `lastRunStatus` TEXT,
                    `lastRunMessage` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }

    val all: Array<Migration> = arrayOf(MIGRATION_1_2)
}
