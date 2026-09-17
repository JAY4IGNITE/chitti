package com.owlcoders.chitti.services

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
            
            Schema:
            { "what": "...", "when": "...", "who": "...", "confidence": 0.0-1.0 }
            
            Example 1:
            Message: "Submit the hackathon deck by 10am tomorrow"
            Output: { "what": "Submit hackathon deck", "when": "tomorrow 10am", "who": "me", "confidence": 0.95 }
            
            Example 2 (Code-mixed):
            Message: "repu class unda? 9 ki?"
            Output: { "what": "class", "when": "tomorrow 9:00", "who": "unknown", "confidence": 0.8 }
            
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
                confidence = json.optDouble("confidence", 0.0)
            )
        } catch (e: Exception) {
            Log.e("ChittiExtraction", "Extraction failed: ${e.message}")
            return null
        }
    }
}

data class ExtractedData(
    val what: String,
    val whenTime: String,
    val who: String,
    val confidence: Double
)
