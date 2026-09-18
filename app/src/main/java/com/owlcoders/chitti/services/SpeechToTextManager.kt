package com.owlcoders.chitti.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * High-performance Speech-to-Text Manager with low-latency silence detection
 * and instant partial trigger for immediate task execution.
 */
class SpeechToTextManager(
    private val context: Context,
    private val onPartialResult: (String) -> Unit = {},
    private val onFinalResult: (String) -> Unit = {},
    private val onRmsChanged: (Float) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onStateChange: (SpeechState) -> Unit = {}
) {
    private val tag = "ChittiSTT"
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var hasTriggeredFinal = false

    enum class SpeechState {
        IDLE, READY, LISTENING, PROCESSING, ERROR
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(tag, "onReadyForSpeech")
            isListening = true
            hasTriggeredFinal = false
            onStateChange(SpeechState.READY)
        }

        override fun onBeginningOfSpeech() {
            Log.d(tag, "onBeginningOfSpeech")
            onStateChange(SpeechState.LISTENING)
        }

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
            onRmsChanged(normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(tag, "onEndOfSpeech")
            onStateChange(SpeechState.PROCESSING)
        }

        override fun onError(error: Int) {
            if (hasTriggeredFinal) return
            isListening = false
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap mic to speak."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap mic to try again."
                SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network issue for voice recognition."
                SpeechRecognizer.ERROR_CLIENT -> "Voice client busy."
                else -> "Speech recognition error ($error)"
            }
            Log.w(tag, "Speech recognition error: $message (code $error)")
            onStateChange(SpeechState.ERROR)
            onError(message)
        }

        override fun onResults(results: Bundle?) {
            if (hasTriggeredFinal) return
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim() ?: ""
            Log.d(tag, "Final recognized text: $recognizedText")
            onStateChange(SpeechState.IDLE)
            if (recognizedText.isNotBlank()) {
                hasTriggeredFinal = true
                onFinalResult(recognizedText)
            } else {
                onError("Could not hear anything clearly.")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (hasTriggeredFinal) return
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = matches?.firstOrNull()?.trim() ?: ""
            if (partialText.isNotBlank()) {
                Log.d(tag, "Partial text: $partialText")
                onPartialResult(partialText)

                // Ultra-fast path: If an unambiguous app command is spoken, trigger immediately without waiting for silence!
                val lower = partialText.lowercase()
                val isFastAction = (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ")) &&
                        (lower.contains("whatsapp") || lower.contains("youtube") || lower.contains("camera") ||
                                lower.contains("settings") || lower.contains("chrome") || lower.contains("spotify") ||
                                lower.contains("instagram") || lower.contains("telegram") || lower.contains("maps"))

                val isFastControl = lower.contains("turn on flashlight") || lower.contains("turn off flashlight") ||
                        lower.contains("torch on") || lower.contains("torch off")

                if ((isFastAction || isFastControl) && isListening) {
                    Log.d(tag, "Instant fast-action triggered on partial transcript: $partialText")
                    hasTriggeredFinal = true
                    stopListening()
                    onFinalResult(partialText)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    onError("Speech recognition is not available on this device.")
                    return@post
                }

                stopListening()
                hasTriggeredFinal = false

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
                    // Note: Google Speech Engine requires Integer values, not Long!
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
                }

                speechRecognizer?.startListening(intent)
                Log.d(tag, "Started fast-response speech listening")
            } catch (e: Exception) {
                Log.e(tag, "Failed to start listening: ${e.message}", e)
                onError("Failed to start microphone: ${e.message}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer?.stopListening()
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                    isListening = false
                    Log.d(tag, "Stopped speech listening")
                }
            } catch (e: Exception) {
                Log.w(tag, "Error destroying speech recognizer: ${e.message}")
            }
        }
    }

    fun destroy() {
        stopListening()
    }
}
