package com.owlcoders.chitti.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Single place that builds the reminder PendingIntent and arms the alarm, shared by
 * [ActionExecutor] (new reminders) and [BootReceiver] (re-arming after a reboot).
 */
object ReminderScheduler {
    const val ACTION_REMINDER = "com.owlcoders.chitti.REMINDER"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TASK_ID = "taskId"

    private fun pendingIntent(context: Context, taskId: Int, title: String): PendingIntent {
        // Explicit intent: implicit broadcasts to a non-exported receiver are not delivered on
        // Android 8+. Request code = taskId so each reminder has its own PendingIntent.
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Arms the alarm. Returns true when an exact alarm was set, false when it fell back to inexact. */
    fun schedule(context: Context, taskId: Int, title: String, triggerTime: Long): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, taskId, title)
        // SCHEDULE_EXACT_ALARM can be revoked by the user on Android 12+; fall back to an
        // inexact alarm instead of crashing with SecurityException.
        return try {
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
                false
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
                true
            }
        } catch (e: SecurityException) {
            Log.w("ReminderScheduler", "Exact alarm denied, using inexact: ${e.message}")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
            false
        }
    }

    fun cancel(context: Context, taskId: Int, title: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, taskId, title))
    }
}
