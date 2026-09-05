package com.example.calmcompanion

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calmcompanion.ui.theme.CalmCompanionTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmCompanionTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AiraApp()
                }
            }
        }
    }
}

private enum class Screen { READY, SETUP, DEMO, HISTORY }

@Composable
fun AiraApp(viewModel: MainViewModel = viewModel()) {
    val assistantState by viewModel.assistantState.collectAsStateCompat()
    val running by viewModel.isServiceRunning.collectAsStateCompat()
    val status by viewModel.currentStatus.collectAsStateCompat()
    val hasPermission by viewModel.hasPermissions.collectAsStateCompat()
    val messages by viewModel.conversationHistory.collectAsStateCompat()
    val settings by viewModel.settings.collectAsStateCompat()
    val latestAlert by viewModel.latestAlert.collectAsStateCompat()
    val audioLevel by viewModel.audioLevel.collectAsStateCompat()
    var screen by remember { mutableStateOf(Screen.READY) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissions() }

    // Listening is the resting state: the patient should never have to start it.
    LaunchedEffect(hasPermission, screen) {
        if (hasPermission && screen == Screen.READY) viewModel.startVoiceAssistant()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AiraBrand() },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { screen = Screen.READY }) { Text("Listen") }
                TextButton(onClick = { screen = Screen.SETUP }) { Text("Setup") }
                if (BuildConfig.DEMO_MODE) {
                    TextButton(onClick = { screen = Screen.DEMO }) { Text("Demo") }
                }
                TextButton(onClick = { screen = Screen.HISTORY }) { Text("History") }
            }
        }
    ) { padding ->
        when (screen) {
            Screen.READY -> ReadyScreen(
                modifier = Modifier.padding(padding),
                assistantState = assistantState,
                running = running,
                status = status,
                hasPermission = hasPermission,
                settings = settings,
                level = audioLevel,
                onRequestPermissions = {
                    val permissions = buildList {
                        add(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionsLauncher.launch(permissions.toTypedArray())
                },
                onCalmTouch = viewModel::triggerHelp,
                onStart = viewModel::startVoiceAssistant
            )
            Screen.SETUP -> SetupScreen(
                modifier = Modifier.padding(padding),
                initial = settings,
                onSave = {
                    viewModel.saveSettings(it)
                    if (it.automaticSms && !BuildConfig.DEMO_MODE) {
                        permissionsLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                    }
                    screen = Screen.READY
                }
            )
            Screen.DEMO -> DemoScreen(
                modifier = Modifier.padding(padding),
                settings = settings,
                latestAlert = latestAlert,
                onLoadDemoSetup = viewModel::loadDemoSetup,
                onSimulateMessage = viewModel::simulateCaregiverMessage,
                onDemo = viewModel::runCriticalDemo,
                onAcknowledge = viewModel::acknowledgeLatestAlert
            )
            Screen.HISTORY -> HistoryScreen(
                modifier = Modifier.padding(padding),
                messages = messages,
                onClear = viewModel::clearConversationHistory
            )
        }
    }
}

@Composable
private fun ReadyScreen(
    modifier: Modifier,
    assistantState: AssistantState,
    running: Boolean,
    status: String,
    hasPermission: Boolean,
    settings: CaregiverSettings,
    level: Float,
    onRequestPermissions: () -> Unit,
    onCalmTouch: () -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CalmSurface(
            assistantState = assistantState,
            hasPermission = hasPermission,
            patientName = settings.patientName,
            level = level,
            onTouch = onCalmTouch
        )
        Spacer(Modifier.height(24.dp))
        Text(
            shortStatus(assistantState, running, hasPermission, status),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!hasPermission) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) { Text("Allow microphone") }
        } else if (!running) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) { Text("Start listening") }
        }
    }
}

private fun shortStatus(
    state: AssistantState,
    running: Boolean,
    hasPermission: Boolean,
    fallback: String
): String = when {
    !hasPermission -> "Microphone access needed"
    state == AssistantState.SPEAKING -> "Stay with my voice"
    state == AssistantState.PROCESSING -> "I heard you"
    state == AssistantState.ERROR -> "Listening needs attention"
    running -> "Listening"
    fallback.contains("sent", ignoreCase = true) -> fallback
    else -> "Ready"
}

