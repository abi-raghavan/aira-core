package com.example.calmcompanion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceAssistantService : LifecycleService() {
    inner class VoiceAssistantBinder : Binder() {
        fun getService(): VoiceAssistantService = this@VoiceAssistantService
    }

    private val binder = VoiceAssistantBinder()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var speechEngine: SpeechEngine
    private val detector = DistressDetector()
    private val responseEngine = ResponseEngine()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var alertCoordinator: AlertCoordinator
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var listeningActive = false
    private var currentState = AssistantState.IDLE
    private var lastDistressAt = 0L
    private var audioFocusRequest: AudioFocusRequest? = null
    private var onStateChanged: (AssistantState) -> Unit = {}
    private var onMessageReceived: (String) -> Unit = {}
    private var onResponseGenerated: (String) -> Unit = {}

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        speechEngine = AndroidOfflineSpeechEngine(this)
        settingsRepository = SettingsRepository.get(this)
        alertCoordinator = AlertCoordinator(this)
        createNotificationChannel()
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.82f)
                tts?.setPitch(0.94f)
                installTtsListener()
                pendingSpeech?.let {
                    pendingSpeech = null
                    speak(it)
                }
            } else {
                pendingSpeech = null
                updateState(AssistantState.ERROR)
                if (!listeningActive) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    isForegroundActive = false
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Ready — tap Help any time"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
        )
        isForegroundActive = true
        when (intent?.action) {
            ACTION_STOP -> shutdownService()
            ACTION_HELP -> triggerHelp()
            else -> if (intent?.getBooleanExtra(EXTRA_AUTO_START, false) == true) startListening()
        }
        return Service.START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    fun setCallbacks(
        onStateChanged: (AssistantState) -> Unit,
        onMessageReceived: (String) -> Unit,
        onResponseGenerated: (String) -> Unit
    ) {
        this.onStateChanged = onStateChanged
        this.onMessageReceived = onMessageReceived
        this.onResponseGenerated = onResponseGenerated
        onStateChanged(currentState)
    }

    fun startListening() {
        listeningActive = true
        requestAudioFocus()
        beginRecognition()
    }

    fun stopListening() {
        listeningActive = false
        handler.removeCallbacksAndMessages(null)
        speechEngine.stop()
        tts?.stop()
        abandonAudioFocus()
        updateState(AssistantState.IDLE)
        updateNotification("Paused — open AIRA to restart")
    }

    fun shutdownService() {
        stopListening()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isForegroundActive = false
        stopSelf()
    }

    fun pauseListening() = stopListening()
    fun resumeListening() = startListening()

    /**
     * Immediate support from a touch. [keepListening] is true when the microphone is
     * available, so AIRA returns to its resting listening state after speaking.
     */
    fun triggerHelp(keepListening: Boolean = listeningActive) {
        requestAudioFocus()
        processText("help me", keepListening)
    }

    fun simulateCriticalDemo(keepListening: Boolean = listeningActive) {
        requestAudioFocus()
        processText("I cannot breathe medical emergency", keepListening)
    }

    private fun beginRecognition() {
        if (!listeningActive) return
        updateState(AssistantState.LISTENING)
        updateNotification("Listening privately on this device")
        speechEngine.start(
            onText = { if (listeningActive) processText(it, resumeListening = true) },
            onError = { message ->
                if (!listeningActive) return@start
                when {
                    message == "Listening…" -> scheduleRecognition()
                    // Nothing will improve by retrying: no speech service, no language
                    // pack, or no microphone permission. Stay reachable by touch.
                    message.contains("installed", ignoreCase = true) ||
                        message.contains("Microphone access", ignoreCase = true) -> {
                        listeningActive = false
                        updateState(AssistantState.ERROR)
                        updateNotification(message)
                    }
                    else -> {
                    updateState(AssistantState.ERROR)
                    updateNotification(message)
                    scheduleRecognition(2_000)
                    }
                }
            }
        )
    }

    private fun processText(text: String, resumeListening: Boolean = listeningActive) {
        listeningActive = resumeListening
        speechEngine.stop()
        updateState(AssistantState.PROCESSING)
        onMessageReceived(text)

        val now = System.currentTimeMillis()
        val settings = settingsRepository.load()
        val result = detector.detect(
            text = text,
            customTriggers = settings.customTriggers,
            customCriticalTriggers = settings.customCriticalTriggers,
            repeatedWithinWindow = now - lastDistressAt < REPEAT_WINDOW_MS
        )
        if (result.severity != DistressSeverity.NONE) lastDistressAt = now
        val response = responseEngine.responseFor(
            result,
            settings.patientName,
            settings.canAutomaticallyAlert
        )
        onResponseGenerated(response)

        if (result.severity >= DistressSeverity.HIGH && settings.canAutomaticallyAlert) {
            AiraApplicationScope.scope.launch { alertCoordinator.enqueue(result.severity) }
        }
        speak(response)
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            pendingSpeech = text
            updateNotification("Preparing spoken guidance")
            return
        }
        updateState(AssistantState.SPEAKING)
        updateNotification("Guiding a calming exercise")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun installTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onError(utteranceId: String?) {
                handler.post {
                    updateState(AssistantState.ERROR)
                    finishGuidance()
                }
            }
            override fun onDone(utteranceId: String?) {
                handler.post { finishGuidance() }
            }
        })
    }

    private fun finishGuidance() {
        if (listeningActive) {
            scheduleRecognition(600)
        } else {
            abandonAudioFocus()
            updateState(AssistantState.IDLE)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForegroundActive = false
            stopSelf()
        }
    }

    private fun scheduleRecognition(delayMs: Long = 800) {
        handler.removeCallbacksAndMessages(null)
        if (listeningActive) handler.postDelayed(::beginRecognition, delayMs)
    }

    private fun updateState(state: AssistantState) {
        currentState = state
        handler.post { onStateChanged(state) }
    }

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return
        val manager = getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        speechEngine.stop()
                        updateNotification("Paused for other audio")
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> scheduleRecognition(500)
                }
            }
            .build()
        audioFocusRequest = request
        manager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AIRA listening",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when AIRA is using the microphone"
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("AIRA support is available")
        .setContentText(text)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .addAction(
            android.R.drawable.ic_dialog_info,
            "Help now",
            PendingIntent.getService(
                this,
                2,
                Intent(this, VoiceAssistantService::class.java).setAction(ACTION_HELP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Pause",
            PendingIntent.getService(
                this,
                1,
                Intent(this, VoiceAssistantService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        isRunning = false
        isForegroundActive = false
        listeningActive = false
        handler.removeCallbacksAndMessages(null)
        speechEngine.destroy()
        tts?.shutdown()
        abandonAudioFocus()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.calmcompanion.STOP"
        const val ACTION_HELP = "com.example.calmcompanion.HELP"
        const val EXTRA_AUTO_START = "auto_start"
        private const val CHANNEL_ID = "aira_listening"
        private const val NOTIFICATION_ID = 410
        private const val UTTERANCE_ID = "aira_support"
        private const val REPEAT_WINDOW_MS = 2 * 60 * 1000L
        @Volatile
        var isRunning: Boolean = false
            private set
        @Volatile
        var isForegroundActive: Boolean = false
            private set
    }
}
