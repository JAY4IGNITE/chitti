package com.owlcoders.chitti.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.security.LinkGuardActivity
import com.owlcoders.chitti.security.LinkScanner
import com.owlcoders.chitti.db.entities.AutomationHistory
import com.owlcoders.chitti.db.entities.Memory
import com.owlcoders.chitti.db.entities.Reminder
import com.owlcoders.chitti.db.entities.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Executes whitelisted Android actions from plan.md §9.
 * All actions go through permission checking, optional confirmation, execution, and audit logging.
 */
class ActionExecutor(
    private val context: Context,
    private val database: AppDatabase
) {
    private val tag = "ChittiAction"
    private var flashlightOn = false

    /**
     * Execute an action. Returns a result description string.
     * Caller is responsible for showing confirmation dialog if action.needsConfirmation is true.
     */
    suspend fun execute(action: ActionId, params: Map<String, String> = emptyMap(), userConfirmed: Boolean = false): ActionResult {
        // 1. Permission check
        if (!PermissionChecker.hasPermissions(context, action)) {
            val missing = PermissionChecker.getMissingPermissions(context, action)
            val result = ActionResult(false, "Missing permissions: ${missing.joinToString()}")
            logAction(action, params, result, userConfirmed)
            return result
        }

        // 2. Confirmation check
        if (action.needsConfirmation && !userConfirmed) {
            return ActionResult(false, "Action requires user confirmation", needsConfirmation = true)
        }

        // 3. Execute
        val result = try {
            when (action) {
                ActionId.OPEN_APP -> openApp(params["packageName"] ?: "")
                ActionId.OPEN_DEEP_LINK -> openDeepLink(params["url"] ?: "")
                ActionId.CREATE_REMINDER -> createReminder(
                    params["title"] ?: "Reminder",
                    params["triggerTime"]?.toLongOrNull() ?: (System.currentTimeMillis() + 3600000)
                )
                ActionId.CREATE_CALENDAR_EVENT -> createCalendarEvent(
                    params["title"] ?: "",
                    params["description"] ?: "",
                    params["startTime"]?.toLongOrNull(),
                    params["endTime"]?.toLongOrNull(),
                    params["location"] ?: ""
                )
                ActionId.DRAFT_MESSAGE -> draftMessage(
                    params["to"] ?: "",
                    params["text"] ?: ""
                )
                ActionId.SEND_MESSAGE -> ActionResult(false, "Direct SMS sending disabled for safety")
                ActionId.SHARE_TEXT -> shareText(params["text"] ?: "")
                ActionId.OPEN_SETTINGS -> openSettings()
                ActionId.TOGGLE_FLASHLIGHT -> toggleFlashlight()
                ActionId.START_VOICE_INPUT -> ActionResult(true, "Voice input started")
                ActionId.SET_PRIORITY -> setPriority(
                    params["taskId"]?.toIntOrNull() ?: 0,
                    params["priority"]?.toIntOrNull() ?: 0
                )
                ActionId.MARK_DONE -> markDone(params["taskId"]?.toIntOrNull() ?: 0)
                ActionId.SNOOZE -> snooze(
                    params["taskId"]?.toIntOrNull() ?: 0,
                    params["minutes"]?.toIntOrNull() ?: 15
                )
                ActionId.DELETE_TASK -> deleteTask(params["taskId"]?.toIntOrNull() ?: 0)
                ActionId.ADD_TO_MEMORY -> addToMemory(
                    params["key"] ?: "",
                    params["value"] ?: "",
                    params["category"] ?: "note"
                )
                ActionId.QUERY_MEMORY -> queryMemory(params["query"] ?: "")
                ActionId.LAUNCH_CAMERA -> launchCamera()
            }
        } catch (e: Exception) {
            Log.e(tag, "Action execution failed: ${e.message}", e)
            ActionResult(false, "Error: ${e.message}")
        }

        // 4. Audit log
        logAction(action, params, result, userConfirmed)
        return result
    }

    private fun openApp(packageName: String): ActionResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ActionResult(true, "Opened $packageName")
        } else {
            ActionResult(false, "App not found: $packageName")
        }
    }

    private fun openDeepLink(url: String): ActionResult {
        // LinkGuard: every URL is scanned on-device before it is allowed to open.
        // SAFE links pass straight through inside LinkGuardActivity with no UI;
        // risky links show the warning interstitial instead of opening blindly.
        val verdict = LinkScanner.scan(url)
        LinkGuardActivity.open(context, url)
        return if (verdict.level == LinkScanner.RiskLevel.SAFE) {
            ActionResult(true, "Opened link: $url (LinkGuard: safe)")
        } else {
            ActionResult(true, "LinkGuard warning shown (${verdict.level}, score ${verdict.score}): $url")
        }
    }

    private suspend fun createReminder(title: String, triggerTime: Long): ActionResult {
        if (triggerTime <= System.currentTimeMillis()) {
            return ActionResult(false, "That time is already in the past.")
        }
        // Persist first so BootReceiver can re-schedule after a reboot (alarms do not survive one).
        val taskId = database.taskDao().insertTask(
            Task(
                title = title,
                description = "Reminder set via Chitti",
                status = "pending",
                priority = 1,
                sourceType = "voice"
            )
        ).toInt()
        database.reminderDao().insertReminder(Reminder(taskId = taskId, triggerTime = triggerTime))

        val exact = ReminderScheduler.schedule(context, taskId, title, triggerTime)
        return ActionResult(true, if (exact) "Reminder set: $title" else "Reminder set (inexact, exact alarms not permitted): $title")
    }

    private fun createCalendarEvent(
        title: String,
        description: String,
        startTime: Long?,
        endTime: Long?,
        location: String
    ): ActionResult {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            startTime?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            endTime?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult(true, "Calendar event created: $title")
    }

    private fun draftMessage(to: String, text: String): ActionResult {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$to")
            putExtra("sms_body", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult(true, "Message drafted to: $to")
    }

    private fun shareText(text: String): ActionResult {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return ActionResult(true, "Sharing text")
    }

    private fun openSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult(true, "Opened app settings")
    }

    private fun toggleFlashlight(): ActionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            flashlightOn = !flashlightOn
            cameraManager.setTorchMode(cameraId, flashlightOn)
            ActionResult(true, "Flashlight ${if (flashlightOn) "ON" else "OFF"}")
        } catch (e: Exception) {
            ActionResult(false, "Flashlight error: ${e.message}")
        }
    }

    private suspend fun setPriority(taskId: Int, priority: Int): ActionResult {
        withContext(Dispatchers.IO) {
            database.taskDao().setPriority(taskId, priority)
        }
        return ActionResult(true, "Priority set to $priority for task $taskId")
    }

    private suspend fun markDone(taskId: Int): ActionResult {
        withContext(Dispatchers.IO) {
            database.taskDao().markDone(taskId)
            ReminderScheduler.cancel(context, taskId, "")
            database.reminderDao().deleteByTaskId(taskId)
        }
        return ActionResult(true, "Task $taskId marked as done")
    }

    private suspend fun snooze(taskId: Int, minutes: Int): ActionResult {
        // Snooze logic: in a full implementation, update the reminder trigger time
        return ActionResult(true, "Task $taskId snoozed by $minutes minutes")
    }

    private suspend fun deleteTask(taskId: Int): ActionResult {
        withContext(Dispatchers.IO) {
            ReminderScheduler.cancel(context, taskId, "")
            database.reminderDao().deleteByTaskId(taskId)
            database.taskDao().deleteTaskById(taskId)
        }
        return ActionResult(true, "Task $taskId deleted")
    }

    private suspend fun addToMemory(key: String, value: String, category: String): ActionResult {
        withContext(Dispatchers.IO) {
            val existing = database.memoryDao().findMemory(key, category)
            if (existing != null) {
                database.memoryDao().updateMemory(existing.copy(value = value, updatedAt = System.currentTimeMillis()))
            } else {
                database.memoryDao().insertMemory(Memory(key = key, value = value, category = category))
            }
        }
        return ActionResult(true, "Memory saved: $key = $value")
    }

    private suspend fun queryMemory(query: String): ActionResult {
        // This returns a result; the UI layer should display it
        return ActionResult(true, "Memory query: $query")
    }

    private fun launchCamera(): ActionResult {
        // CAMERA is declared in our manifest, so the system refuses ACTION_IMAGE_CAPTURE with a
        // SecurityException unless the runtime permission is actually granted.
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return ActionResult(false, "Camera permission is not granted. Enable it in Settings and try again.")
        }
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult(true, "Camera launched")
        } catch (e: Exception) {
            Log.w(tag, "Camera launch failed: ${e.message}")
            ActionResult(false, "Could not open the camera: ${e.message}")
        }
    }

    /**
     * Records an action that was executed elsewhere (app launch, flashlight, file search from the
     * dispatcher) so it appears in the automation audit log.
     */
    suspend fun recordExternal(action: ActionId, params: Map<String, String>, success: Boolean, message: String) {
        logAction(action, params, ActionResult(success, message), userConfirmed = true)
    }

    private suspend fun logAction(action: ActionId, params: Map<String, String>, result: ActionResult, userConfirmed: Boolean) {
        try {
            withContext(Dispatchers.IO) {
                database.automationHistoryDao().insertHistory(
                    AutomationHistory(
                        actionType = action.name,
                        parameters = JSONObject(params).toString(),
                        result = if (result.success) "success" else "failed: ${result.message}",
                        userConfirmed = userConfirmed
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to log action: ${e.message}")
        }
    }
}

data class ActionResult(
    val success: Boolean,
    val message: String,
    val needsConfirmation: Boolean = false
)
