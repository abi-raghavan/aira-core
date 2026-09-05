package com.example.calmcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistressDetectorTest {
    private val detector = DistressDetector()

    @Test
    fun criticalPhraseEscalatesImmediately() {
        val result = detector.detect("Aira, I cannot breathe!")

        assertEquals(DistressSeverity.CRITICAL, result.severity)
        assertTrue("cannot breathe" in result.matchedPhrases)
    }

    @Test
    fun customTriggerIsHighSeverity() {
        val result = detector.detect(
            text = "Please, red balloon now",
            customTriggers = setOf("red balloon")
        )

        assertEquals(DistressSeverity.HIGH, result.severity)
        assertEquals(listOf("red balloon"), result.matchedPhrases)
    }

    @Test
    fun caregiverCanConfigureCriticalPhrase() {
        val result = detector.detect(
            text = "Please call Abi now",
            customCriticalTriggers = setOf("call abi")
        )

        assertEquals(DistressSeverity.CRITICAL, result.severity)
        assertEquals(listOf("call abi"), result.matchedPhrases)
    }

    @Test
    fun repeatedLowDistressEscalates() {
        val first = detector.detect("I feel anxious")
        val repeated = detector.detect("Still anxious", repeatedWithinWindow = true)

        assertEquals(DistressSeverity.LOW, first.severity)
        assertEquals(DistressSeverity.HIGH, repeated.severity)
    }

    @Test
    fun matchingUsesWholePhrases() {
        val result = detector.detect("The helper adjusted the campaign.")

        assertEquals(DistressSeverity.NONE, result.severity)
    }

    @Test
    fun responseDoesNotDependOnNetworkOrRandomness() {
        val result = detector.detect("help me")
        val first = ResponseEngine().responseFor(result, "Sam", caregiverWillBeAlerted = true)
        val second = ResponseEngine().responseFor(result, "Sam", caregiverWillBeAlerted = true)

        assertEquals(first, second)
        assertTrue(first.startsWith("Sam,"))
        assertTrue(first.contains("alerting your configured caregiver"))
    }

    @Test
    fun responseDoesNotClaimAlertWhenCaregiverIsNotConfigured() {
        val response = ResponseEngine().responseFor(detector.detect("help me"))

        assertTrue(response.contains("Caregiver alerts are not configured"))
        assertTrue(!response.contains("I am alerting your configured caregiver"))
    }
}
