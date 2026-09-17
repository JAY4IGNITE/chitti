package com.owlcoders.chitti

import android.app.Application
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.services.ExtractionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChittiApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    
    // In-memory cache for deep-linking directly back to the exact notification intent
    val replyIntents = mutableMapOf<Int, android.app.PendingIntent>()
    
    // Pre-warm the LLM to avoid cold-start delays during demo
    var extractionEngine: ExtractionEngine? = null
        private set
    
    private val appScope = CoroutineScope(Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            extractionEngine = ExtractionEngine(this@ChittiApp)
        }
    }
}
