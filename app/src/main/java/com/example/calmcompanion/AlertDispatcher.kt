package com.example.calmcompanion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AlertCoordinator(private val context: Context) {
    private val dao = AiraDatabase.get(context).alerts()

    suspend fun enqueue(severity: DistressSeverity, source: String = SOURCE_LIVE): Boolean {
        dao.deleteOlderThan(System.currentTimeMillis() - RETENTION_MS)
        val timeBucket = System.currentTimeMillis() / DEDUPLICATION_WINDOW_MS
        val eventId = dao.enqueue(
            AlertEvent(
                fingerprint = "$source:${severity.name}:$timeBucket",
                severity = severity.name
            )
        )
        if (eventId <= 0) return false

        val request = OneTimeWorkRequestBuilder<AlertDeliveryWorker>()
            .setInputData(Data.Builder().putLong(AlertDeliveryWorker.KEY_EVENT_ID, eventId).build())
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "alert-$eventId",
            ExistingWorkPolicy.KEEP,
            request
        )
        return true
    }

    companion object {
        const val DEDUPLICATION_WINDOW_MS = 5 * 60 * 1000L
        const val SOURCE_LIVE = "LIVE"
        const val SOURCE_TEST = "TEST"
        private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}

class AlertDeliveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val eventId = inputData.getLong(KEY_EVENT_ID, -1)
        val dao = AiraDatabase.get(applicationContext).alerts()
        val event = if (eventId == -1L) dao.nextPending() else dao.findById(eventId)
        if (event == null || event.status !in setOf(AlertStatus.QUEUED.name, AlertStatus.FAILED.name)) {
            return@withContext Result.success()
        }
        val settings = SettingsRepository.get(applicationContext).load()
        if (!settings.isConfigured) {
            dao.updateStatus(event.id, AlertStatus.FAILED.name, attemptIncrement = 1)
            return@withContext Result.failure()
        }

        val pushDelivered = hasNetwork(applicationContext) && deliverSecurePush(settings, event)
        val delivered = pushDelivered || (settings.automaticSms && deliverSms(settings, event))
        if (delivered) {
            dao.updateStatus(event.id, AlertStatus.SENT.name, attemptIncrement = 1)
            val escalation = OneTimeWorkRequestBuilder<AlertEscalationWorker>()
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setInputData(Data.Builder().putLong(KEY_EVENT_ID, event.id).build())
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "escalate-${event.id}",
                ExistingWorkPolicy.KEEP,
                escalation
            )
            Result.success()
        } else {
            val terminal = runAttemptCount >= 3
            dao.updateStatus(
                event.id,
                if (terminal) AlertStatus.ESCALATED.name else AlertStatus.FAILED.name,
                attemptIncrement = 1
            )
            if (terminal) Result.failure() else Result.retry()
        }
    }

    private fun deliverSecurePush(settings: CaregiverSettings, event: AlertEvent): Boolean {
        if (BuildConfig.DEMO_MODE && BuildConfig.CAREGIVER_ENDPOINT.isBlank()) {
            return settings.caregiverPhone.isNotBlank() && event.id > 0
        }
        return CaregiverGateway().deliver(settings, event)
    }

    private fun deliverSms(settings: CaregiverSettings, event: AlertEvent): Boolean {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val patient = settings.patientName.ifBlank { "The AIRA user" }
        val message = "AIRA support alert: $patient may need help (${event.severity.lowercase()}). " +
            "Please contact them now. AIRA is not an emergency service."
        return runCatching {
            val sms = if (android.os.Build.VERSION.SDK_INT >= 31) {
                applicationContext.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            sms.sendTextMessage(settings.caregiverPhone, null, message, null, null)
        }.isSuccess
    }

    private fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        const val KEY_EVENT_ID = "event_id"
    }
}

object AiraApplicationScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class AlertEscalationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val eventId = inputData.getLong(AlertDeliveryWorker.KEY_EVENT_ID, -1)
        val dao = AiraDatabase.get(applicationContext).alerts()
        val event = dao.findById(eventId) ?: return Result.success()
        if (event.status == AlertStatus.SENT.name) {
            dao.updateStatus(event.id, AlertStatus.ESCALATED.name)
        }
        return Result.success()
    }
}
