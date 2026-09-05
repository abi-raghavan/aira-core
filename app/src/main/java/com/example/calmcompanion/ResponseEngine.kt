package com.example.calmcompanion

class ResponseEngine {
    fun responseFor(
        result: DetectionResult,
        preferredName: String = "",
        caregiverWillBeAlerted: Boolean = false
    ): String {
        val greeting = preferredName.trim().takeIf(String::isNotEmpty)?.let { "$it, " }.orEmpty()
        return when (result.severity) {
            DistressSeverity.CRITICAL ->
                "${greeting}I hear that this may be an emergency. " +
                    alertMessage(caregiverWillBeAlerted) +
                    "If you can, move to a safe place and use the emergency call button now."
            DistressSeverity.HIGH ->
                "${greeting}you are not alone. " +
                    alertMessage(caregiverWillBeAlerted) +
                    "Put both feet down and slowly breathe in, then breathe out."
            DistressSeverity.LOW ->
                "${greeting}I am here with you. Look around and name one thing you can see. " +
                    "Now take one slow breath with me."
            DistressSeverity.NONE ->
                "${greeting}I am listening. Take your time. You can press Help at any moment."
        }
    }

    private fun alertMessage(willAlert: Boolean): String =
        if (willAlert) {
            "I am alerting your configured caregiver. "
        } else {
            "Caregiver alerts are not configured; use the caregiver or emergency call button if needed. "
        }
}
