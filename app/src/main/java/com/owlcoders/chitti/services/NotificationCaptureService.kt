package com.owlcoders.chitti.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.owlcoders.chitti.ChittiApp
import com.owlcoders.chitti.db.CapturedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationCaptureService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val packageName = it.packageName
            val notification = it.notification
            
            // IGNORE ONGOING AND SYSTEM NOTIFICATIONS
            if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 || 
                (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
                return
            }
            if (packageName == "android" || packageName.startsWith("com.android.")) {
                return
            }

            val extras = notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

            if (text != null) {
                if (NotificationFilter.shouldProcess(text)) {
                    Log.d("ChittiCapture", "PASSED FILTER ($packageName): $title - $text")
                    
                    serviceScope.launch {
                        val engine = (application as ChittiApp).extractionEngine
                        val extracted = engine?.extract(text)
                        
                        // ONLY SAVE IF IT'S ACTUALLY A TASK OR LLM IS UNAVAILABLE
                        if (extracted != null) {
                            if (extracted.what.isNotBlank()) {
                                Log.d("ChittiCapture", "Extracted: ${extracted.what} at ${extracted.whenTime}")
                                saveToDatabase(packageName, text, extracted, notification.contentIntent)
                            } else {
                                Log.d("ChittiCapture", "LLM determined this is not a task. Skipping.")
                            }
                        } else if (engine == null) {
                            // Fallback ONLY if model isn't loaded yet (so the demo still works initially)
                            saveToDatabase(packageName, text, null, notification.contentIntent)
                        }
                    }
                } else {
                    Log.d("ChittiCapture", "SKIPPED: $text")
                }
            }
        }
    }
    
    private fun saveToDatabase(sourceApp: String, rawText: String, extracted: ExtractedData?, contentIntent: android.app.PendingIntent?) {
        val app = application as ChittiApp
        serviceScope.launch {
            val id = app.database.eventDao().insertEvent(
                CapturedEvent(
                    sourceApp = sourceApp,
                    rawText = rawText,
                    extractedWhat = extracted?.what,
                    extractedWhen = extracted?.whenTime,
                    extractedWho = extracted?.who,
                    category = extracted?.category,
                    urgency = extracted?.urgency,
                    status = if (extracted != null) "extracted" else "pending",
                    timestamp = System.currentTimeMillis()
                )
            )
            if (contentIntent != null) {
                app.replyIntents[id.toInt()] = contentIntent
            }
            Log.d("ChittiCapture", "Saved to DB with ID: $id")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
