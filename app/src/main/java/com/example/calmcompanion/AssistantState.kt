package com.example.calmcompanion

enum class AssistantState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

enum class DistressSeverity {
    NONE,
    LOW,
    HIGH,
    CRITICAL
}

enum class AlertStatus {
    NONE,
    QUEUED,
    SENT,
    ACKNOWLEDGED,
    FAILED,
    ESCALATED
}

data class DetectionResult(
    val severity: DistressSeverity,
    val matchedPhrases: List<String>,
    val normalizedText: String
)
