package com.owlcoders.chitti.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsEngine(context: Context) : TextToSpeech.OnInitListener {
    private val tag = "ChittiTTS"
    private var tts: TextToSpeech? = null
    var isReady = false
        private set
    var isSpeaking = false
        private set

    private var onSpeechDoneCallback: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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
                    isSpeaking = true
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    mainHandler.post {
                        onSpeechDoneCallback?.invoke()
                    }
                }

                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    mainHandler.post {
                        onSpeechDoneCallback?.invoke()
                    }
                }
            })

            isReady = true
            Log.d(tag, "TTS Engine ready.")
        } else {
            Log.e(tag, "Initialization of TTS failed!")
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        onSpeechDoneCallback = onDone
        if (isReady && tts != null) {
            isSpeaking = true
            // Clean markdown syntax or symbols that sound weird in TTS
            val cleanText = text
                .replace(Regex("[*#_`~]"), "")
                .replace(Regex("https?://\\S+"), "link")
                .trim()
            
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "ChittiResponse_${System.currentTimeMillis()}")
            Log.d(tag, "Speaking: $cleanText")
        } else {
            Log.e(tag, "TTS not ready yet")
            onDone?.invoke()
        }
    }

    fun stop() {
        if (isReady && tts != null) {
            tts?.stop()
            isSpeaking = false
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        isSpeaking = false
    }
}
