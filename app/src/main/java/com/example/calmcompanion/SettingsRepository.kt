package com.example.calmcompanion

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class CaregiverSettings(
    val patientName: String = "",
    val caregiverName: String = "",
    val caregiverPhone: String = "",
    val pairingToken: String = "",
    val customTriggers: Set<String> = setOf("I need my person"),
    val consentToAlert: Boolean = false,
    val automaticSms: Boolean = false
) {
    val isConfigured: Boolean
        get() = caregiverName.isNotBlank() && caregiverPhone.isNotBlank() && consentToAlert

    val canAutomaticallyAlert: Boolean
        get() = isConfigured && (
            automaticSms ||
                (pairingToken.isNotBlank() && BuildConfig.CAREGIVER_ENDPOINT.startsWith("https://")) ||
                BuildConfig.DEMO_MODE
            )
}

class SettingsRepository private constructor(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): CaregiverSettings = CaregiverSettings(
        patientName = preferences.getString(KEY_PATIENT, "").orEmpty(),
        caregiverName = preferences.getString(KEY_CAREGIVER, "").orEmpty(),
        caregiverPhone = preferences.getString(KEY_PHONE, "").orEmpty(),
        pairingToken = preferences.getString(KEY_PAIRING_TOKEN, "").orEmpty(),
        customTriggers = preferences.getStringSet(KEY_TRIGGERS, setOf("I need my person"))
            ?.toSet()
            .orEmpty(),
        consentToAlert = preferences.getBoolean(KEY_CONSENT, false),
        automaticSms = preferences.getBoolean(KEY_AUTO_SMS, false)
    )

    fun save(settings: CaregiverSettings) {
        preferences.edit()
            .putString(KEY_PATIENT, settings.patientName.trim())
            .putString(KEY_CAREGIVER, settings.caregiverName.trim())
            .putString(KEY_PHONE, settings.caregiverPhone.filter { it.isDigit() || it == '+' })
            .putString(KEY_PAIRING_TOKEN, settings.pairingToken.trim())
            .putStringSet(KEY_TRIGGERS, settings.customTriggers.map(String::trim).filter(String::isNotBlank).toSet())
            .putBoolean(KEY_CONSENT, settings.consentToAlert)
            .putBoolean(KEY_AUTO_SMS, settings.automaticSms)
            .apply()
    }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository = instance ?: synchronized(this) {
            instance ?: SettingsRepository(context.applicationContext).also { instance = it }
        }

        const val FILE_NAME = "aira_private_settings"
        const val KEY_PATIENT = "patient"
        const val KEY_CAREGIVER = "caregiver"
        const val KEY_PHONE = "phone"
        const val KEY_PAIRING_TOKEN = "pairing_token"
        const val KEY_TRIGGERS = "triggers"
        const val KEY_CONSENT = "consent"
        const val KEY_AUTO_SMS = "auto_sms"
    }
}
