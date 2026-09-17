package com.owlcoders.chitti.services

import java.security.MessageDigest

/**
 * Deduplication engine per plan.md §2.2 step 7.
 * Checks recent events (last 5 min) by source + hash of task+time.
 */
object DeduplicationEngine {
    
    /**
     * Generate a hash for a notification based on source package and text content.
     * Used to detect duplicate notifications within a time window.
     */
    fun generateHash(packageName: String, text: String): String {
        // Normalize text: lowercase, trim, remove extra whitespace
        val normalized = text.lowercase().trim().replace(Regex("\\s+"), " ")
        val input = "$packageName|$normalized"
        
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }.take(16) // short hash
        } catch (e: Exception) {
            // Fallback to simple hashCode
            input.hashCode().toString()
        }
    }
    
    /**
     * Time window for deduplication (5 minutes in milliseconds).
     */
    const val DEDUP_WINDOW_MS = 5 * 60 * 1000L
    
    /**
     * Returns the cutoff timestamp for dedup checks.
     */
    fun getDeduplicationCutoff(): Long {
        return System.currentTimeMillis() - DEDUP_WINDOW_MS
    }
}
