package com.owlcoders.chitti.security

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Posts a high-priority heads-up alert when LinkGuard finds a DANGER link inside an
 * incoming notification, e.g. a phishing URL forwarded to a WhatsApp group.
 * Tapping the alert opens the LinkGuard warning screen with the full explanation.
 */
object LinkGuardNotifier {

    private const val TAG = "LinkGuardNotifier"
    private const val CHANNEL_ID = "chitti_linkguard"

    fun showDangerAlert(context: Context, url: String, verdict: LinkScanner.LinkVerdict) {
        // API 33+: posting without POST_NOTIFICATIONS throws SecurityException. The runtime
        // request lives in onboarding; here we only guard and explain why nothing was shown.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; dropping LinkGuard alert for $url")
            return
        }
        // Notifications may also be disabled for the app (or this channel) in system settings.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "Notifications disabled for the app; dropping LinkGuard alert for $url")
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "LinkGuard Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Warnings about scam or phishing links Chitti detected" }
        )

        val tapIntent = PendingIntent.getActivity(
            context,
            url.hashCode(),
            Intent(context, LinkGuardActivity::class.java)
                .putExtra(LinkGuardActivity.EXTRA_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val topReason = verdict.reasons.firstOrNull() ?: "Suspicious link detected"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠️ Suspicious link in your messages")
            .setContentText(topReason)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$topReason\n\n$url\n\nTap to see the full on-device analysis.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(url.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post; never crash the listener service.
            Log.w(TAG, "Could not post LinkGuard alert", e)
        }
    }
}
