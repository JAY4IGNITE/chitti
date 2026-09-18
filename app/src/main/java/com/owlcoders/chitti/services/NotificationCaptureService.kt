package com.owlcoders.chitti.services

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.owlcoders.chitti.ChittiApp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.db.entities.NotificationEntity
import com.owlcoders.chitti.db.entities.Task
import com.owlcoders.chitti.security.LinkGuardNotifier
import com.owlcoders.chitti.security.LinkScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Enhanced NotificationListenerService per plan.md §2.2.
 * Flow: SEE → PREPROCESS → CLASSIFY → EXTRACT → SCORE → DEDUP → STORE → ACT
 */
class NotificationCaptureService : NotificationListenerService() {

    // SupervisorJob: an exception while processing one notification must not cancel the
    // scope and silently stop all future captures (previous behaviour with a plain Job).
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Serialises the duplicate check + insert: messaging apps post the same notification
    // twice within milliseconds, and two coroutines could both pass findDuplicate().
    private val dedupMutex = Mutex()

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val notification = sbn?.notification ?: return
        val packageName = sbn.packageName

        // IGNORE ONGOING AND SYSTEM NOTIFICATIONS
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        ) {
            return
        }
        if (packageName == "android" || packageName.startsWith("com.android.")) {
            return
        }
        // Never process our own notifications (reminders would feed back into capture)
        if (packageName == applicationContext.packageName) {
            return
        }

        val extras = notification.extras
        // getCharSequence: WhatsApp/Telegram post Spannable titles, for which getString() returns null.
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        // Prefer BIG_TEXT: EXTRA_TEXT is often truncated for long messages
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim()
        if (text.isNullOrBlank()) return
        val contentIntent = notification.contentIntent

        serviceScope.launch {
            try {
                processNotification(packageName, title, text, contentIntent)
            } catch (t: Throwable) {
                Log.e(TAG, "Capture failed for $packageName: ${t.message}", t)
            }
        }
    }

    private suspend fun processNotification(
        packageName: String,
        title: String,
        text: String,
        contentIntent: PendingIntent?
    ) {
        val app = application as ChittiApp

        // STEP 1 + 2 + 3: hash, atomic duplicate check, store raw notification
        val hash = DeduplicationEngine.generateHash(packageName, text)
        val cutoff = DeduplicationEngine.getDeduplicationCutoff()
        val notifId: Long = dedupMutex.withLock {
            val duplicate = app.database.notificationDao().findDuplicate(hash, cutoff)
            if (duplicate != null) {
                -1L
            } else {
                app.database.notificationDao().insertNotification(
                    NotificationEntity(
                        packageName = packageName,
                        postTime = System.currentTimeMillis(),
                        rawTitle = title,
                        rawText = text,
                        processed = false,
                        hash = hash
                    )
                )
            }
        }
        if (notifId < 0) {
            Log.d(TAG, "DEDUP: Skipping duplicate notification: ${text.take(80)}")
            return
        }
        Log.d(TAG, "Stored raw notification #$notifId from $packageName")

        // STEP 3.5: LinkGuard — scan any URLs in the message, warn on danger
        try {
            for (url in LinkScanner.extractUrls(text)) {
                val verdict = LinkScanner.scan(url)
                if (verdict.level == LinkScanner.RiskLevel.DANGER) {
                    LinkGuardNotifier.showDangerAlert(applicationContext, url, verdict)
                    Log.w(TAG, "LinkGuard flagged dangerous URL: $url (${verdict.score})")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "LinkGuard scan failed: ${e.message}")
        }

        // STEP 4: Cheap filter (regex-based pre-screening)
        if (!NotificationFilter.shouldProcess(text)) {
            Log.d(TAG, "SKIPPED by filter: ${text.take(80)}")
            return
        }
        Log.d(TAG, "PASSED FILTER ($packageName): $title - ${text.take(80)}")

        // STEP 5: extraction. Wait briefly for the LLM warm-up; if it is still not ready, use the
        // rule-based extractor instead of parking an empty "pending" event that nothing revisits.
        val engine = awaitEngine(app)
        val extracted = engine?.extract(text) ?: ExtractionEngine.ruleBased(text)

        // STEP 6: Only save as task if extraction found something actionable
        if (extracted != null && extracted.what.isNotBlank()) {
            // STEP 7: Importance scoring
            val importanceScore = ImportanceScorer.score(
                extractedWhat = extracted.what,
                extractedWhen = extracted.whenTime,
                urgency = extracted.urgency,
                category = extracted.category,
                rawText = text
            )
            val priority = ImportanceScorer.toPriority(importanceScore)
            Log.d(TAG, "Extracted: ${extracted.what} | Score: $importanceScore | Priority: $priority")

            // STEP 8: Save to events table with priority (blank fields are stored as null)
            val eventId = app.database.eventDao().insertEvent(
                CapturedEvent(
                    sourceApp = packageName,
                    rawText = text,
                    extractedWhat = extracted.what,
                    extractedWhen = extracted.whenTime.ifBlank { null },
                    extractedWho = extracted.who.ifBlank { null },
                    category = extracted.category,
                    urgency = extracted.urgency,
                    status = "extracted",
                    timestamp = System.currentTimeMillis()
                )
            )

            // Also save to tasks table per plan.md §8
            app.database.taskDao().insertTask(
                Task(
                    title = extracted.what,
                    description = "Source: $packageName\nDetails: ${text.take(500)}\nWhen: ${extracted.whenTime.ifBlank { "unspecified" }}\nCategory: ${extracted.category}",
                    status = "pending",
                    priority = priority,
                    sourceType = "notification",
                    sourceId = notifId.toInt()
                )
            )

            // Cache reply intent
            if (contentIntent != null) {
                app.replyIntents[eventId.toInt()] = contentIntent
            }

            app.database.notificationDao().markProcessed(notifId.toInt())
            Log.d(TAG, "Saved event #$eventId, notification #$notifId marked processed")
        } else {
            Log.d(TAG, "Not a task. Skipping event creation.")
            app.database.notificationDao().markProcessed(notifId.toInt())
        }
    }

    /** Polls for the warmed-up engine for up to ~15 s (model mapping takes a few seconds). */
    private suspend fun awaitEngine(app: ChittiApp): ExtractionEngine? {
        var attempts = 0
        while (app.extractionEngine == null && attempts < 30) {
            delay(500)
            attempts++
        }
        return app.extractionEngine
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    private companion object {
        const val TAG = "ChittiCapture"
    }
}