/** The mark and wordmark, centred so the brand reads the same on every screen. */
@Composable
private fun AiraBrand() {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_aira_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "AIRA",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * The resting surface of the app. It shows that AIRA is listening and accepts an
 * immediate touch anywhere inside the circle, so no reading or aiming is required.
 */
@Composable
private fun CalmSurface(
    assistantState: AssistantState,
    hasPermission: Boolean,
    patientName: String,
    level: Float,
    onTouch: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val breathing = rememberInfiniteTransition(label = "breath")
    val breath by breathing.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val listening = assistantState == AssistantState.LISTENING
    // While listening the circle answers the voice; otherwise it keeps breathing.
    val voice by animateFloatAsState(
        targetValue = if (listening) level else 0f,
        animationSpec = tween(120),
        label = "voice"
    )
    val pulse = if (listening) 0.94f + 0.06f * voice else breath

    val headline = when {
        !hasPermission -> "I'm here"
        assistantState == AssistantState.SPEAKING -> "I'm with you"
        assistantState == AssistantState.PROCESSING -> "I heard you"
        assistantState == AssistantState.ERROR -> "I'm still here"
        patientName.isNotBlank() -> "I'm listening,\n$patientName"
        else -> "I'm listening"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(pulse)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onTouch()
            }
            .semantics {
                contentDescription = "AIRA is listening. Touch anywhere for calming support."
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                headline,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            VoiceWave(
                level = level,
                active = listening,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(0.62f).height(64.dp)
            )
        }
    }
}

/**
 * A live waveform of what the microphone is hearing. Each bar is one recent
 * loudness sample, so the wave scrolls while someone speaks and rests flat in
 * silence.
 */
@Composable
private fun VoiceWave(
    level: Float,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val latest by rememberUpdatedState(level)
    var samples by remember { mutableStateOf(List(BAR_COUNT) { 0f }) }

    LaunchedEffect(active) {
        if (!active) {
            samples = List(BAR_COUNT) { 0f }
            return@LaunchedEffect
        }
        while (true) {
            delay(60)
            samples = samples.drop(1) + latest
        }
    }

    // A slow ripple travels along the bars in silence, so the wave still looks awake.
    val ripple = rememberInfiniteTransition(label = "ripple")
    val phase by ripple.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing)
        ),
        label = "phase"
    )

    Canvas(modifier) {
        val slot = size.width / BAR_COUNT
        val barWidth = slot * 0.5f
        val corner = CornerRadius(barWidth / 2f)
        samples.forEachIndexed { index, sample ->
            val idle = if (active) {
                0.12f + 0.2f * (sin(phase + index * 0.6f) + 1f) / 2f
            } else {
                0.07f
            }
            val amplitude = maxOf(sample, idle)
            val barHeight = (size.height * amplitude).coerceAtMost(size.height)
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = index * slot + (slot - barWidth) / 2f,
                    y = (size.height - barHeight) / 2f
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = corner
            )
        }
    }
}

private const val BAR_COUNT = 18

