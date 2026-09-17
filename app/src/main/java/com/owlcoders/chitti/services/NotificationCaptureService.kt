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
            val extras = notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

            if (text != null) {
                if (NotificationFilter.shouldProcess(text)) {
                    Log.d("ChittiCapture", "PASSED FILTER ($packageName): $title - $text")
                    
                    serviceScope.launch {
                        val engine = (application as ChittiApp).extractionEngine
                        val extracted = engine?.extract(text)
                        if (extracted != null) {
                            Log.d("ChittiCapture", "Extracted: ${extracted.what} at ${extracted.whenTime}")
                            saveToDatabase(packageName, text, extracted)
                        } else {
                            // Fallback if model fails or isn't loaded
                            saveToDatabase(packageName, text, null)
                        }
                    }
                } else {
                    Log.d("ChittiCapture", "SKIPPED: $text")
                }
            }
        }
    }
    
    private fun saveToDatabase(sourceApp: String, rawText: String, extracted: ExtractedData?) {
        val app = application as ChittiApp
        serviceScope.launch {
            app.database.eventDao().insertEvent(
                CapturedEvent(
                    sourceApp = sourceApp,
                    rawText = rawText,
                    extractedWhat = extracted?.what,
                    extractedWhen = extracted?.whenTime,
                    extractedWho = extracted?.who,
                    status = if (extracted != null) "extracted" else "pending",
                    timestamp = System.currentTimeMillis()
                )
            )
            Log.d("ChittiCapture", "Saved to DB")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
