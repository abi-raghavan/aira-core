package com.example.calmcompanion

import java.util.Locale

class DistressDetector {
    private val criticalPhrases = setOf(
        "call emergency", "cannot breathe", "cant breathe", "heart attack",
        "hurt myself", "kill myself", "suicide", "medical emergency"
    )
    private val highPhrases = setOf(
        "help me", "panic attack", "not safe", "terrified", "very scared",
        "losing control", "need someone"
    )
    private val lowPhrases = setOf(
        "anxious", "anxiety", "overwhelmed", "stressed", "upset", "sad", "afraid"
    )

    fun detect(
        text: String,
        customTriggers: Set<String> = emptySet(),
        repeatedWithinWindow: Boolean = false
    ): DetectionResult {
        val normalized = normalize(text)
        val custom = customTriggers.map(::normalize).filter(String::isNotBlank).toSet()
        val critical = matches(normalized, criticalPhrases)
        val high = matches(normalized, highPhrases)
        val low = matches(normalized, lowPhrases)
        val customMatches = matches(normalized, custom)
        val matches = (critical + high + low + customMatches).distinct()

        val severity = when {
            critical.isNotEmpty() -> DistressSeverity.CRITICAL
            high.isNotEmpty() || customMatches.isNotEmpty() || (low.isNotEmpty() && repeatedWithinWindow) ->
                DistressSeverity.HIGH
            low.isNotEmpty() -> DistressSeverity.LOW
            else -> DistressSeverity.NONE
        }
        return DetectionResult(severity, matches, normalized)
    }

    fun normalize(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9']+"), " ")
            .replace("can't", "cant")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun matches(text: String, phrases: Set<String>): List<String> =
        phrases.filter { phrase ->
            val escaped = Regex.escape(phrase)
            Regex("(^|\\s)$escaped($|\\s)").containsMatchIn(text)
        }
}
