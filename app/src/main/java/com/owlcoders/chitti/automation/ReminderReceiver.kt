package com.owlcoders.chitti.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.owlcoders.chitti.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives alarm broadcasts and shows reminder notifications.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Chitti Reminder"
        val taskId = intent.getIntExtra(ReminderScheduler.EXTRA_TASK_ID, -1)

        fun dropPersistedRow() {
            // The reminder has fired; drop the persisted row so BootReceiver does not re-arm it.
            if (taskId >= 0) {
                val pending = goAsync()
                val appContext = context.applicationContext
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AppDatabase.getDatabase(appContext).reminderDao().deleteByTaskId(taskId)
                    } catch (t: Throwable) {
                        Log.w("ReminderReceiver", "Could not delete fired reminder: ${t.message}")
                    } finally {
                        pending.finish()
                    }
                }
            }
        }

        // Android 13+: notify() without POST_NOTIFICATIONS throws SecurityException,
        // which in a BroadcastReceiver crashes the whole app.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("ReminderReceiver", "POST_NOTIFICATIONS not granted; reminder '$title' not shown")
            dropPersistedRow()
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            "chitti_reminders",
            "Chitti Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders set via Chitti"
        }
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, "chitti_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Chitti Reminder")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(if (taskId >= 0) taskId else title.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w("ReminderReceiver", "notify() refused: ${e.message}")
        }
        dropPersistedRow()
    }
}
