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
            .replace(Regex("^(chitti|hey chitti|ok chitti|please|can you|could you)\\s*"), "")
            .trim()
        Log.d(tag, "Processing query: '$trimmed' (cleaned: '$lower')")

        // 1. Identity & Introduction
        if (lower.contains("who are you") || lower.contains("what is your name") || lower.contains("who made you")) {
            val reply = "I am Chitti, your on-device mobile AI assistant. I can open apps like WhatsApp and YouTube, find documents from today or long ago, manage your agenda, and assist with your daily tasks completely privately on your device."
            if (shouldSpeak) ttsEngine.speak(reply)
            return@withContext AssistantResponse(
                message = reply,
                spokenText = reply,
                actionType = ActionCategory.CONVERSATION,
                actionSuccess = true,
                actionLabel = "About Chitti"
            )
        }

        // 2. Greetings & Politeness
        if (lower.matches(Regex("^(hi|hello|hey|yo|namaste|good morning|good evening|good afternoon|good night)\\b.*"))) {
            val reply = "Hello! I'm Chitti. What can I do for you right now? You can say \"Open WhatsApp\", \"Open YouTube\", \"Find old files\", or ask about your schedule."
            if (shouldSpeak) ttsEngine.speak(reply)
            return@withContext AssistantResponse(
                message = reply,
                spokenText = reply,
                actionType = ActionCategory.CONVERSATION,
                actionSuccess = true,
                actionLabel = "Greeting"
            )
        }

        if (lower.contains("how are you")) {
            val reply = "I'm doing great and running fast on your device! Ready to help you with apps, files, or tasks."
            if (shouldSpeak) ttsEngine.speak(reply)
            return@withContext AssistantResponse(
                message = reply,
                spokenText = reply,
                actionType = ActionCategory.CONVERSATION,
                actionSuccess = true,
                actionLabel = "Status"
            )
        }

        // 3. Capabilities / Help
        if (lower.contains("what can you do") || lower.contains("help") || lower == "features") {
            val reply = "Here is what I can do for you:\n• Launch Apps: \"Open WhatsApp\", \"Open YouTube\", \"Open Camera\"\n• Find Files: \"Find files from long ago\", \"Search documents\"\n• Device Control: \"Turn on flashlight\", \"Turn off torch\"\n• Agenda & Tasks: \"What's pending today?\", \"My schedule\"\n• Local AI Q&A and instant voice responses."
            val spoken = "I can open apps like WhatsApp and YouTube, find documents from today or long ago, control your flashlight, and manage your daily tasks."
            if (shouldSpeak) ttsEngine.speak(spoken)
            return@withContext AssistantResponse(
                message = reply,
                spokenText = spoken,
                actionType = ActionCategory.CONVERSATION,
                actionSuccess = true,
                actionLabel = "Capabilities"
            )
        }

        // 4. Current Time & Date
        if (lower.contains("what time") || lower.contains("current time") || lower.contains("what is the time") ||
            lower.contains("today's date") || lower.contains("what day is it") || lower.contains("what date is it")) {
            val now = java.util.Date()
            val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            val dateFormat = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault())
            val reply = "It's ${timeFormat.format(now)} on ${dateFormat.format(now)}."
            if (shouldSpeak) ttsEngine.speak(reply)
            return@withContext AssistantResponse(
                message = reply,
                spokenText = reply,
                actionType = ActionCategory.CONVERSATION,
                actionSuccess = true,
                actionLabel = "Clock"
            )
        }

        // 5. Jokes
        if (lower.contains("tell me a joke") || lower.contains("joke")) {
            val jokes = listOf(
                "Why do programmers prefer dark mode? Because light attracts bugs! 😄",
                "Why did the smartphone go to school? To become a smart phone! 📱",
                "There are 10 types of people in the world: those who understand binary, and those who don't. 🤖",
                "Why did the developer go broke? Because they used up all their cache! 💰"
            )
            val joke = jokes.random()
            if (shouldSpeak) ttsEngine.speak(joke)
            return@withContext AssistantResponse(
                message = joke,
                spokenText = joke,
                actionType = ActionCategory.CONVERSATION,
                actionSuccess = true,
                actionLabel = "Humor"
            )
        }

        // 6. App Launching Intent: "open whatsapp", "launch youtube", "open camera", "open ..."
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ") || lower.startsWith("go to ")) {
            val appResult = appLauncher.launch(lower)
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

        // 7. Flashlight / Torch Intent: "turn on flashlight", "toggle flashlight", "torch on"
        if (lower.contains("flashlight") || lower.contains("torch")) {
            val isTurnOff = lower.contains("off") || lower.contains("stop") || lower.contains("disable")
            val isTurnOn = lower.contains("on") || lower.contains("enable") || !isTurnOff
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

        // 8. File Finder Intent: "find file ...", "search files for ...", "find long ago documents", "find old files"
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

        // 9. Task & Agenda Queries: "what's pending today?", "what are my tasks?", "what do I have scheduled?"
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

        // 10. General Assistant AI Q&A via ExtractionEngine / Gemma RAG
        val app = context.applicationContext as? ChittiApp
        val extractionEngine = app?.extractionEngine
        val ragResult = extractionEngine?.generateRagResponse(trimmed, events)

        val finalResponse = if (ragResult != null && !ragResult.contains("I don't have that in my memory")) {
            ragResult
        } else {
            "I'm Chitti, your on-device AI assistant. You can ask me to open apps like WhatsApp or YouTube, find documents and files from today or long ago, manage your agenda, or toggle your flashlight."
        }

        if (shouldSpeak) {
            ttsEngine.speak(finalResponse)
        }

        AssistantResponse(
            message = finalResponse,
            spokenText = finalResponse,
            actionType = ActionCategory.CONVERSATION,
            actionSuccess = true,
            actionLabel = "Assistant"
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
