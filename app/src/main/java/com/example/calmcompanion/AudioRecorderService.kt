package com.example.calmcompanion

import android.app.*
import android.content.Intent
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
    private var isRecording = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var recordingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
        
        recordingJob = serviceScope.launch {
            while (isRecording.get()) {
                try {
                    recordAudio()
                    delay(8000) // Wait 8 seconds between recordings
                } catch (e: Exception) {
                    Log.e(TAG, "Error in recording loop", e)
                }
            }
        }
    }

    private fun recordAudio() {
        val audioFile = File(filesDir, "audio/recording.wav").apply {
            parentFile?.mkdirs()
        }

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile.absolutePath)
            try {
                prepare()
                start()
                // Record for 5 seconds
                Thread.sleep(5000)
                stop()
                release()
            } catch (e: IOException) {
                Log.e(TAG, "Error recording audio", e)
            }
        }
        mediaRecorder = null
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
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 