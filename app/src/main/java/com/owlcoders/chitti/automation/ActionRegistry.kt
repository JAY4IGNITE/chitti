package com.owlcoders.chitti.automation

import android.Manifest

/**
 * All 17 whitelisted Android actions from plan.md §9.
 * Each action has permission requirements, confirmation requirements, and a description.
 */
@android.annotation.SuppressLint("InlinedApi")
enum class ActionId(
    val displayName: String,
    val description: String,
    val requiredPermissions: List<String> = emptyList(),
    val needsConfirmation: Boolean = false
) {
    OPEN_APP(
        "Open App",
        "Launch an installed app via package name",
    ),
    OPEN_DEEP_LINK(
        "Open Link",
        "Open a URL with Intent.ACTION_VIEW",
    ),
    CREATE_REMINDER(
        "Create Reminder",
        "Create a reminder via AlarmManager",
        // SCHEDULE_EXACT_ALARM is a special app-op, not a runtime permission: checkSelfPermission()
        // reports it denied, which blocked every reminder. The executor falls back to an inexact
        // alarm when exact alarms are not allowed.
        needsConfirmation = true
    ),
    CREATE_CALENDAR_EVENT(
        "Add to Calendar",
        "Insert event into CalendarContract.Events",
        // Uses ACTION_INSERT (the calendar app writes the row), so WRITE_CALENDAR is not needed.
        needsConfirmation = true
    ),
    DRAFT_MESSAGE(
        "Draft Message",
        "Pre-fill a share intent with ACTION_SENDTO",
    ),
    SEND_MESSAGE(
        "Send SMS",
        "Send SMS via SmsManager",
        requiredPermissions = listOf(Manifest.permission.SEND_SMS),
        needsConfirmation = true
    ),
    SHARE_TEXT(
        "Share Text",
        "Share plain text via chooser",
    ),
    OPEN_SETTINGS(
        "Open Settings",
        "Launch Android Settings",
    ),
    TOGGLE_FLASHLIGHT(
        "Toggle Flashlight",
        "Turn camera flash on/off",
        // CameraManager.setTorchMode() needs no permission.
    ),
    START_VOICE_INPUT(
        "Voice Input",
        "Launch STT UI",
        requiredPermissions = listOf(Manifest.permission.RECORD_AUDIO),
    ),
    SET_PRIORITY(
        "Set Priority",
        "Update priority field of a task",
    ),
    MARK_DONE(
        "Mark Done",
        "Set task status to DONE",
    ),
    SNOOZE(
        "Snooze",
        "Delay reminder/deadline by N minutes",
    ),
    DELETE_TASK(
        "Delete Task",
        "Remove task and related rows",
        needsConfirmation = true
    ),
    ADD_TO_MEMORY(
        "Add to Memory",
        "Insert or update a row in memories",
    ),
    QUERY_MEMORY(
        "Query Memory",
        "Return matching memories via search",
    ),
    LAUNCH_CAMERA(
        "Launch Camera",
        "Open camera for picture capture",
        requiredPermissions = listOf(Manifest.permission.CAMERA),
    );

    companion object {
        fun fromId(id: String): ActionId? = entries.find { it.name == id }
    }
}
