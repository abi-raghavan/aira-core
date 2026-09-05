package com.example.calmcompanion

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class ConversationMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val settingsRepository = SettingsRepository.get(context)
    private val alertDao = AiraDatabase.get(context).alerts()
    private val alertCoordinator = AlertCoordinator(context)
    private val serviceIntent = Intent(context, VoiceAssistantService::class.java)
    private var voiceService: VoiceAssistantService? = null
    private var isServiceBound = false
    private var bindRequested = false
    private var cleared = false
    private var pendingAction: PendingAction? = null
    private var fallbackTtsReady = false
    private var pendingFallbackSpeech: String? = null
    private var fallbackTts: TextToSpeech? = null

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    private val _conversationHistory = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationMessage>> = _conversationHistory.asStateFlow()
    private val _currentStatus = MutableStateFlow("Starting up. Speak or touch the circle any time.")
    val currentStatus: StateFlow<String> = _currentStatus.asStateFlow()
    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()
    private val _settings = MutableStateFlow(settingsRepository.load())
    val settings: StateFlow<CaregiverSettings> = _settings.asStateFlow()
    private val _latestAlert = MutableStateFlow<AlertEvent?>(null)
    val latestAlert: StateFlow<AlertEvent?> = _latestAlert.asStateFlow()

    /** Microphone loudness from 0 to 1, drives the listening animation. */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (cleared) {
                runCatching { context.unbindService(this) }
                bindRequested = false
                return
            }
            val binder = service as? VoiceAssistantService.VoiceAssistantBinder ?: return
            voiceService = binder.getService()
            isServiceBound = true
            voiceService?.setCallbacks(
                onStateChanged = {
                    _assistantState.value = it
                    _isServiceRunning.value = it == AssistantState.LISTENING ||
                        it == AssistantState.PROCESSING ||
                        it == AssistantState.SPEAKING
                    _currentStatus.value = statusFor(it)
                },
                onMessageReceived = { addMessage(it, true) },
                onResponseGenerated = { addMessage(it, false) },
                onLevelChanged = { _audioLevel.value = it }
            )
            when (pendingAction) {
                PendingAction.LISTEN -> voiceService?.startListening()
                PendingAction.HELP -> voiceService?.triggerHelp(_hasPermissions.value)
                PendingAction.CRITICAL_DEMO -> voiceService?.simulateCriticalDemo(_hasPermissions.value)
                null -> Unit
            }
            pendingAction = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
            isServiceBound = false
            bindRequested = false
            _isServiceRunning.value = false
            _assistantState.value = AssistantState.ERROR
            _currentStatus.value = "Listening stopped. Touch the circle to restart support."
        }
    }

    init {
        fallbackTts = TextToSpeech(context) { status ->
            fallbackTtsReady = status == TextToSpeech.SUCCESS
            if (fallbackTtsReady) {
                fallbackTts?.language = Locale.US
                fallbackTts?.setSpeechRate(0.82f)
                pendingFallbackSpeech?.let(::speakFallback)
                pendingFallbackSpeech = null
            }
        }
        refreshPermissions()
        if (VoiceAssistantService.isRunning) {
            bindRequested = context.bindService(
                serviceIntent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
        viewModelScope.launch {
            alertDao.observeLatest().collect {
                _latestAlert.value = it
            }
        }
    }

    fun refreshPermissions() {
        val microphoneGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        _hasPermissions.value = microphoneGranted && notificationsGranted
    }

    fun startVoiceAssistant() = connectAndRun(PendingAction.LISTEN)
    fun triggerHelp() = connectAndRun(PendingAction.HELP)
    fun runCriticalDemo() = connectAndRun(PendingAction.CRITICAL_DEMO)

    fun sendTestAlert() {
        if (!_settings.value.isConfigured) {
            _currentStatus.value = "Complete caregiver setup before testing an alert."
            return
        }
        viewModelScope.launch {
            val queued = alertCoordinator.enqueue(
                DistressSeverity.HIGH,
                AlertCoordinator.SOURCE_TEST
            )
            _currentStatus.value = if (queued) {
                "Test alert queued. Check its delivery state below."
            } else {
                "A similar alert was already queued recently."
            }
        }
    }

    private fun connectAndRun(action: PendingAction) {
        if (!refreshAndReturnPermission()) {
            _currentStatus.value =
                "Listening is off until microphone access is allowed. Touch still gives calming support."
            if (action != PendingAction.LISTEN) {
                val response = ResponseEngine().responseFor(
                    DetectionResult(DistressSeverity.HIGH, listOf("help me"), "help me"),
                    _settings.value.patientName,
                    _settings.value.canAutomaticallyAlert
                )
                addMessage(response, false)
                speakFallback(response)
                if (_settings.value.canAutomaticallyAlert) {
                    AiraApplicationScope.scope.launch {
                        alertCoordinator.enqueue(DistressSeverity.HIGH)
                    }
                }
            }
            return
        }
        pendingAction = action
        if (!VoiceAssistantService.isForegroundActive) {
            ContextCompat.startForegroundService(context, serviceIntent)
        }
        if (isServiceBound) {
            when (action) {
                PendingAction.LISTEN -> voiceService?.startListening()
                PendingAction.HELP -> voiceService?.triggerHelp(_hasPermissions.value)
                PendingAction.CRITICAL_DEMO -> voiceService?.simulateCriticalDemo(_hasPermissions.value)
            }
            pendingAction = null
        } else if (!bindRequested) {
            bindRequested = context.bindService(
                serviceIntent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    fun stopVoiceAssistant() {
        stopService()
        _currentStatus.value = "Listening paused. Touch the circle for support."
    }

    fun stopService() {
        pendingAction = null
        voiceService?.shutdownService()
        if (bindRequested) {
            runCatching { context.unbindService(serviceConnection) }
            isServiceBound = false
            bindRequested = false
        }
        context.stopService(serviceIntent)
        voiceService = null
        _isServiceRunning.value = false
        _audioLevel.value = 0f
        _assistantState.value = AssistantState.IDLE
    }

    /**
     * Demo builds must never contact a real caregiver. This reports the delivery the
     * patient would see without opening the messaging app or sending anything.
     */
    fun simulateCaregiverMessage() {
        val name = _settings.value.caregiverName.ifBlank { "your caregiver" }
        addMessage("Message sent to $name. No real message left this device.", false)
        _currentStatus.value = "Message sent to $name (simulated for this demo)."
    }

    fun simulateCaregiverCall() {
        val name = _settings.value.caregiverName.ifBlank { "your caregiver" }
        addMessage("Calling $name now. No real call is placed in this demo.", false)
        _currentStatus.value = "Calling $name (simulated for this demo)."
    }

    fun loadDemoSetup() {
        saveSettings(
            CaregiverSettings(
                patientName = "Sam",
                caregiverName = "Abi",
                caregiverPhone = "+15555550100",
                customTriggers = setOf("I need my person"),
                customCriticalTriggers = setOf("call Abi"),
                consentToAlert = true,
                automaticSms = false
            )
        )
        _currentStatus.value = "Demo caregiver loaded. Tap Simulate critical phrase for the full flow."
    }

    fun saveSettings(settings: CaregiverSettings) {
        settingsRepository.save(settings)
        _settings.value = settingsRepository.load()
        _currentStatus.value = if (_settings.value.isConfigured) {
            "Caregiver setup saved. Use Test alert before relying on it."
        } else {
            "Setup saved. Add a caregiver and consent to enable alerts."
        }
    }

    fun acknowledgeLatestAlert() {
        val alert = _latestAlert.value ?: return
        viewModelScope.launch {
            alertDao.updateStatus(alert.id, AlertStatus.ACKNOWLEDGED.name)
        }
    }

    fun clearConversationHistory() {
        _conversationHistory.value = emptyList()
    }

    private fun addMessage(text: String, isFromUser: Boolean) {
        _conversationHistory.value = (_conversationHistory.value + ConversationMessage(text, isFromUser))
            .takeLast(20)
    }

    private fun refreshAndReturnPermission(): Boolean {
        refreshPermissions()
        return _hasPermissions.value
    }

    private fun speakFallback(text: String) {
        if (!fallbackTtsReady) {
            pendingFallbackSpeech = text
            return
        }
        fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aira_fallback")
    }

    private fun statusFor(state: AssistantState): String = when (state) {
        AssistantState.IDLE -> "Resting. Speak or touch the circle any time."
        AssistantState.LISTENING -> "Listening on this device."
        AssistantState.PROCESSING -> "Choosing a safe response."
        AssistantState.SPEAKING -> "Breathe slowly with AIRA."
        AssistantState.ERROR -> "Voice is unavailable. Touch the circle or call your caregiver."
    }

    override fun onCleared() {
        cleared = true
        if (bindRequested) runCatching { context.unbindService(serviceConnection) }
        bindRequested = false
        isServiceBound = false
        fallbackTts?.shutdown()
        super.onCleared()
    }

    private enum class PendingAction { LISTEN, HELP, CRITICAL_DEMO }
}
