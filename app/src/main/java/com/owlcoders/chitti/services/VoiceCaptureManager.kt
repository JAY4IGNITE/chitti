package com.owlcoders.chitti.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*

class VoiceCaptureManager(
    private val context: Context,
    private val vadEngine: VadEngine,
    private val sttEngine: SttEngine,
    private val ttsEngine: TtsEngine
) {
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    // 32ms chunk at 16kHz = 512 samples. 16-bit PCM = 2 bytes per sample -> 1024 bytes
    private val CHUNK_SIZE_SAMPLES = 512
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT).coerceAtLeast(CHUNK_SIZE_SAMPLES * 2)

    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Voice state
    private var isSpeechActive = false
    private val audioBuffer = mutableListOf<Float>()

    fun startListening() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("ChittiVoice", "RECORD_AUDIO permission not granted.")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        )

        audioRecord?.startRecording()
        isListening = true

        captureJob = scope.launch {
            val shortBuffer = ShortArray(CHUNK_SIZE_SAMPLES)
            val floatBuffer = FloatArray(CHUNK_SIZE_SAMPLES)

            Log.d("ChittiVoice", "Listening for voice activity...")

            while (isListening) {
                val readResult = audioRecord?.read(shortBuffer, 0, CHUNK_SIZE_SAMPLES) ?: 0
                if (readResult > 0) {
                    // Convert to float [-1.0, 1.0]
                    for (i in 0 until readResult) {
                        floatBuffer[i] = shortBuffer[i] / 32768.0f
                    }
                    
                    // Run VAD
                    val speechProb = vadEngine.processAudioChunk(floatBuffer)
                    val isSpeechNow = speechProb > 0.5f

                    if (isSpeechNow) {
                        if (!isSpeechActive) {
                            Log.d("ChittiVoice", "Speech started!")
                            isSpeechActive = true
                            audioBuffer.clear()
                        }
                        audioBuffer.addAll(floatBuffer.toList())
                    } else {
                        if (isSpeechActive) {
                            Log.d("ChittiVoice", "Speech ended!")
                            isSpeechActive = false
                            
                            // Process STT
                            if (audioBuffer.size > SAMPLE_RATE / 2) { // At least half a second
                                val transcript = sttEngine.transcribe(audioBuffer.toFloatArray())
                                Log.d("ChittiVoice", "Transcript: \$transcript")
                                
                                // TODO: Pass to ExtractionEngine for intent parsing
                                // For now, just echo back
                                ttsEngine.speak("I heard: \$transcript")
                            }
                            audioBuffer.clear()
                        }
                    }
                }
            }
        }
    }

    fun stopListening() {
        isListening = false
        captureJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
