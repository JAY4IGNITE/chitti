package com.owlcoders.chitti.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsEngine(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("ChittiTTS", "The Language specified is not supported!")
            } else {
                isReady = true
                Log.d("ChittiTTS", "TTS Engine ready.")
            }
        } else {
            Log.e("ChittiTTS", "Initialization of TTS failed!")
        }
    }

    fun speak(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ChittiResponse")
            Log.d("ChittiTTS", "Speaking: $text")
        } else {
            Log.e("ChittiTTS", "TTS not ready yet")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
