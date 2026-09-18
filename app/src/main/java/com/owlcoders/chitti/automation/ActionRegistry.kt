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
        requiredPermissions = listOf(Manifest.permission.SCHEDULE_EXACT_ALARM),
        needsConfirmation = true
    ),
    CREATE_CALENDAR_EVENT(
        "Add to Calendar",
        "Insert event into CalendarContract.Events",
        requiredPermissions = listOf(Manifest.permission.WRITE_CALENDAR),
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
    SEARCH_FILES(
        "Search Files",
        "Query local documents + file system",
        requiredPermissions = listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
    ),
    OPEN_SETTINGS(
        "Open Settings",
        "Launch Android Settings",
    ),
    TOGGLE_FLASHLIGHT(
        "Toggle Flashlight",
        "Turn camera flash on/off",
        requiredPermissions = listOf(Manifest.permission.CAMERA),
    ),
    START_VOICE_INPUT(
        "Voice Input",
        "Launch STT UI",
        requiredPermissions = listOf(Manifest.permission.RECORD_AUDIO),
    ),
    SHOW_OCR_RESULTS(
        "Show OCR",
        "Display OCR-extracted text in a dialog",
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
    ),
    LAUNCH_FILE_PICKER(
        "Pick File",
        "Open SAF to pick a file",
    );

    companion object {
        fun fromId(id: String): ActionId? = entries.find { it.name == id }
    }
}
