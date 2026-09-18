package com.owlcoders.chitti.automation

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import com.owlcoders.chitti.ChittiApp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.db.entities.Memory
import com.owlcoders.chitti.services.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AssistantResponse(
    val message: String,
    val spokenText: String = message,
    val actionType: ActionCategory = ActionCategory.CONVERSATION,
    val actionSuccess: Boolean = true,
    val actionLabel: String? = null
)

enum class ActionCategory {
    APP_LAUNCH,
    DEVICE_CONTROL,
    TASK_SCHEDULE,
    TYPING,
    CONVERSATION
}

/**
 * Intelligent Assistant Intent Dispatcher.
 * Translates natural speech and chat into concrete Android actions, app launches,
 * deep file discoveries (including files from long ago), system controls, and spoken TTS feedback.
 *
 * When an [ActionExecutor] is supplied, reminders are created through it and every device
 * action is written to the automation audit log (the "Actions" screen).
 */
class AssistantIntentDispatcher(
    private val context: Context,
    private val appLauncher: AppLauncher,
    private val ttsEngine: TtsEngine,
    private val actionExecutor: ActionExecutor? = null
) {
    private val tag = "ChittiDispatcher"
    private var flashlightOn = false

    private val prefixRegex = Regex("^(hey chitti|ok chitti|chitti|please|can you|could you)[,\\s]*", RegexOption.IGNORE_CASE)

    suspend fun processQuery(
        query: String,
        events: List<CapturedEvent> = emptyList(),
        memories: List<Memory> = emptyList(),
        shouldSpeak: Boolean = true
    ): AssistantResponse = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        // Strip the wake-word/politeness prefix from the ORIGINAL text so that commands which
        // need the user's casing ("type Hello Ravi") slice the same string we matched on.
        val cleaned = trimmed.replace(prefixRegex, "").trim()
        val lower = cleaned.lowercase()
        Log.d(tag, "Processing query: '$trimmed' (cleaned: '$lower')")

        fun reply(
            message: String,
            spoken: String = message,
            type: ActionCategory = ActionCategory.CONVERSATION,
            success: Boolean = true,
            label: String? = null
        ): AssistantResponse {
            if (shouldSpeak) ttsEngine.speak(spoken)
            return AssistantResponse(
                message = message,
                spokenText = spoken,
                actionType = type,
                actionSuccess = success,
                actionLabel = label
            )
        }

        if (lower.isBlank()) {
            return@withContext reply("I didn't catch that. Try \"Open WhatsApp\" or \"What's pending today?\"", success = false, label = "Assistant")
        }

        // 1. Identity & Introduction
        if (lower.contains("who are you") || lower.contains("what is your name") || lower.contains("your name") ||
            Regex("who (made|created|built|developed|designed) you").containsMatchIn(lower) || lower.contains("what are you")
        ) {
            return@withContext reply(
                "I am Chitti, your on-device mobile AI assistant. I can open apps like WhatsApp and YouTube, set reminders, manage your agenda, and assist with your daily tasks completely privately on your device.",
                label = "About Chitti"
            )
        }

        // 2. Greetings & Politeness
        if (lower.matches(Regex("^(h+i+|h+e+l+o+|h+e+y+|yo+|namaste|hola|good (morning|evening|afternoon|night))\\b.*"))) {
            return@withContext reply(
                "Hello! I'm Chitti. What can I do for you right now? You can say \"Open WhatsApp\", \"Open YouTube\", \"Remind me to call mom in 30 minutes\", or ask about your schedule.",
                label = "Greeting"
            )
        }

        if (lower.contains("how are you")) {
            return@withContext reply("I'm doing great and running fast on your device! Ready to help you with apps, files, reminders, or tasks.", label = "Status")
        }

        // 3. Reminders: "remind me to call mom in 30 minutes", "set a reminder at 5 pm to submit the deck"
        if (Regex("^(remind me|set (a )?reminder|reminder)\\b").containsMatchIn(lower)) {
            return@withContext handleReminder(cleaned, ::reply)
        }

        // 4. Capabilities / Help
        if (lower.contains("what can you do") || lower.contains("what do you do") ||
            lower.contains("how can you help") || lower == "help" || lower == "features"
        ) {
            val message = "Here is what I can do for you:\n• Launch Apps: \"Open WhatsApp\", \"Open YouTube\", \"Open Camera\"\n• Reminders: \"Remind me to call mom in 30 minutes\"\n• Device Control: \"Turn on flashlight\", \"Turn off torch\"\n• Agenda & Tasks: \"What's pending today?\", \"My schedule\"\n• Local AI Q&A and instant voice responses."
            val spoken = "I can open apps like WhatsApp and YouTube, set reminders, control your flashlight, and manage your daily tasks."
            return@withContext reply(message, spoken, label = "Capabilities")
        }

        // 5. Current Time & Date
        if (lower.contains("what time") || lower.contains("current time") || lower.contains("what is the time") ||
            lower.contains("today's date") || lower.contains("what day is it") || lower.contains("what date is it")
        ) {
            val now = Date()
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            return@withContext reply("It's ${timeFormat.format(now)} on ${dateFormat.format(now)}.", label = "Clock")
        }

        // 6. Jokes
        if (Regex("\\bjokes?\\b").containsMatchIn(lower)) {
            val jokes = listOf(
                "Why do programmers prefer dark mode? Because light attracts bugs! 😄",
                "Why did the smartphone go to school? To become a smart phone! 📱",
                "There are 10 types of people in the world: those who understand binary, and those who don't. 🤖",
                "Why did the developer go broke? Because they used up all their cache! 💰"
            )
            return@withContext reply(jokes.random(), label = "Humor")
        }

        // 7. App Launching Intent: "open whatsapp", "launch youtube", "open camera", "open ..."
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ") || lower.startsWith("go to ")) {
            val appResult = appLauncher.launch(lower)
            actionExecutor?.recordExternal(
                ActionId.OPEN_APP,
                mapOf("query" to lower, "app" to (appResult.appName ?: "")),
                appResult.success,
                appResult.message
            )
            val speech = if (appResult.success) appResult.message else "I couldn't find that app on your phone."
            return@withContext reply(appResult.message, speech, ActionCategory.APP_LAUNCH, appResult.success, appResult.appName ?: "App")
        }

        // 8. Typing Action: "type ..."
        if (lower.startsWith("type ")) {
            val textToType = cleaned.substring(5).trim() // Keep original casing
            if (textToType.isNotEmpty()) {
                val a11yService = com.owlcoders.chitti.services.ChittiAccessibilityService.instance
                return@withContext if (a11yService != null) {
                    a11yService.typeTextGlobal(textToType)
                    reply(
                        "Switch to the app you want to type in within 10 seconds. I'll type: \"$textToType\"",
                        "Switch to the app where you want this typed. I'll type it there.",
                        ActionCategory.TYPING,
                        true,
                        "Typing Action"
                    )
                } else {
                    reply(
                        "Accessibility service is not enabled. Enable Chitti in Settings > Accessibility to let me type.",
                        "Accessibility service is not enabled. I cannot type right now.",
                        ActionCategory.TYPING,
                        false,
                        "Typing Failed"
                    )
                }
            }
        }

        // 9. Flashlight / Torch Intent: "turn on flashlight", "toggle flashlight", "torch off"
        if (Regex("\\b(flashlight|flash light|torch)\\b").containsMatchIn(lower)) {
            val turnOff = Regex("\\b(off|stop|disable)\\b").containsMatchIn(lower)
            val turnOn = Regex("\\b(on|enable)\\b").containsMatchIn(lower)
            val target = when {
                turnOff && !turnOn -> false
                turnOn && !turnOff -> true
                else -> !flashlightOn // "toggle flashlight" or ambiguous
            }
            val (ok, resultMsg) = toggleFlashlight(target)
            actionExecutor?.recordExternal(ActionId.TOGGLE_FLASHLIGHT, mapOf("on" to target.toString()), ok, resultMsg)
            return@withContext reply(resultMsg, type = ActionCategory.DEVICE_CONTROL, success = ok, label = "Flashlight")
        }

        // 11. Task & Agenda Queries: "what's pending today?", "what are my tasks?", "what do I have scheduled?"
        if (lower.contains("what's pending") || lower.contains("whats pending") || lower.contains("my tasks") ||
            lower.contains("schedule today") || lower.contains("my schedule") ||
            lower.contains("what do i have") || lower.contains("agenda") || lower.contains("commitments")
        ) {
            val pendingEvents = events.filter { it.status != "done" }
            val message = if (pendingEvents.isNotEmpty()) {
                val summary = pendingEvents.take(3).joinToString("; ") {
                    "${it.extractedWhat ?: "Task"} at ${it.extractedWhen ?: "unspecified time"}"
                }
                "You have ${pendingEvents.size} pending task${if (pendingEvents.size > 1) "s" else ""}: $summary."
            } else {
                "Your schedule is clear! You have no pending tasks today."
            }
            return@withContext reply(message, type = ActionCategory.TASK_SCHEDULE, label = "Schedule")
        }

        // 12. Anything else: answer it with the on-device model.
        val app = context.applicationContext as? ChittiApp
        val engine = app?.extractionEngine

        // Questions explicitly about what the user has saved go through the memory lookup;
        // everything else is a normal question and gets a normal answer.
        val asksAboutMemory = Regex("\\b(my|i have|do i|did i|remember|saved|remind(ed)? me)\\b").containsMatchIn(lower)
        if (asksAboutMemory && engine != null) {
            val ragResult = engine.generateRagResponse(cleaned.take(300), events)
            if (!ragResult.contains("I don't have that in my memory")) {
                return@withContext reply(ragResult, label = "Memory")
            }
        }

        // Only hand the model the user's notes when the question is actually about them.
        // Injecting context into every prompt made it answer general questions with
        // "the provided text does not contain that", because it read every prompt as closed-book.
        val answer = engine?.generateChatResponse(
            query = cleaned,
            contextEvents = if (asksAboutMemory) events else emptyList(),
            memoryFacts = if (asksAboutMemory) memories.map { it.key to it.value } else emptyList()
        )
        if (!answer.isNullOrBlank()) {
            Log.d(tag, "Answered from the on-device model")
            return@withContext reply(answer, label = "Answer")
        }

        // The model is still loading, unavailable, or produced nothing. Say that honestly instead
        // of printing the same feature list for every question.
        Log.w(tag, "No model answer available for: $lower")
        val fallback = when {
            engine == null || !engine.isLlmLoaded() ->
                "My on-device model isn't loaded, so I can only do actions right now. Try \"Open WhatsApp\", \"Remind me in 10 minutes\", or \"What's pending?\"."
            memories.isEmpty() && events.isEmpty() ->
                "I couldn't answer that one. I don't have anything saved about you yet, so ask me to open an app, set a reminder, or save a fact in Memory."
            else ->
                "I couldn't answer that one. Try rephrasing it, or ask about your agenda, a reminder, or opening an app."
        }
        reply(fallback, success = false, label = "No answer")
    }

    // ------------------------------------------------------------------------------------
    // Reminders
    // ------------------------------------------------------------------------------------

    internal data class ParsedReminder(val title: String, val triggerTime: Long)

    private suspend fun handleReminder(
        cleaned: String,
        reply: (String, String, ActionCategory, Boolean, String?) -> AssistantResponse
    ): AssistantResponse {
        val parsed = parseReminder(cleaned)
        if (parsed == null) {
            val msg = "When should I remind you? Say something like \"Remind me to call mom in 30 minutes\" or \"Remind me at 5 pm to submit the deck\"."
            return reply(msg, msg, ActionCategory.TASK_SCHEDULE, false, "Reminder")
        }
        val executor = actionExecutor
        if (executor == null) {
            val msg = "Reminders are not available right now."
            return reply(msg, msg, ActionCategory.TASK_SCHEDULE, false, "Reminder")
        }
        val result = executor.execute(
            ActionId.CREATE_REMINDER,
            mapOf("title" to parsed.title, "triggerTime" to parsed.triggerTime.toString()),
            userConfirmed = true // the spoken/typed command is the confirmation
        )
        val whenText = SimpleDateFormat("h:mm a, EEE d MMM", Locale.getDefault()).format(Date(parsed.triggerTime))
        val msg = if (result.success) "Reminder set for $whenText: ${parsed.title}" else "Couldn't set the reminder: ${result.message}"
        return reply(msg, msg, ActionCategory.TASK_SCHEDULE, result.success, "Reminder")
    }

    /**
     * Understands "in N minutes/hours/seconds", "at 5", "at 5:30 pm", "tomorrow at 9", "at 17:00".
     * Returns null when no time could be found.
     */
    internal fun parseReminder(text: String, nowMillis: Long = System.currentTimeMillis()): ParsedReminder? {
        var body = text.replace(
            Regex("^(remind me to|remind me|set a reminder to|set a reminder for|set a reminder|set reminder to|set reminder|reminder to|reminder)\\b[,\\s]*", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        var trigger: Long? = null

        // Relative: "in 30 minutes", "in an hour", "in 2 hrs"
        val rel = Regex("\\bin\\s+(\\d+|a|an|one|two|three|four|five|ten|fifteen|twenty|thirty|forty five|forty-five)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)\\b", RegexOption.IGNORE_CASE).find(body)
        if (rel != null) {
            val n = when (rel.groupValues[1].lowercase()) {
                "a", "an", "one" -> 1; "two" -> 2; "three" -> 3; "four" -> 4; "five" -> 5; "ten" -> 10
                "fifteen" -> 15; "twenty" -> 20; "thirty" -> 30; "forty five", "forty-five" -> 45
                else -> rel.groupValues[1].toIntOrNull() ?: 0
            }
            val unit = rel.groupValues[2].lowercase()
            val ms = when {
                unit.startsWith("sec") -> n * 1_000L
                unit.startsWith("min") -> n * 60_000L
                else -> n * 3_600_000L
            }
            if (n > 0) {
                trigger = nowMillis + ms
                body = body.replace(rel.value, " ")
            }
        }

        // Absolute: "at 5", "at 5:30 pm", "tomorrow at 9am", "5 pm tomorrow"
        if (trigger == null) {
            val abs = Regex("\\b(tomorrow\\s+)?(?:at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?|(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.))(\\s+tomorrow)?\\b", RegexOption.IGNORE_CASE).find(body)
            if (abs != null) {
                val g = abs.groupValues
                val hourStr = g[2].ifBlank { g[5] }
                val minStr = g[3].ifBlank { g[6] }
                val ampm = g[4].ifBlank { g[7] }.lowercase().replace(".", "")
                val tomorrow = g[1].isNotBlank() || g[8].isNotBlank()
                var hour = hourStr.toIntOrNull() ?: -1
                val minute = minStr.toIntOrNull() ?: 0
                if (hour in 0..23 && minute in 0..59) {
                    if (ampm == "pm" && hour < 12) hour += 12
                    if (ampm == "am" && hour == 12) hour = 0
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = nowMillis
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (tomorrow) cal.add(Calendar.DAY_OF_YEAR, 1)
                    // "at 5" said at 15:00 with no am/pm means 17:00, not tomorrow 05:00
                    if (!tomorrow && ampm.isBlank() && cal.timeInMillis <= nowMillis && hour < 12) {
                        cal.add(Calendar.HOUR_OF_DAY, 12)
                    }
                    if (cal.timeInMillis <= nowMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
                    trigger = cal.timeInMillis
                    body = body.replace(abs.value, " ")
                }
            }
        }

        if (trigger == null) return null

        val title = body
            .replace(Regex("\\b(tomorrow|today|tonight)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("^\\s*(to|that|about)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.', '-', ':')
            .ifBlank { "Reminder" }
            .take(120)
        return ParsedReminder(title, trigger)
    }

    // ------------------------------------------------------------------------------------
    // Flashlight
    // ------------------------------------------------------------------------------------

    private fun toggleFlashlight(turnOn: Boolean): Pair<Boolean, String> {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            // Pick a camera that actually has a flash unit (prefer the back camera); the first id
            // is the front camera on some devices and has no torch.
            val cameraId = cameraManager.cameraIdList
                .filter { id ->
                    cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                .sortedBy { id ->
                    if (cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) 0 else 1
                }
                .firstOrNull()
                ?: return false to "This device has no camera flash to use as a torch."
            cameraManager.setTorchMode(cameraId, turnOn)
            flashlightOn = turnOn
            true to if (turnOn) "Flashlight turned on." else "Flashlight turned off."
        } catch (e: Exception) {
            Log.e(tag, "Failed to toggle torch: ${e.message}")
            false to "Could not toggle the flashlight. It may be in use by the camera."
        }
    }
}
