package com.owlcoders.chitti.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class TtsEngine(context: Context) : TextToSpeech.OnInitListener {
    private val tag = "ChittiTTS"
    private var tts: TextToSpeech? = null

    var isReady = false
        private set

    /**
     * Compose-observable so screens (mute button, voice overlay) recompose when speech
     * starts/ends. Written from the TTS binder thread; snapshot state handles that safely.
     */
    var isSpeaking by mutableStateOf(false)
        private set

    /** Per-call completion callback (passed to [speak]). */
    private var onSpeechDoneCallback: (() -> Unit)? = null

    /** Global listener invoked on the main thread whenever any utterance finishes or is stopped. */
    var onSpeechFinished: (() -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Id of the utterance we currently care about; callbacks for older (flushed) ones are ignored. */
    @Volatile
    private var currentUtteranceId: String? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(tag, "Language US is not supported, falling back to default locale")
                tts?.setLanguage(Locale.getDefault())
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId == currentUtteranceId) isSpeaking = true
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == currentUtteranceId) finished()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == currentUtteranceId) finished()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == currentUtteranceId) finished()
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId == currentUtteranceId) finished()
                }
            })

            isReady = true
            Log.d(tag, "TTS Engine ready.")
        } else {
            Log.e(tag, "Initialization of TTS failed!")
        }
    }

    private fun finished() {
        isSpeaking = false
        mainHandler.post {
            val cb = onSpeechDoneCallback
            onSpeechDoneCallback = null
            cb?.invoke()
            onSpeechFinished?.invoke()
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        onSpeechDoneCallback = onDone
        if (isReady && tts != null) {
            isSpeaking = true
            // Clean markdown syntax or symbols that sound weird in TTS
            val cleanText = text
                .replace(Regex("[*#_`~•]"), "")
                .replace(Regex("https?://\\S+"), "link")
                .trim()

            val utteranceId = "ChittiResponse_${System.nanoTime()}"
            currentUtteranceId = utteranceId
            val result = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                Log.e(tag, "TTS speak() failed with $result")
                finished()
            } else {
                Log.d(tag, "Speaking: $cleanText")
            }
        } else {
            Log.e(tag, "TTS not ready yet")
            isSpeaking = false
            onSpeechDoneCallback = null
            onDone?.invoke()
        }
    }

    fun stop() {
        currentUtteranceId = null
        if (isReady && tts != null) {
            tts?.stop()
        }
        if (isSpeaking) {
            isSpeaking = false
            mainHandler.post { onSpeechFinished?.invoke() }
        }
        onSpeechDoneCallback = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        isSpeaking = false
        onSpeechDoneCallback = null
        onSpeechFinished = null
    }
}
