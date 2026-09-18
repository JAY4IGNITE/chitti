package com.owlcoders.chitti

import android.app.Application
import android.app.PendingIntent
import android.util.Log
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.services.ExtractionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ChittiApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }

    // In-memory cache for deep-linking directly back to the exact notification intent.
    // Written from the notification listener's IO coroutines, so it must be thread-safe.
    val replyIntents: MutableMap<Int, PendingIntent> = ConcurrentHashMap()

    // Pre-warm the LLM to avoid cold-start delays during demo.
    // @Volatile: published from an IO thread, read from main/other threads.
    @Volatile
    var extractionEngine: ExtractionEngine? = null
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                extractionEngine = ExtractionEngine(this@ChittiApp)
            } catch (t: Throwable) {
                // ExtractionEngine already degrades to regex mode internally; this is the last
                // line of defence so a warm-up failure can never take the process down.
                Log.e("ChittiApp", "LLM warm-up failed, continuing without LLM: ${t.message}", t)
                extractionEngine = null
            }
        }
    }
}
