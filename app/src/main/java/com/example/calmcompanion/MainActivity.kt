package com.example.calmcompanion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.calmcompanion.ui.theme.AIRATheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecordingService()
        } else {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissionAndRecord() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startRecordingService()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startRecordingService() {
        val intent = Intent(this, AudioRecorderService::class.java).apply {
            action = "START_RECORDING"
        }
        startService(intent)
    }

    private fun stopRecordingService() {
        val intent = Intent(this, AudioRecorderService::class.java).apply {
            action = "STOP_RECORDING"
        }
        stopService(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIRATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onToggleRecording = { isRecording ->
                            if (isRecording) {
                                checkPermissionAndRecord()
                            } else {
                                stopRecordingService()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(onToggleRecording: (Boolean) -> Unit) {
    var isListening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Idle") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text(
            text = "🧠 AIRA is Active",
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Toggle Button
        Switch(
            checked = isListening,
            onCheckedChange = { 
                isListening = it
                status = if (it) "Listening..." else "Idle"
                onToggleRecording(it)
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Status Text
        Text(
            text = status,
            fontSize = 18.sp,
            color = when (status) {
                "Listening..." -> MaterialTheme.colorScheme.primary
                "Responded" -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onBackground
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AIRATheme {
        MainScreen(onToggleRecording = {})
    }
} 