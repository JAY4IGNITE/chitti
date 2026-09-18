package com.owlcoders.chitti.automation

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import com.owlcoders.chitti.ChittiApp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.services.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AssistantResponse(
    val message: String,
    val spokenText: String = message,
    val actionType: ActionCategory = ActionCategory.CONVERSATION,
    val actionSuccess: Boolean = true,
    val actionLabel: String? = null,
    val matchingFiles: List<DeviceFile> = emptyList(),
    val matchingDocuments: List<DocumentSearchResult> = emptyList()
)

enum class ActionCategory {
    APP_LAUNCH,
    DEVICE_CONTROL,
    DOCUMENT_SEARCH,
    FILE_SEARCH,
    TASK_SCHEDULE,
    CONVERSATION
}

/**
 * Intelligent Assistant Intent Dispatcher.
 * Translates natural speech and chat into concrete Android actions, app launches,
 * deep file discoveries (including files from long ago), system controls, and spoken TTS feedback.
 */
class AssistantIntentDispatcher(
    private val context: Context,
    private val appLauncher: AppLauncher,
    private val fileFinder: FileFinder,
    private val documentFinder: DocumentFinder,
    private val ttsEngine: TtsEngine
) {
    private val tag = "ChittiDispatcher"
    private var flashlightOn = false

    suspend fun processQuery(
        query: String,
        events: List<CapturedEvent> = emptyList(),
        shouldSpeak: Boolean = true
    ): AssistantResponse = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val lower = trimmed.lowercase()
        Log.d(tag, "Processing query: '$trimmed'")

        // 1. App Launching Intent: "open whatsapp", "launch youtube", "open camera", "open ..."
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ") || lower.startsWith("go to ")) {
            val appResult = appLauncher.launch(trimmed)
            val speech = if (appResult.success) appResult.message else "I couldn't find that app on your phone."
            if (shouldSpeak) {
                ttsEngine.speak(speech)
            }
            return@withContext AssistantResponse(
                message = appResult.message,
                spokenText = speech,
                actionType = ActionCategory.APP_LAUNCH,
                actionSuccess = appResult.success,
                actionLabel = appResult.appName ?: "App"
            )
        }

        // 2. Flashlight / Torch Intent: "turn on flashlight", "toggle flashlight", "torch on"
        if (lower.contains("flashlight") || lower.contains("torch")) {
            val isTurnOn = lower.contains("on") || !flashlightOn
            val resultMsg = toggleFlashlight(isTurnOn)
            if (shouldSpeak) {
                ttsEngine.speak(resultMsg)
            }
            return@withContext AssistantResponse(
                message = resultMsg,
                spokenText = resultMsg,
                actionType = ActionCategory.DEVICE_CONTROL,
                actionSuccess = true,
                actionLabel = "Flashlight"
            )
        }

        // 3. File Finder Intent: "find file ...", "search files for ...", "find long ago documents", "find old files"
        if (lower.startsWith("find file") || lower.startsWith("search file") ||
            lower.startsWith("find document") || lower.startsWith("search document") ||
            lower.startsWith("find doc") || lower.startsWith("search doc") ||
            lower.contains("long ago") || lower.contains("old documents") || lower.contains("find my ")) {

            val isLongAgo = lower.contains("long ago") || lower.contains("old") || lower.contains("earlier") || lower.contains("previous")
            val timeFilter = if (isLongAgo) TimeFilter.LONG_AGO else TimeFilter.ALL_TIME

            val cleanKeyword = lower
                .replace(Regex("^(find files?|search files?|find documents?|search documents?|find docs?|search docs? for|search docs?|find my|find)\\s*"), "")
                .replace(Regex("\\b(long ago|old|documents?|files?)\\b"), "")
                .trim()

            val foundFiles = fileFinder.queryFiles(
                keyword = cleanKeyword,
                timeFilter = timeFilter,
                limit = 25
            )

            val reply = if (foundFiles.isNotEmpty()) {
                val timeNote = if (isLongAgo) " from long ago" else ""
                val topNames = foundFiles.take(2).joinToString(", ") { it.name }
                "Found ${foundFiles.size} file${if (foundFiles.size > 1) "s" else ""}$timeNote including $topNames."
            } else {
                "I searched for files${if (cleanKeyword.isNotBlank()) " matching \"$cleanKeyword\"" else ""}, but couldn't find any. You can browse all storage files in File Finder."
            }

            if (shouldSpeak) {
                ttsEngine.speak(reply)
            }

            return@withContext AssistantResponse(
                message = reply,
                spokenText = reply,
                actionType = ActionCategory.FILE_SEARCH,
                actionSuccess = foundFiles.isNotEmpty(),
                actionLabel = if (isLongAgo) "Old Files" else "Files",
                matchingFiles = foundFiles
            )
        }

        // 4. Task & Agenda Queries: "what's pending today?", "what are my tasks?", "what do I have scheduled?"
        if (lower.contains("what's pending") || lower.contains("my tasks") || lower.contains("schedule today") ||
            lower.contains("what do i have") || lower.contains("agenda") || lower.contains("commitments")) {

            val pendingEvents = events.filter { it.status != "done" }
            val reply = if (pendingEvents.isNotEmpty()) {
                val summary = pendingEvents.take(3).joinToString("; ") {
                    "${it.extractedWhat ?: "Task"} at ${it.extractedWhen ?: "unspecified time"}"
                }
                "You have ${pendingEvents.size} pending tasks: $summary."
            } else {
                "Your schedule is clear! You have no pending tasks today."
            }

            if (shouldSpeak) {
                ttsEngine.speak(reply)
            }

            return@withContext AssistantResponse(
                message = reply,
                spokenText = reply,
                actionType = ActionCategory.TASK_SCHEDULE,
                actionSuccess = true,
                actionLabel = "Schedule"
            )
        }

        // 5. General AI Q&A via ExtractionEngine / Gemma RAG
        val app = context.applicationContext as? ChittiApp
        val extractionEngine = app?.extractionEngine
        val aiResponse = extractionEngine?.generateRagResponse(trimmed, events)
            ?: "I'm Chitti, your on-device AI assistant. You can ask me to open apps like WhatsApp or YouTube, find any recent or long-ago files, manage your tasks, or control your device."

        if (shouldSpeak) {
            ttsEngine.speak(aiResponse)
        }

        AssistantResponse(
            message = aiResponse,
            spokenText = aiResponse,
            actionType = ActionCategory.CONVERSATION,
            actionSuccess = true
        )
    }

    private fun toggleFlashlight(turnOn: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return "No camera flashlight found on this device."
            flashlightOn = turnOn
            cameraManager.setTorchMode(cameraId, flashlightOn)
            if (flashlightOn) "Flashlight turned on." else "Flashlight turned off."
        } catch (e: Exception) {
            Log.e(tag, "Failed to toggle torch: ${e.message}")
            "Could not toggle flashlight: ${e.message}"
        }
    }
}
