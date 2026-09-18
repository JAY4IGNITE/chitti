package com.owlcoders.chitti.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.owlcoders.chitti.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager alarms do not survive a reboot. Re-arms every persisted reminder that is
 * still in the future.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(appContext)
                val now = System.currentTimeMillis()
                val reminders = db.reminderDao().getUpcoming(now)
                for (r in reminders) {
                    val title = db.taskDao().getTaskById(r.taskId)?.title ?: "Chitti Reminder"
                    ReminderScheduler.schedule(appContext, r.taskId, title, r.triggerTime)
                }
                Log.d("BootReceiver", "Re-armed ${reminders.size} reminders")
            } catch (t: Throwable) {
                Log.e("BootReceiver", "Failed to re-arm reminders: ${t.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
