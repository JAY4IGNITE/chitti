package com.owlcoders.chitti.services

object NotificationFilter {

    private val keywords = listOf(
        // English
        "submit", "due", "fee", "meeting", "class", "deadline", "tomorrow", "today", "reminder", "asap", "schedule",
        // Hindi (transliterated)
        "kal", "aaj", "bhej", "jama", "karna", "kab",
        // Telugu (transliterated)
        "repu", "ivala", "eeroju", "cheppu", "kattu", "unda"
    )

    // Regex for basic time patterns like 10am, 10:30 PM, 14:00, etc.
    private val timeRegex = Regex("""\b(1[0-2]|0?[1-9])(:[0-5][0-9])?\s*(am|pm|a\.m\.|p\.m\.)\b|\b([01]?[0-9]|2[0-3]):[0-5][0-9]\b""", RegexOption.IGNORE_CASE)
    
    // Regex for basic date patterns like 12/05, Oct 23, Monday
    private val dateRegex = Regex("""\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2}\b|\b\d{1,2}/\d{1,2}(/\d{2,4})?\b|\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE)

    // Precompiled whole-word regex: substring matching caused massive over-triggering
    // ("unda" matched "Sunday"/"under", "kal" matched "kalyan", "due" matched "residue"),
    // sending far more than the intended ~10% of messages to the LLM and wasting battery.
    private val keywordRegex = Regex(
        "\\b(" + keywords.joinToString("|") { Regex.escape(it) } + ")\\b",
        RegexOption.IGNORE_CASE
    )

    /**
     * Checks if a notification text should be processed further by the LLM.
     * Returns true if it contains any of the target keywords or date/time patterns.
     */
    fun shouldProcess(text: String): Boolean {
        if (text.isBlank()) return false
        
        val lowerText = text.lowercase()

        // 1. Check keywords (whole words only)
        if (keywordRegex.containsMatchIn(lowerText)) {
            return true
        }

        // 2. Check time/date regex
        if (timeRegex.containsMatchIn(lowerText) || dateRegex.containsMatchIn(lowerText)) {
            return true
        }

        return false
    }
}
