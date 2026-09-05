package com.example.calmcompanion

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

interface SpeechEngine {
    val isAvailable: Boolean
    fun start(onText: (String) -> Unit, onError: (String) -> Unit)
    fun stop()
    fun destroy()
}

/**
 * Prefers Android's on-device recognizer so speech never leaves the phone. Many
 * devices report on-device recognition as available but have no language pack
 * downloaded; when that happens we fall back to the installed system recognizer
 * once, rather than telling the patient that speech is unavailable.
 */
class AndroidOfflineSpeechEngine(private val context: Context) : SpeechEngine {
    private var recognizer: SpeechRecognizer? = null
    private var listeningOnDevice = false
    private var onDeviceUsable = true

    private val onDeviceAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    private val systemAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override val isAvailable: Boolean
        get() = (onDeviceAvailable && onDeviceUsable) || systemAvailable

    override fun start(onText: (String) -> Unit, onError: (String) -> Unit) {
        val useOnDevice = onDeviceAvailable && onDeviceUsable
        if (!useOnDevice && !systemAvailable) {
            onError("No speech service is installed. Touch the circle for support.")
            return
        }
        listen(useOnDevice, onText, onError)
    }

    private fun listen(onDevice: Boolean, onText: (String) -> Unit, onError: (String) -> Unit) {
        stop()
        listeningOnDevice = onDevice
        recognizer = if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) onError("Listening…") else onText(text)
            }

            override fun onError(error: Int) {
                // The on-device engine could not serve this language. Retry once with
                // whichever recognizer the system provides.
                if (listeningOnDevice && error in ON_DEVICE_FALLBACK_ERRORS && systemAvailable) {
                    onDeviceUsable = false
                    listen(onDevice = false, onText = onText, onError = onError)
                    return
                }
                onError(messageFor(error))
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, onDevice)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Listening…"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone access is needed to listen."
        in ON_DEVICE_FALLBACK_ERRORS ->
            "No English speech pack is installed. Touch the circle for support."
        else -> "Speech recognition unavailable ($error)."
    }

    override fun stop() {
        recognizer?.setRecognitionListener(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private companion object {
        // Literal codes: ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE, and
        // ERROR_CANNOT_CHECK_SUPPORT only exist on API 33+, plus server-side failures
        // the on-device engine reports when a pack is missing.
        val ON_DEVICE_FALLBACK_ERRORS = setOf(4, 11, 12, 13, 14)
    }
}
