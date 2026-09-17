package com.owlcoders.chitti.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Receives alarm broadcasts and shows reminder notifications.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Chitti Reminder"
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "chitti_reminders",
                "Chitti Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders set via Chitti"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(context, "chitti_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Chitti Reminder")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(title.hashCode(), notification)
    }
}
