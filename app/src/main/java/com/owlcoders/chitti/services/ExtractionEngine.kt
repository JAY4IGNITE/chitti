package com.owlcoders.chitti.services

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.owlcoders.chitti.db.CapturedEvent

class ExtractionEngine(private val context: Context, modelPath: String = "/data/local/tmp/gemma.bin") {

    private var llmInference: LlmInference? = null
    private val mutex = Mutex()
    
    init {
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            Log.d("ChittiExtraction", "LLM Initialized successfully from $modelPath")
        } catch (e: Exception) {
            Log.e("ChittiExtraction", "Failed to load LLM model: ${e.message}")
        }
    }

    /**
     * Runs the on-device LLM to extract JSON from the notification text.
     */
    suspend fun extract(text: String): ExtractedData? {
        val llm = llmInference ?: return null
        
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
                val startTime = System.currentTimeMillis()
                val response = llm.generateResponse(prompt)
                val latency = System.currentTimeMillis() - startTime
                Log.d("ChittiExtraction", "LLM Inference Latency: ${latency}ms")
                response
            }
            
            // Clean output to ensure it's JSON (sometimes models add markdown backticks)
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
            Log.e("ChittiExtraction", "Extraction failed: ${e.message}")
            return null
        }
    }

    suspend fun generateSmartReply(event: CapturedEvent): String {
        val llm = llmInference ?: return "Sure, I'll take care of it."
        
        val prompt = """
            You are a smart reply assistant. The user received a message containing a task.
            Draft a short, natural, and polite reply confirming that the user will do the task.
            Only output the reply text, no quotes or explanation.
            
            Task: ${event.extractedWhat}
            Time: ${event.extractedWhen}
            Reply:
        """.trimIndent()
        
        return try {
            mutex.withLock {
                llm.generateResponse(prompt).trim()
            }
        } catch (e: Exception) {
            "Got it!"
        }
    }

    suspend fun generateRagResponse(query: String, contextEvents: List<CapturedEvent>): String {
        val llm = llmInference ?: return "I am unable to answer right now because the AI model is not loaded."
        
        val contextString = contextEvents.joinToString("\n") { 
            "- ${it.extractedWhat} (Due: ${it.extractedWhen}, Category: ${it.category})" 
        }
        
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
        
        return try {
            mutex.withLock {
                llm.generateResponse(prompt).trim()
            }
        } catch (e: Exception) {
            "Sorry, I ran into an error while thinking."
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
