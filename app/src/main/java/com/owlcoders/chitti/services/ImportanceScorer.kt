package com.owlcoders.chitti.services

/**
 * Importance scoring per plan.md §2.2 step 6.
 * Weighted sum of: deadline proximity, financial impact, urgency keywords.
 */
object ImportanceScorer {
    
    /**
     * Scores the importance of extracted data on a scale of 0.0 to 1.0.
     * Higher score = more important / more urgent.
     */
    fun score(
        extractedWhat: String?,
        extractedWhen: String?,
        urgency: String?,
        category: String?,
        rawText: String
    ): Float {
        var score = 0f
        val lowerText = rawText.lowercase()
        
        // 1. Urgency weight (0.3)
        score += when (urgency?.lowercase()) {
            "high" -> 0.3f
            "medium" -> 0.15f
            "low" -> 0.05f
            else -> 0.1f
        }
        
        // 2. Financial keywords (0.25)
        val financialKeywords = listOf("fee", "payment", "pay", "amount", "rupees", "rs", "₹", "due", "fine", "invoice", "bill")
        if (financialKeywords.any { lowerText.contains(it) }) {
            score += 0.25f
        }
        
        // 3. Time proximity keywords (0.25)
        val urgentTimeKeywords = listOf("now", "immediately", "asap", "urgent", "today", "tonight", "this hour")
        val soonKeywords = listOf("tomorrow", "tonight", "morning", "evening", "kal", "repu")
        
        if (urgentTimeKeywords.any { lowerText.contains(it) }) {
            score += 0.25f
        } else if (soonKeywords.any { lowerText.contains(it) }) {
            score += 0.15f
        } else if (extractedWhen?.isNotBlank() == true) {
            score += 0.1f
        }
        
        // 4. Category weight (0.1)
        score += when (category?.lowercase()) {
            "work" -> 0.1f
            "academic" -> 0.08f
            "personal" -> 0.05f
            else -> 0.05f
        }
        
        // 5. Has actionable task (0.1)
        if (extractedWhat?.isNotBlank() == true) {
            score += 0.1f
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Convert importance score to priority level.
     * 0 = low, 1 = medium, 2 = high
     */
    fun toPriority(score: Float): Int {
        return when {
            score >= 0.6f -> 2 // high
            score >= 0.3f -> 1 // medium
            else -> 0 // low
        }
    }
}
