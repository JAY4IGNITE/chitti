package com.owlcoders.chitti.services

import android.app.Notification
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
import kotlinx.coroutines.launch

/**
 * Enhanced NotificationListenerService per plan.md §2.2.
 * Flow: SEE → PREPROCESS → CLASSIFY → EXTRACT → SCORE → DEDUP → STORE → ACT
 */
class NotificationCaptureService : NotificationListenerService() {

    // SupervisorJob: an exception while processing one notification must not cancel the
    // scope and silently stop all future captures (previous behaviour with a plain Job).
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
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
            // Never process our own notifications (reminders would feed back into capture)
            if (packageName == applicationContext.packageName) {
                return
            }

            val extras = notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            // Prefer BIG_TEXT: EXTRA_TEXT is often truncated for long messages
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return

            serviceScope.launch {
                val app = application as ChittiApp

                // STEP 1: Generate hash for dedup
                val hash = DeduplicationEngine.generateHash(packageName, text)
                val cutoff = DeduplicationEngine.getDeduplicationCutoff()

                // STEP 2: Check for duplicates
                val duplicate = app.database.notificationDao().findDuplicate(hash, cutoff)
                if (duplicate != null) {
                    Log.d("ChittiCapture", "DEDUP: Skipping duplicate notification: $text")
                    return@launch
                }

                // STEP 3: Always store raw notification
                val notifId = app.database.notificationDao().insertNotification(
                    NotificationEntity(
                        packageName = packageName,
                        postTime = System.currentTimeMillis(),
                        rawTitle = title,
                        rawText = text,
                        processed = false,
                        hash = hash
                    )
                )
                Log.d("ChittiCapture", "Stored raw notification #$notifId from $packageName")

                // STEP 3.5: LinkGuard — scan any URLs in the message, warn on danger
                for (url in LinkScanner.extractUrls(text)) {
                    val verdict = LinkScanner.scan(url)
                    if (verdict.level == LinkScanner.RiskLevel.DANGER) {
                        LinkGuardNotifier.showDangerAlert(applicationContext, url, verdict)
                        Log.w("ChittiCapture", "LinkGuard flagged dangerous URL: $url (${verdict.score})")
                    }
                }

                // STEP 4: Cheap filter (regex-based pre-screening)
                if (!NotificationFilter.shouldProcess(text)) {
                    Log.d("ChittiCapture", "SKIPPED by filter: $text")
                    return@launch
                }

                Log.d("ChittiCapture", "PASSED FILTER ($packageName): $title - $text")

                // STEP 5: LLM extraction (intent classification + entity extraction)
                val engine = app.extractionEngine
                val extracted = engine?.extract(text)

                // STEP 6: Only save as task if LLM found something actionable
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

                    Log.d("ChittiCapture", "Extracted: ${extracted.what} | Score: $importanceScore | Priority: $priority")

                    // STEP 8: Save to events table (legacy) with priority
                    val eventId = app.database.eventDao().insertEvent(
                        CapturedEvent(
                            sourceApp = packageName,
                            rawText = text,
                            extractedWhat = extracted.what,
                            extractedWhen = extracted.whenTime,
                            extractedWho = extracted.who,
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
                            description = "Source: $packageName\nDetails: $text\nWhen: ${extracted.whenTime}\nCategory: ${extracted.category}",
                            status = "pending",
                            priority = priority,
                            sourceType = "notification",
                            sourceId = notifId.toInt()
                        )
                    )

                    // Cache reply intent
                    if (notification.contentIntent != null) {
                        app.replyIntents[eventId.toInt()] = notification.contentIntent
                    }

                    // Mark notification as processed
                    app.database.notificationDao().markProcessed(notifId.toInt())

                    Log.d("ChittiCapture", "Saved event #$eventId, notification #$notifId marked processed")
                } else if (extracted != null && extracted.what.isBlank()) {
                    Log.d("ChittiCapture", "LLM determined this is not a task. Skipping event creation.")
                    // Still mark notification as processed (LLM reviewed it)
                    app.database.notificationDao().markProcessed(notifId.toInt())
                } else if (engine == null) {
                    // Fallback: model not loaded yet, save raw event
                    app.database.eventDao().insertEvent(
                        CapturedEvent(
                            sourceApp = packageName,
                            rawText = text,
                            extractedWhat = null,
                            extractedWhen = null,
                            extractedWho = null,
                            category = null,
                            urgency = null,
                            status = "pending",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    Log.d("ChittiCapture", "Engine not ready. Saved raw event for later processing.")
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
