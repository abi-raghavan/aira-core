package com.example.calmcompanion

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class CaregiverGateway {
    fun deliver(settings: CaregiverSettings, event: AlertEvent): Boolean {
        val endpoint = BuildConfig.CAREGIVER_ENDPOINT.trim()
        if (!endpoint.startsWith("https://") || settings.pairingToken.isBlank()) return false

        return runCatching {
            val connection = URL(endpoint).openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${settings.pairingToken}")
                connection.setRequestProperty("Idempotency-Key", event.fingerprint)
                val body = JSONObject()
                    .put("eventId", event.id)
                    .put("severity", event.severity)
                    .put("occurredAt", event.createdAt)
                    .put("source", "aira-android-pilot")
                    .toString()
                connection.outputStream.bufferedWriter().use { it.write(body) }
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }
}
