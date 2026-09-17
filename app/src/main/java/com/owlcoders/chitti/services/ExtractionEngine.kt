package com.owlcoders.chitti.services

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.owlcoders.chitti.db.CapturedEvent

/**
 * Intelligent Extraction Engine for Chitti.
 * Uses on-device Gemma LLM via MediaPipe GenAI when available,
 * with a high-accuracy, low-latency regex/rule-based NLP fallback
 * per plan.md §2.2, §7, and §14.
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
                .setMaxTokens(1024)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            lastInferenceMode = "Gemma 2B (On-Device)"
            Log.d("ChittiExtraction", "LLM Initialized successfully from $modelPath")
        } catch (e: Exception) {
            Log.i("ChittiExtraction", "LLM file not found or unavailable, operating in fast rule-based mode: ${e.message}")
            lastInferenceMode = "Rule-based Regex"
        }
    }

    fun isLlmLoaded(): Boolean = llmInference != null

    /**
     * Extracts commitments, deadlines, or meetings from raw text.
     * Uses Gemma LLM if loaded; falls back seamlessly to rule-based NER.
     */
    suspend fun extract(text: String): ExtractedData? {
        val startTime = System.currentTimeMillis()
        val llm = llmInference

        if (llm != null) {
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
                
                Message: "$text"
                Output: 
            """.trimIndent()

            try {
                val rawOutput = mutex.withLock {
                    val response = llm.generateResponse(prompt)
                    response
                }

                val latency = System.currentTimeMillis() - startTime
                lastInferenceLatencyMs = latency
                lastInferenceMode = "Gemma 2B (On-Device)"
                Log.d("ChittiExtraction", "LLM Inference Latency: ${latency}ms")

                val cleanOutput = rawOutput.replace("```json", "").replace("```", "").trim()
                val json = JSONObject(cleanOutput)

                return ExtractedData(
                    what = json.optString("what", ""),
                    whenTime = json.optString("when", ""),
                    who = json.optString("who", ""),
                    category = json.optString("category", "Personal"),
                    urgency = json.optString("urgency", "Medium"),
                    confidence = json.optDouble("confidence", 0.0)
                )
            } catch (e: Exception) {
                Log.w("ChittiExtraction", "LLM extraction error, using rule-based fallback: ${e.message}")
            }
        }

        // Rule-based / Regex extraction fallback
        val result = ruleBasedExtract(text)
        val latency = System.currentTimeMillis() - startTime
        lastInferenceLatencyMs = latency
        lastInferenceMode = "Rule-based Regex"
        Log.d("ChittiExtraction", "Rule-based Extraction Latency: ${latency}ms, Result: ${result?.what}")
        return result
    }

    /**
     * Fast, lightweight, offline rule-based entity extractor per plan.md §2.2 & §7.
     * Handles English, Telugu-English code-mix, and Hinglish.
     */
    fun ruleBasedExtract(text: String): ExtractedData? {
        val lower = text.lowercase()

        // 1. Noise Filter - ignore casual chat/greetings with no task cues
        val isCasual = lower.matches(Regex("^(hi|hello|hey|yo|sup|good morning|good evening|good night|how are you|kya haal|wassup|ok|okay|cool|hmm|thanks|thank you)\\b.*"))
        val hasTaskIndicator = lower.contains("submit") || lower.contains("submission") ||
                lower.contains("deck") || lower.contains("hackathon") || lower.contains("class") ||
                lower.contains("fee") || lower.contains("pay") || lower.contains("kattali") ||
                lower.contains("meeting") || lower.contains("meet") || lower.contains("call") ||
                lower.contains("review") || lower.contains("assignment") || lower.contains("exam") ||
                lower.contains("due") || lower.contains("deadline") || lower.contains("remind") ||
                lower.contains("lab") || lower.contains("record") || lower.contains("project") ||
                lower.contains("presentation") || lower.contains("standup") || lower.contains("schedule")

        val hasTimeIndicator = lower.contains("tomorrow") || lower.contains("today") ||
                lower.contains("repu") || lower.contains("am") || lower.contains("pm") ||
                lower.contains("lopu") || lower.contains("ki") || lower.contains("by ") ||
                lower.contains("at ") || Regex("\\b\\d{1,2}(?::\\d{2})?\\s*(am|pm)\\b").containsMatchIn(lower) ||
                Regex("\\b\\d{1,2}(st|nd|rd|th)\\b").containsMatchIn(lower)

        if (!hasTaskIndicator && !hasTimeIndicator && isCasual) {
            return ExtractedData(what = "", whenTime = "", who = "", category = "Personal", urgency = "Low", confidence = 0.0)
        }

        // 2. Extract When
        var whenTime = ""
        val timePatterns = listOf(
            Regex("\\b(tomorrow\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(today\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(repu\\s*\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|ki)?)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(\\d{1,2}(?:st|nd|rd|th)?\\s+lopu)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(by\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?\\s*(?:tomorrow|today)?)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(at\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm))\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(tomorrow|today|repu)\\b", RegexOption.IGNORE_CASE)
        )
        for (pattern in timePatterns) {
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
            lower.contains("fee") || lower.contains("kattali") || lower.contains("pay") || lower.contains("upi") || lower.contains("rs") || lower.contains("bill") -> "Work"
            lower.contains("deck") || lower.contains("hackathon") || lower.contains("class") || lower.contains("lab") || lower.contains("assignment") || lower.contains("exam") || lower.contains("record") -> "Academic"
            lower.contains("meeting") || lower.contains("standup") || lower.contains("sync") || lower.contains("client") || lower.contains("office") -> "Work"
            else -> "Personal"
        }

        val urgency = when {
            lower.contains("urgent") || lower.contains("asap") || lower.contains("marchipovaddu") || lower.contains("immediately") || lower.contains("today") -> "High"
            lower.contains("tomorrow") || lower.contains("repu") || lower.contains("by") -> "Medium"
            else -> "Low"
        }

        // 5. Extract What
        val what = when {
            lower.contains("record submission") -> "Record submission"
            lower.contains("fee kattali") || lower.contains("pay fee") || lower.contains("fee") -> "College Fee Payment"
            lower.contains("hackathon") && lower.contains("deck") -> "Submit hackathon deck"
            lower.contains("class") && (lower.contains("repu") || lower.contains("tomorrow")) -> "Attend Class"
            else -> {
                var cleaned = text
                if (whenTime.isNotBlank()) cleaned = cleaned.replace(whenTime, "", ignoreCase = true)
                if (who.isNotBlank()) cleaned = cleaned.replace(who, "", ignoreCase = true)
                cleaned = cleaned.replace(Regex("(?i)\\b(please|kindly|remember to|don't forget to|marchipovaddu)\\b"), "")
                    .trim(',', '.', ' ', '-', ':')
                if (cleaned.length > 3) cleaned.take(60).trim() else text.take(60)
            }
        }

        return ExtractedData(
            what = what,
            whenTime = if (whenTime.isNotBlank()) whenTime else "Pending",
            who = if (who.isNotBlank()) who else "Self",
            category = category,
            urgency = urgency,
            confidence = if (what.isNotBlank() && whenTime.isNotBlank()) 0.90 else 0.70
        )
    }

    suspend fun generateSmartReply(event: CapturedEvent): String {
        val llm = llmInference
        if (llm != null) {
            val prompt = """
                You are a smart reply assistant. The user received a message containing a task.
                Draft a short, natural, and polite reply confirming that the user will do the task.
                Only output the reply text, no quotes or explanation.
                
                Task: ${event.extractedWhat}
                Time: ${event.extractedWhen}
                Reply:
            """.trimIndent()

            try {
                return mutex.withLock {
                    llm.generateResponse(prompt).trim()
                }
            } catch (e: Exception) {
                Log.w("ChittiExtraction", "LLM smart reply failed: ${e.message}")
            }
        }

        // Fallback smart reply
        val task = event.extractedWhat ?: "task"
        val time = if (!event.extractedWhen.isNullOrBlank()) " by ${event.extractedWhen}" else ""
        return "Got it! I will take care of \"$task\"$time."
    }

    suspend fun generateRagResponse(query: String, contextEvents: List<CapturedEvent>): String {
        val llm = llmInference
        val contextString = contextEvents.joinToString("\n") { 
            "- ${it.extractedWhat} (Due: ${it.extractedWhen}, Category: ${it.category})" 
        }

        if (llm != null) {
            val prompt = """
                You are Chitti, a helpful personal assistant memory bot.
                Answer the user's question based ONLY on the provided context of their tasks. 
                If the answer is not in the context, say "I don't have that in my memory."
                Keep it conversational but concise.
                
                Context (User's Tasks):
                $contextString
                
                Question: $query
                Answer:
            """.trimIndent()

            try {
                return mutex.withLock {
                    llm.generateResponse(prompt).trim()
                }
            } catch (e: Exception) {
                Log.w("ChittiExtraction", "LLM RAG response failed: ${e.message}")
            }
        }

        // Fallback offline keyword matching RAG
        val queryLower = query.lowercase()
        val matching = contextEvents.filter {
            it.extractedWhat?.lowercase()?.contains(queryLower) == true ||
            it.category?.lowercase()?.contains(queryLower) == true ||
            it.rawText.lowercase().contains(queryLower)
        }

        if (matching.isNotEmpty()) {
            return "Found in memory:\n" + matching.joinToString("\n") {
                "• ${it.extractedWhat} (Due: ${it.extractedWhen ?: "N/A"}, Category: ${it.category})"
            }
        }

        if (contextEvents.isNotEmpty()) {
            return "Here are your active commitments:\n" + contextEvents.take(5).joinToString("\n") {
                "• ${it.extractedWhat} (Due: ${it.extractedWhen ?: "N/A"}, Category: ${it.category})"
            }
        }

        return "I don't have that in my memory yet."
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
