package com.owlcoders.chitti.services

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.owlcoders.chitti.db.CapturedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Intelligent Extraction Engine for Chitti.
 * Uses on-device Gemma LLM via MediaPipe GenAI when available,
 * with a high-accuracy, low-latency regex/rule-based NLP fallback
 * per plan.md §2.2, §7, and §14.
 *
 * Safety notes:
 *  - MediaPipe enforces `prompt_tokens < maxTokens` with a native RET_CHECK that aborts the
 *    whole process (it is NOT a catchable exception). Every prompt built here is therefore
 *    clamped and size-checked before inference (see [runLlm]).
 *  - Inference takes seconds on a phone, so it always runs on Dispatchers.IO regardless of
 *    the caller's dispatcher, serialized by a mutex (LlmInference is not thread-safe).
 */
class ExtractionEngine(private val context: Context, modelPath: String = "/data/local/tmp/gemma.bin") {

    private var llmInference: LlmInference? = null
    private val mutex = Mutex()

    var lastInferenceLatencyMs: Long = 0L
        private set

    var lastInferenceMode: String = "Rule-based Regex"
        private set

    init {
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            lastInferenceMode = "Gemma 2B (On-Device)"
            Log.d(TAG, "LLM Initialized successfully from $modelPath")
        } catch (t: Throwable) {
            // Throwable, not Exception: a missing native library (UnsatisfiedLinkError) or an
            // OutOfMemoryError while mapping the 1.3 GB model must degrade to regex mode
            // instead of killing the process from the warm-up coroutine.
            Log.i(TAG, "LLM unavailable, operating in rule-based mode: ${t.javaClass.simpleName}: ${t.message}")
            llmInference = null
            lastInferenceMode = "Rule-based Regex"
        }
    }

    fun isLlmLoaded(): Boolean = llmInference != null

    // ------------------------------------------------------------------------------------
    // Prompt safety helpers
    // ------------------------------------------------------------------------------------

    private fun clamp(text: String?, maxChars: Int): String {
        val single = text.orEmpty().replace(Regex("\\s+"), " ").trim()
        return if (single.length <= maxChars) single else single.take(maxChars).trimEnd() + "…"
    }

    private fun estimateTokens(prompt: String): Int {
        var ascii = 0
        var other = 0
        for (c in prompt) if (c.code < 128) ascii++ else other++
        // ~4 ASCII chars per token; Telugu/Hindi script and emoji tokenize per character or worse.
        return ascii / 4 + other * 2 + 1
    }

    /**
     * Serialized inference on the IO dispatcher.
     * Returns null when the prompt is refused (too large for the token budget) or inference fails.
     */
    private suspend fun runLlm(llm: LlmInference, prompt: String): String? {
        val est = estimateTokens(prompt)
        if (prompt.length > MAX_PROMPT_CHARS || est > MAX_PROMPT_TOKENS_EST) {
            Log.w(TAG, "Prompt refused (chars=${prompt.length}, estTokens=$est) to stay under the $MAX_TOKENS token budget")
            return null
        }
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    llm.generateResponse(prompt)
                } catch (e: Exception) {
                    Log.w(TAG, "LLM inference failed: ${e.message}")
                    null
                }
            }
        }
    }

    private fun parseJsonObject(raw: String): JSONObject? {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(cleaned.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }

    /** optString() returns the literal "null" for JSON null; normalise that (and missing keys) to "". */
    private fun jsonString(json: JSONObject, key: String): String {
        if (!json.has(key) || json.isNull(key)) return ""
        val v = json.optString(key, "").trim()
        return if (v.equals("null", ignoreCase = true)) "" else v
    }

    // ------------------------------------------------------------------------------------
    // Extraction
    // ------------------------------------------------------------------------------------

    /**
     * Extracts commitments, deadlines, or meetings from raw text.
     * Uses Gemma LLM if loaded; falls back seamlessly to rule-based NER.
     */
    suspend fun extract(text: String): ExtractedData? {
        val startTime = System.currentTimeMillis()
        val llm = llmInference

        if (llm != null) {
            val safeText = clamp(text, MAX_MESSAGE_CHARS).replace('"', '\'')
            val prompt = """
                You are a strict data extraction assistant. Your task is to extract commitments, deadlines, or meetings from the user's message.
                You must output ONLY valid JSON and nothing else. No markdown, no explanations.
                If the message does NOT contain any task, commitment, meeting, or deadline, you MUST set "what" to "".

                Schema:
                { "what": "...", "when": "...", "who": "...", "category": "Work|Personal|Academic", "urgency": "High|Medium|Low", "confidence": 0.0-1.0 }

                Example 1:
                Message: "Submit the hackathon deck by 10am tomorrow"
                Output: { "what": "Submit hackathon deck", "when": "tomorrow 10am", "who": "me", "category": "Academic", "urgency": "High", "confidence": 0.95 }

                Example 2 (Code-mixed):
                Message: "repu class unda? 9 ki?"
                Output: { "what": "class", "when": "tomorrow 9:00", "who": "unknown", "category": "Academic", "urgency": "Medium", "confidence": 0.8 }

                Example 3 (Not a task):
                Message: "hi how are you"
                Output: { "what": "", "when": "", "who": "", "category": "Personal", "urgency": "Low", "confidence": 0.0 }

                Message: "$safeText"
                Output:
            """.trimIndent()

            val rawOutput = runLlm(llm, prompt)
            if (rawOutput != null) {
                val latency = System.currentTimeMillis() - startTime
                lastInferenceLatencyMs = latency
                lastInferenceMode = "Gemma 2B (On-Device)"
                Log.d(TAG, "LLM Inference Latency: ${latency}ms")

                val json = parseJsonObject(rawOutput)
                if (json != null) {
                    val confidence = json.optDouble("confidence", 0.0)
                    return ExtractedData(
                        what = jsonString(json, "what").take(MAX_FIELD_CHARS),
                        whenTime = jsonString(json, "when").take(MAX_FIELD_CHARS),
                        who = jsonString(json, "who").take(MAX_FIELD_CHARS),
                        category = jsonString(json, "category").ifBlank { "Personal" },
                        urgency = jsonString(json, "urgency").ifBlank { "Medium" },
                        confidence = if (confidence.isNaN()) 0.0 else confidence.coerceIn(0.0, 1.0)
                    )
                }
                Log.w(TAG, "LLM output was not valid JSON, using rule-based fallback: ${rawOutput.take(120)}")
            }
        }

        // Rule-based / Regex extraction fallback
        val result = ruleBased(text)
        val latency = System.currentTimeMillis() - startTime
        lastInferenceLatencyMs = latency
        lastInferenceMode = "Rule-based Regex"
        Log.d(TAG, "Rule-based Extraction Latency: ${latency}ms, Result: ${result?.what}")
        return result
    }

    /**
     * Fast, lightweight, offline rule-based entity extractor per plan.md §2.2 & §7.
     * Handles English, Telugu-English code-mix, and Hinglish.
     */
    fun ruleBasedExtract(text: String): ExtractedData? = ruleBased(text)

    suspend fun generateSmartReply(event: CapturedEvent): String {
        val llm = llmInference
        if (llm != null) {
            val prompt = """
                You are a smart reply assistant. The user received a message containing a task.
                Draft a short, natural, and polite reply confirming that the user will do the task.
                Only output the reply text, no quotes or explanation.

                Task: ${clamp(event.extractedWhat, MAX_FIELD_CHARS)}
                Time: ${clamp(event.extractedWhen, 60)}
                Reply:
            """.trimIndent()

            val reply = runLlm(llm, prompt)?.trim()
            if (!reply.isNullOrBlank()) return reply
        }

        // Fallback smart reply
        val task = event.extractedWhat?.takeIf { it.isNotBlank() } ?: "task"
        val time = if (!event.extractedWhen.isNullOrBlank()) " by ${event.extractedWhen}" else ""
        return "Got it! I will take care of \"$task\"$time."
    }

    /**
     * Answers a general question.
     *
     * This is deliberately NOT the memory-RAG prompt: that one is told to answer only from the
     * user's task list and to say "I don't have that in my memory" otherwise, so with an empty
     * agenda it refused every question. Here the model may answer from its own knowledge and use
     * the user's context only when the question is actually about them.
     *
     * Returns null when the model is unavailable or produced nothing usable, so the caller can
     * fall back rather than print a canned line as if it were an answer.
     */
    suspend fun generateChatResponse(
        query: String,
        contextEvents: List<CapturedEvent> = emptyList(),
        memoryFacts: List<Pair<String, String>> = emptyList()
    ): String? {
        val llm = llmInference ?: return null
        val safeQuery = clamp(query, MAX_QUERY_CHARS)
        if (safeQuery.isBlank()) return null

        val facts = memoryFacts.take(MAX_MEMORY_FACTS).joinToString("\n") {
            "- ${clamp(it.first, 40)}: ${clamp(it.second, 60)}"
        }
        val tasks = contextEvents
            .filter { it.status != "done" }
            .take(MAX_CHAT_EVENTS)
            .joinToString("\n") {
                "- ${clamp(it.extractedWhat, 60).ifBlank { "Task" }} (${clamp(it.extractedWhen, 30).ifBlank { "no time set" }})"
            }
        val context = buildString {
            if (facts.isNotBlank()) append("Notes:\n").append(facts)
            if (tasks.isNotBlank()) append("Tasks:\n").append(tasks)
        }

        // This model file refuses general questions when it is addressed as an assistant:
        // even a correct <start_of_turn> chat prompt with a worked example came back with
        // "I am unable to provide information related to specific literary works". Plain
        // completion format sidesteps the persona - the model simply continues the pattern.
        val prompt = buildString {
            append("Q: What is the capital of Japan?\n")
            append("A: Tokyo.\n\n")
            append("Q: Who wrote Hamlet?\n")
            append("A: William Shakespeare.\n\n")
            if (context.isNotBlank()) append(context).append("\n\n")
            append("Q: ").append(safeQuery).append("\n")
            append("A:")
        }

        val raw = runLlm(llm, prompt) ?: return null
        val cleaned = raw
            .substringBefore("\nQ:")
            .substringBefore("<end_of_turn>")
            .replace("<eos>", " ")
            .removePrefix("A:")
            .trim()
            .trim('"')
            .trim()
        // A correct answer can be one character ("7"), so only blank output counts as no answer.
        return cleaned.takeIf { it.isNotBlank() }
    }

    suspend fun generateRagResponse(query: String, contextEvents: List<CapturedEvent>): String {
        val llm = llmInference
        val safeQuery = clamp(query, MAX_QUERY_CHARS)

        if (llm != null) {
            // Only the most recent events fit in the prompt budget; the offline fallback below
            // still searches the full list.
            val contextString = contextEvents
                .filter { it.status != "done" }
                .take(MAX_CONTEXT_EVENTS)
                .joinToString("\n") {
                    "- ${clamp(it.extractedWhat, 60).ifBlank { "Task" }} (Due: ${clamp(it.extractedWhen, 30).ifBlank { "unspecified" }}, Category: ${clamp(it.category, 20).ifBlank { "Personal" }})"
                }

            val prompt = """
                You are Chitti, a helpful personal assistant memory bot.
                Answer the user's question based ONLY on the provided context of their tasks.
                If the answer is not in the context, say "I don't have that in my memory."
                Keep it conversational but concise.

                Context (User's Tasks):
                $contextString

                Question: $safeQuery
                Answer:
            """.trimIndent()

            val answer = runLlm(llm, prompt)?.trim()
            if (!answer.isNullOrBlank()) return answer
        }

        // Fallback offline keyword matching RAG
        val queryLower = safeQuery.lowercase()
        val matching = if (queryLower.isBlank()) emptyList() else contextEvents.filter {
            it.extractedWhat?.lowercase()?.contains(queryLower) == true ||
                it.category?.lowercase()?.contains(queryLower) == true ||
                it.rawText.lowercase().contains(queryLower)
        }

        if (matching.isNotEmpty()) {
            return "Found in memory:\n" + matching.take(10).joinToString("\n") {
                "• ${it.extractedWhat ?: "Task"} (Due: ${it.extractedWhen ?: "N/A"}, Category: ${it.category ?: "Personal"})"
            }
        }

        if (contextEvents.isNotEmpty()) {
            return "Here are your active commitments:\n" + contextEvents.take(5).joinToString("\n") {
                "• ${it.extractedWhat ?: "Task"} (Due: ${it.extractedWhen ?: "N/A"}, Category: ${it.category ?: "Personal"})"
            }
        }

        return "I don't have that in my memory yet."
    }

    companion object {
        private const val TAG = "ChittiExtraction"

        /** Shared input + output token budget passed to MediaPipe. */
        private const val MAX_TOKENS = 1024
        private const val MAX_MESSAGE_CHARS = 350
        private const val MAX_FIELD_CHARS = 120
        private const val MAX_QUERY_CHARS = 300
        private const val MAX_CONTEXT_EVENTS = 8
        private const val MAX_CHAT_EVENTS = 6
        private const val MAX_MEMORY_FACTS = 6
        private const val MAX_PROMPT_CHARS = 1900
        private const val MAX_PROMPT_TOKENS_EST = 720

        private fun words(vararg w: String) =
            Regex("\\b(" + w.joinToString("|") { Regex.escape(it) } + ")\\b", RegexOption.IGNORE_CASE)

        // Whole-word matching everywhere: substring checks turned "coffee" into a fee payment
        // and "amazing" into a timed task.
        private val CASUAL = Regex(
            "^(hi|hello|hey|yo|sup|good morning|good evening|good night|how are you|kya haal|wassup|ok|okay|cool|hmm|thanks|thank you)\\b.*",
            RegexOption.IGNORE_CASE
        )
        private val TASK_WORDS = words(
            "submit", "submission", "deck", "hackathon", "class", "fee", "fees", "pay", "payment", "kattali",
            "meeting", "meet", "call", "review", "assignment", "exam", "due", "deadline", "remind", "reminder",
            "lab", "record", "project", "presentation", "standup", "schedule", "appointment", "interview",
            "deliver", "send", "bring", "transfer", "bill", "invoice", "renew", "book", "ticket", "collect",
            "pickup", "pick up", "drop", "join", "attend", "complete", "finish", "prepare", "doctor", "visit"
        )
        private val TIME_WORDS = Regex("\\b(tomorrow|today|tonight|repu|lopu|ki|by|at)\\b", RegexOption.IGNORE_CASE)
        private val CLOCK = Regex("\\b\\d{1,2}(?::\\d{2})?\\s*(am|pm)\\b", RegexOption.IGNORE_CASE)
        private val ORDINAL = Regex("\\b\\d{1,2}(st|nd|rd|th)\\b", RegexOption.IGNORE_CASE)
        private val FIN_WORDS = words("fee", "fees", "kattali", "pay", "payment", "upi", "rs", "rupees", "bill", "invoice", "transfer")
        private val ACADEMIC_WORDS = words("deck", "hackathon", "class", "lab", "assignment", "exam", "record", "lecture", "college")
        private val WORK_WORDS = words("meeting", "standup", "sync", "client", "office", "interview")
        private val URGENT_WORDS = words("urgent", "asap", "marchipovaddu", "immediately", "today", "tonight", "now")
        private val SOON_WORDS = words("tomorrow", "repu", "by", "kal")
        private val FEE_WHAT = Regex("\\b(fee kattali|pay (?:the )?fees?|fees?)\\b", RegexOption.IGNORE_CASE)

        private val TIME_PATTERNS = listOf(
            Regex("\\b(tomorrow\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(today\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(repu\\s*\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|ki)?)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(\\d{1,2}(?:st|nd|rd|th)?\\s+lopu)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(by\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?\\s*(?:tomorrow|today)?)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(at\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm))\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(tomorrow|today|tonight|repu)\\b", RegexOption.IGNORE_CASE)
        )

        /**
         * Static rule-based extractor. Also used by the capture service while the LLM is still
         * warming up, so notifications are never parked as empty "pending" events.
         */
        fun ruleBased(text: String): ExtractedData? {
            val lower = text.lowercase()

            // 1. Noise filter: needs a task cue, or a time cue on a non-greeting message.
            val hasTaskIndicator = TASK_WORDS.containsMatchIn(lower)
            val hasTimeIndicator = TIME_WORDS.containsMatchIn(lower) ||
                CLOCK.containsMatchIn(lower) || ORDINAL.containsMatchIn(lower)
            if (!hasTaskIndicator && (!hasTimeIndicator || CASUAL.matches(lower))) {
                return ExtractedData(what = "", whenTime = "", who = "", category = "Personal", urgency = "Low", confidence = 0.0)
            }

            // 2. Extract When
            var whenTime = ""
            for (pattern in TIME_PATTERNS) {
                val match = pattern.find(text)
                if (match != null) {
                    whenTime = match.groupValues[1].trim()
                    break
                }
            }

            // 3. Extract Who / Location
            var who = ""
            val locationMatch = Regex("\\b(?:in|at|room|lab)\\s+([a-zA-Z0-9_\\-\\s]{2,15})\\b", RegexOption.IGNORE_CASE).find(text)
            if (locationMatch != null) {
                who = locationMatch.groupValues[0].trim()
            } else if (lower.contains("lab 2") || lower.contains("lab2")) {
                who = "Lab 2"
            }

            // 4. Extract Category & Urgency
            val category = when {
                FIN_WORDS.containsMatchIn(lower) -> "Work"
                ACADEMIC_WORDS.containsMatchIn(lower) -> "Academic"
                WORK_WORDS.containsMatchIn(lower) -> "Work"
                else -> "Personal"
            }

            val urgency = when {
                URGENT_WORDS.containsMatchIn(lower) -> "High"
                SOON_WORDS.containsMatchIn(lower) -> "Medium"
                else -> "Low"
            }

            // 5. Extract What
            val what = when {
                lower.contains("record submission") -> "Record submission"
                FEE_WHAT.containsMatchIn(lower) -> "Fee Payment"
                lower.contains("hackathon") && lower.contains("deck") -> "Submit hackathon deck"
                Regex("\\bclass\\b").containsMatchIn(lower) && (lower.contains("repu") || lower.contains("tomorrow")) -> "Attend Class"
                else -> {
                    var cleaned = text
                    if (whenTime.isNotBlank()) cleaned = cleaned.replace(whenTime, "", ignoreCase = true)
                    if (who.isNotBlank()) cleaned = cleaned.replace(who, "", ignoreCase = true)
                    cleaned = cleaned.replace(Regex("(?i)\\b(please|kindly|remember to|don't forget to|marchipovaddu)\\b"), "")
                        .replace(Regex("\\s+"), " ")
                        .trim(',', '.', ' ', '-', ':')
                    if (cleaned.length > 3) cleaned.take(60).trim() else text.take(60).trim()
                }
            }

            // Blank means "unknown"; callers store null. Never persist sentinel strings.
            return ExtractedData(
                what = what,
                whenTime = whenTime,
                who = who,
                category = category,
                urgency = urgency,
                confidence = if (what.isNotBlank() && whenTime.isNotBlank()) 0.90 else 0.70
            )
        }
    }
}

data class ExtractedData(
    val what: String,
    val whenTime: String,
    val who: String,
    val category: String,
    val urgency: String,
    val confidence: Double
)
