package dev.wmos.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.wmos.MainActivity
import dev.wmos.R
import dev.wmos.WmApplication
import dev.wmos.domain.reminder.ReminderScheduler
import dev.wmos.domain.reminder.SchedulePrecision
import dev.wmos.domain.reminder.ScheduleResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReminderNotifications {
    const val CHANNEL_ID = "wm_reminders_v1"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_desc)
            // Private content stays hidden on the lock screen by default.
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun show(context: Context, reminderId: String, title: String, body: String, notificationId: Int) {
        if (!canPost(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val openPending = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val donePending = actionPending(context, reminderId, notificationId, ACTION_DONE, notificationId + 7_000)
        val snoozePending = actionPending(context, reminderId, notificationId, ACTION_SNOOZE, notificationId + 9_000)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(body.ifBlank { "Напоминание WM OS" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifBlank { title }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, context.getString(R.string.reminder_action_done), donePending)
            .addAction(0, context.getString(R.string.reminder_action_snooze), snoozePending)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
    }

    private fun actionPending(
        context: Context,
        reminderId: String,
        notificationId: Int,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("wmos://reminder/$reminderId/$action")
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val ACTION_DONE = "dev.wmos.action.REMINDER_DONE"
    const val ACTION_SNOOZE = "dev.wmos.action.REMINDER_SNOOZE"
}

class AlarmReminderScheduler(private val context: Context) : ReminderScheduler {
    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    override fun notificationsEnabled(): Boolean = ReminderNotifications.canPost(context)

    override fun schedule(
        reminderId: String,
        triggerAtMillis: Long,
        title: String,
        body: String,
        notificationId: Int,
        timeZoneId: String,
    ): ScheduleResult {
        val manager = alarmManager
            ?: return ScheduleResult(SchedulePrecision.FAILED, "Системный сервис будильника недоступен")
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("wmos://reminder/$reminderId")
            putExtra(ReminderNotifications.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderNotifications.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return try {
            if (canScheduleExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                ScheduleResult(SchedulePrecision.EXACT)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                ScheduleResult(
                    SchedulePrecision.INEXACT,
                    "Точные будильники запрещены системой — сигнал может прийти с задержкой",
                )
            }
        } catch (security: SecurityException) {
            ScheduleResult(SchedulePrecision.FAILED, "Нет разрешения на точный будильник")
        } catch (invalid: IllegalArgumentException) {
            ScheduleResult(SchedulePrecision.FAILED, invalid.message ?: "Некорректное время")
        }
    }

    override fun cancel(reminderId: String, notificationId: Int) {
        val manager = alarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("wmos://reminder/$reminderId")
        }
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { manager.cancel(pending) }
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
    }
}

/**
 * Receives the alarm and the notification actions. Work runs in `goAsync()` because the
 * database write must finish before the process may be reclaimed.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? WmApplication ?: return
        val reminderId = intent.getStringExtra(ReminderNotifications.EXTRA_REMINDER_ID) ?: return
        val notificationId = intent.getIntExtra(ReminderNotifications.EXTRA_NOTIFICATION_ID, 0)
        val action = intent.action
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ReminderNotifications.ACTION_DONE -> {
                        app.container.reminders.complete(reminderId)
                        NotificationManagerCompat.from(context).cancel(notificationId)
                    }

                    ReminderNotifications.ACTION_SNOOZE -> {
                        app.container.reminders.snooze(reminderId, 10)
                        NotificationManagerCompat.from(context).cancel(notificationId)
                    }

                    else -> {
                        val reminder = app.container.reminders.reminder(reminderId)
                        if (reminder != null) {
                            app.container.reminders.markFired(reminderId)
                            app.container.reminders.recordOccurrence(reminderId, reminder.nextTriggerAt, "FIRED")
                            ReminderNotifications.show(
                                context = context,
                                reminderId = reminderId,
                                title = reminder.title,
                                body = reminder.description,
                                notificationId = notificationId,
                            )
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Restores every active alarm after reboot, app update or a time-zone/time change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_TIME_CHANGED
        ) {
            return
        }
        val app = context.applicationContext as? WmApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.reminders.rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
