package com.owlcoders.chitti.services

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer

class VadEngine(private val context: Context) {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    // Silero VAD v5 constants
    private val SAMPLE_RATE = 16000L
    private val WINDOW_SIZE_SAMPLES = 512 // 32ms at 16kHz
    
    // Model state
    private var h = Array(2) { FloatArray(64) }
    private var c = Array(2) { FloatArray(64) }

    init {
        try {
            env = OrtEnvironment.getEnvironment()
            // IMPORTANT: The user must place 'silero_vad.onnx' in the assets folder.
            val modelBytes = context.assets.open("silero_vad.onnx").readBytes()
            session = env?.createSession(modelBytes, OrtSession.SessionOptions())
            Log.d("ChittiVAD", "Silero VAD ONNX model loaded successfully.")
        } catch (e: Exception) {
            Log.e("ChittiVAD", "Failed to load VAD model. Make sure silero_vad.onnx is in assets: ${e.message}")
        }
    }

    /**
     * Feeds 32ms of audio to the model and returns the probability of speech (0.0 to 1.0)
     */
    fun processAudioChunk(audioData: FloatArray): Float {
        val ortEnv = env ?: return 0f
        val ortSession = session ?: return 0f

        try {
            val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(audioData), longArrayOf(1, audioData.size.toLong()))
            val srTensor = OnnxTensor.createTensor(ortEnv, LongArray(1) { SAMPLE_RATE })
            
            // Flatten state tensors
            val hFlat = FloatArray(128)
            val cFlat = FloatArray(128)
            System.arraycopy(h[0], 0, hFlat, 0, 64)
            System.arraycopy(h[1], 0, hFlat, 64, 64)
            System.arraycopy(c[0], 0, cFlat, 0, 64)
            System.arraycopy(c[1], 0, cFlat, 64, 64)
            
            val hTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(hFlat), longArrayOf(2, 1, 64))
            val cTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(cFlat), longArrayOf(2, 1, 64))

            val inputs = mapOf(
                "input" to inputTensor,
                "sr" to srTensor,
                "h" to hTensor,
                "c" to cTensor
            )

            val result = ortSession.run(inputs)
            
            // Extract probability
            val output = result[0].value as Array<FloatArray>
            val speechProb = output[0][0]
            
            // Update state
            val hn = result[1].value as Array<Array<FloatArray>>
            val cn = result[2].value as Array<Array<FloatArray>>
            
            h[0] = hn[0][0]
            h[1] = hn[1][0]
            c[0] = cn[0][0]
            c[1] = cn[1][0]
            
            inputTensor.close()
            srTensor.close()
            hTensor.close()
            cTensor.close()
            result.close()

            return speechProb
        } catch (e: Exception) {
            Log.e("ChittiVAD", "Error processing audio chunk: ${e.message}")
            return 0f
        }
    }
}
