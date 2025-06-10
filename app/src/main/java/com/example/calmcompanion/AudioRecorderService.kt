package com.example.calmcompanion

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorderService : Service() {
    private val TAG = "AudioRecorderService"
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "AudioRecorderChannel"
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var recordingJob: Job? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_RECORDING" -> startRecording()
            "STOP_RECORDING" -> stopRecording()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Recorder Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Used for the audio recording service"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AIRA")
            .setContentText("Recording audio...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startRecording() {
        if (isRecording.get()) return
        
        isRecording.set(true)
        startForeground()
        
        Log.d(TAG, "Starting recording...")
        
        recordingJob = serviceScope.launch {
            while (isRecording.get() && isActive) {
                try {
                    recordAudio()
                    delay(8000) // Wait 8 seconds between recordings
                } catch (e: CancellationException) {
                    // This is a normal cancellation, just log it as debug
                    Log.d(TAG, "Recording coroutine was cancelled")
                    break
                } catch (e: Exception) {
                    // This is an actual error that needs to be handled
                    Log.e(TAG, "Error in recording loop: ${e.message}", e)
                    if (isActive) {
                        // Only retry if we're still active
                        delay(1000) // Wait a bit before retrying
                    }
                }
            }
        }
    }

    private fun recordAudio() {
        // Use a fixed filename to overwrite the existing file
        val audioFile = File(filesDir, "audio/recording.wav").apply {
            // Ensure the directory exists
            parentFile?.mkdirs()
        }
        
        Log.d(TAG, "Recording audio to file: ${audioFile.absolutePath}")

        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR) // Using RAW_AMR for better compatibility
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setAudioChannels(1) // Mono recording
                setAudioSamplingRate(16000) // 16kHz for voice
                setAudioEncodingBitRate(16000) // 16kbps for voice
                setOutputFile(audioFile.absolutePath)
                
                try {
                    Log.d(TAG, "Preparing MediaRecorder...")
                    prepare()
                    Log.d(TAG, "Starting recording...")
                    start()
                    // Record for 5 seconds
                    Thread.sleep(5000)
                    Log.d(TAG, "Stopping recording...")
                    stop()
                    release()
                    Log.d(TAG, "Recording completed and saved to: ${audioFile.absolutePath}")
                    
                    // Verify file exists and has content
                    if (audioFile.exists() && audioFile.length() > 0) {
                        Log.d(TAG, "Recording file size: ${audioFile.length()} bytes")
                        // Play back the recording
                        playRecording(audioFile)
                    } else {
                        Log.e(TAG, "Recording file is empty or does not exist")
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error recording audio: ${e.message}", e)
                    release()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Error in MediaRecorder state: ${e.message}", e)
                    release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaRecorder: ${e.message}", e)
        } finally {
            mediaRecorder = null
        }
    }

    private fun playRecording(audioFile: File) {
        try {
            // Request audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // Changed to speech
                        .build())
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                mediaPlayer?.pause()
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                mediaPlayer?.pause()
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                mediaPlayer?.start()
                            }
                        }
                    }
                    .build()
                
                val result = audioManager.requestAudioFocus(audioFocusRequest!!)
                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.e(TAG, "Could not get audio focus")
                    return
                }
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                mediaPlayer?.pause()
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                mediaPlayer?.pause()
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                mediaPlayer?.start()
                            }
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.e(TAG, "Could not get audio focus")
                    return
                }
            }

            // Set volume to maximum
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
            Log.d(TAG, "Set volume to maximum: $maxVolume")

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // Changed to speech
                    .build())
                setOnPreparedListener {
                    Log.d(TAG, "MediaPlayer prepared, starting playback")
                    start()
                }
                setOnCompletionListener {
                    Log.d(TAG, "Playback completed")
                    release()
                    mediaPlayer = null
                    // Abandon audio focus
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.abandonAudioFocus(null)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Error playing audio: what=$what, extra=$extra")
                    release()
                    mediaPlayer = null
                    true
                }
                setDataSource(audioFile.absolutePath)
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing recording: ${e.message}", e)
        }
    }

    private fun stopRecording() {
        isRecording.set(false)
        recordingJob?.cancel()
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recorder", e)
            }
        }
        mediaRecorder = null
        mediaPlayer?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping player", e)
            }
        }
        mediaPlayer = null
        // Abandon audio focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service is being destroyed")
        stopRecording()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 