@Composable
private fun DemoScreen(
    modifier: Modifier,
    settings: CaregiverSettings,
    latestAlert: AlertEvent?,
    onLoadDemoSetup: () -> Unit,
    onSimulateMessage: () -> Unit,
    onDemo: () -> Unit,
    onAcknowledge: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Demo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                if (settings.patientName.isBlank()) "Set a name, then run the story."
                else "Ready for ${settings.patientName}.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            OutlinedButton(
                onClick = onLoadDemoSetup,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Use sample: Sam + Abi") }
        }
        item {
            Button(
                onClick = onDemo,
                modifier = Modifier.fillMaxWidth().height(72.dp)
            ) { Text("Run critical-phrase demo") }
        }
        item {
            OutlinedButton(
                onClick = onSimulateMessage,
                enabled = settings.caregiverName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Show caregiver message") }
        }
        item {
            Text(
                "Demo only — no call, SMS, or server request is made.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        latestAlert?.let { alert ->
            item { AlertCard(alert, onAcknowledge) }
        }
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier,
    messages: List<ConversationMessage>,
    onClear: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "History",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (messages.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        if (messages.isEmpty()) {
            item {
                Text("No conversation yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(messages.takeLast(20).reversed()) { MessageBubble(it) }
        }
    }
}

@Composable
private fun AlertCard(alert: AlertEvent, onAcknowledge: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Caregiver alert ${alert.status.lowercase()}" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Caregiver alert: ${alert.status.lowercase()}", fontWeight = FontWeight.Bold)
            Text("Severity: ${alert.severity.lowercase()}. Attempts: ${alert.attemptCount}.")
            if (BuildConfig.DEMO_MODE) {
                Text("Simulated delivery. No message or call left this device.")
            }
            if (BuildConfig.DEMO_MODE && alert.status == AlertStatus.SENT.name) {
                Button(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth()) {
                    Text("Demo caregiver: acknowledge")
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    modifier: Modifier,
    initial: CaregiverSettings,
    onSave: (CaregiverSettings) -> Unit
) {
    var patient by remember(initial) { mutableStateOf(initial.patientName) }
    var caregiver by remember(initial) { mutableStateOf(initial.caregiverName) }
    var phone by remember(initial) { mutableStateOf(initial.caregiverPhone) }
    var pairingToken by remember(initial) { mutableStateOf(initial.pairingToken) }
    var triggers by remember(initial) { mutableStateOf(initial.customTriggers.joinToString(", ")) }
    var criticalTriggers by remember(initial) {
        mutableStateOf(initial.customCriticalTriggers.joinToString(", "))
    }
    var consent by remember(initial) { mutableStateOf(initial.consentToAlert) }
    var automaticSms by remember(initial) { mutableStateOf(initial.automaticSms) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Setup",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (BuildConfig.DEMO_MODE) "Choose the names and urgent phrase for this demo."
                else "Set up AIRA with the patient.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { OutlinedTextField(patient, { patient = it }, label = { Text("Patient name") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(caregiver, { caregiver = it }, label = { Text("Caregiver name") }, modifier = Modifier.fillMaxWidth()) }
        if (!BuildConfig.DEMO_MODE) {
            item {
                OutlinedTextField(
                    phone,
                    { phone = it },
                    label = { Text("Caregiver phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    triggers,
                    { triggers = it },
                    label = { Text("Support phrases") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            OutlinedTextField(
                criticalTriggers,
                { criticalTriggers = it },
                label = { Text("Urgent phrase") },
                supportingText = { Text("Example: call Abi") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!BuildConfig.DEMO_MODE) {
            item {
                OutlinedTextField(
                    pairingToken,
                    { pairingToken = it },
                    label = { Text("Caregiver pairing token (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                LabeledCheckbox(
                    checked = consent,
                    onCheckedChange = { consent = it },
                    label = "Allow caregiver alerts. Audio and transcripts are never included."
                )
            }
            item {
                LabeledCheckbox(
                    checked = automaticSms,
                    onCheckedChange = { automaticSms = it },
                    label = "Allow direct SMS fallback when internet is unavailable. Carrier charges may apply."
                )
            }
        }
        item {
            Button(
                onClick = {
                    onSave(
                        CaregiverSettings(
                            patientName = patient,
                            caregiverName = caregiver,
                            caregiverPhone = if (BuildConfig.DEMO_MODE) "0000000000" else phone,
                            pairingToken = pairingToken,
                            customTriggers = triggers.split(',').map(String::trim).filter(String::isNotBlank).toSet(),
                            customCriticalTriggers = criticalTriggers
                                .split(',')
                                .map(String::trim)
                                .filter(String::isNotBlank)
                                .toSet(),
                            consentToAlert = if (BuildConfig.DEMO_MODE) true else consent,
                            automaticSms = automaticSms
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                enabled = patient.isNotBlank() && caregiver.isNotBlank() &&
                    (BuildConfig.DEMO_MODE || !consent || phone.isNotBlank())
            ) { Text("Save and listen") }
        }
    }
}

@Composable
private fun LabeledCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MessageBubble(message: ConversationMessage) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (message.isFromUser) "Heard" else "AIRA",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(message.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun <T> StateFlow<T>.collectAsStateCompat() = collectAsStateWithLifecycle()
