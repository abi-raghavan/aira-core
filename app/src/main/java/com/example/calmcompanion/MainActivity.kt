package com.example.calmcompanion

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calmcompanion.ui.theme.CalmCompanionTheme
import kotlinx.coroutines.flow.StateFlow

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

private enum class Screen { READY, SETUP, PRIVACY }

@Composable
fun AiraApp(viewModel: MainViewModel = viewModel()) {
    val assistantState by viewModel.assistantState.collectAsStateCompat()
    val running by viewModel.isServiceRunning.collectAsStateCompat()
    val status by viewModel.currentStatus.collectAsStateCompat()
    val hasPermission by viewModel.hasPermissions.collectAsStateCompat()
    val messages by viewModel.conversationHistory.collectAsStateCompat()
    val settings by viewModel.settings.collectAsStateCompat()
    val latestAlert by viewModel.latestAlert.collectAsStateCompat()
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
        topBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    "AIRA",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Always here. Just speak, or touch the circle.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { screen = Screen.READY }) { Text("Support") }
                TextButton(onClick = { screen = Screen.SETUP }) { Text("Caregiver setup") }
                TextButton(onClick = { screen = Screen.PRIVACY }) { Text("Safety") }
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
                messages = messages,
                settings = settings,
                latestAlert = latestAlert,
                onRequestPermissions = {
                    val permissions = buildList {
                        add(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionsLauncher.launch(permissions.toTypedArray())
                },
                onCalmTouch = viewModel::triggerHelp,
                onPause = viewModel::stopVoiceAssistant,
                onLoadDemoSetup = viewModel::loadDemoSetup,
                onDemo = viewModel::runCriticalDemo,
                onTestAlert = viewModel::sendTestAlert,
                onAcknowledge = viewModel::acknowledgeLatestAlert,
                onClear = viewModel::clearConversationHistory
            )
            Screen.SETUP -> SetupScreen(
                modifier = Modifier.padding(padding),
                initial = settings,
                onSave = {
                    viewModel.saveSettings(it)
                    if (it.automaticSms) {
                        permissionsLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                    }
                    screen = Screen.READY
                }
            )
            Screen.PRIVACY -> SafetyScreen(Modifier.padding(padding))
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
    messages: List<ConversationMessage>,
    settings: CaregiverSettings,
    latestAlert: AlertEvent?,
    onRequestPermissions: () -> Unit,
    onCalmTouch: () -> Unit,
    onPause: () -> Unit,
    onLoadDemoSetup: () -> Unit,
    onDemo: () -> Unit,
    onTestAlert: () -> Unit,
    onAcknowledge: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CalmSurface(
                assistantState = assistantState,
                hasPermission = hasPermission,
                onTouch = onCalmTouch
            )
        }
        item {
            Text(
                status,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (BuildConfig.DEMO_MODE) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Client demo",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "This build walks the full path: spoken guidance, critical detection, and a caregiver alert. Load the demo caregiver first if setup is empty.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Button(
                            onClick = onLoadDemoSetup,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) { Text("Load demo caregiver") }
                        Button(
                            onClick = onDemo,
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        ) { Text("Simulate critical phrase") }
                    }
                }
            }
        }
        if (!hasPermission) {
            item {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) { Text("Turn on private listening") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, "tel:${settings.caregiverPhone}".toUri())
                        )
                    },
                    enabled = settings.caregiverPhone.isNotBlank(),
                    modifier = Modifier.weight(1f).sizeIn(minHeight = 64.dp)
                ) { Text("Call caregiver") }
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:".toUri())) },
                    modifier = Modifier.weight(1f).sizeIn(minHeight = 64.dp)
                ) { Text("Emergency dialer") }
            }
        }
        if (settings.caregiverPhone.isNotBlank()) {
            item {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO, "smsto:${settings.caregiverPhone}".toUri())
                                .putExtra(
                                    "sms_body",
                                    "AIRA support request: please contact ${settings.patientName.ifBlank { "me" }}."
                                )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Message caregiver") }
            }
        }
        if (!settings.isConfigured) {
            item {
                NoticeCard("Caregiver alerts are off until setup and consent are complete. Calming support still works.")
            }
        } else if (!settings.canAutomaticallyAlert) {
            item {
                NoticeCard("Automatic alerts are not configured. Caregiver call and message stay available.")
            }
        }
        latestAlert?.let { alert ->
            item { AlertCard(alert, onAcknowledge) }
        }
        if (settings.isConfigured) {
            item {
                OutlinedButton(
                    onClick = onTestAlert,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Send test caregiver alert") }
            }
        }
        if (messages.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Recent session",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }
            items(messages.takeLast(6)) { MessageBubble(it) }
        }
        if (hasPermission) {
            item {
                TextButton(
                    onClick = onPause,
                    enabled = running,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (running) "Pause listening" else "Listening paused") }
            }
        }
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
    onTouch: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val breathing = rememberInfiniteTransition(label = "breath")
    val pulse by breathing.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val headline = when {
        !hasPermission -> "Touch to be guided"
        assistantState == AssistantState.SPEAKING -> "Breathe with me"
        assistantState == AssistantState.PROCESSING -> "I heard you"
        assistantState == AssistantState.ERROR -> "Touch for support"
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                headline,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                "In for four. Out for four.",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoticeCard(text: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
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
                "Caregiver and trigger setup",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text("Set this up together with the patient. Test alerts before relying on AIRA.")
        }
        item { OutlinedTextField(patient, { patient = it }, label = { Text("Patient preferred name") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(caregiver, { caregiver = it }, label = { Text("Caregiver name") }, modifier = Modifier.fillMaxWidth()) }
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
                label = { Text("Support phrases, separated by commas") },
                supportingText = { Text("These provide calming guidance and send a high-priority alert.") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                criticalTriggers,
                { criticalTriggers = it },
                label = { Text("Critical phrases, separated by commas") },
                supportingText = {
                    Text("Examples: call Abi, blue umbrella. Use only for phrases that mean urgent help.")
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                pairingToken,
                { pairingToken = it },
                label = { Text("Caregiver pairing token (optional)") },
                supportingText = { Text("Provided by the secure caregiver service; stored encrypted.") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            LabeledCheckbox(
                checked = consent,
                onCheckedChange = { consent = it },
                label = "We consent to caregiver alerts containing severity, but no raw audio or transcript."
            )
        }
        item {
            LabeledCheckbox(
                checked = automaticSms,
                onCheckedChange = { automaticSms = it },
                label = "Allow direct SMS fallback when internet is unavailable. Carrier charges may apply."
            )
        }
        item {
            Button(
                onClick = {
                    onSave(
                        CaregiverSettings(
                            patientName = patient,
                            caregiverName = caregiver,
                            caregiverPhone = phone,
                            pairingToken = pairingToken,
                            customTriggers = triggers.split(',').map(String::trim).filter(String::isNotBlank).toSet(),
                            customCriticalTriggers = criticalTriggers
                                .split(',')
                                .map(String::trim)
                                .filter(String::isNotBlank)
                                .toSet(),
                            consentToAlert = consent,
                            automaticSms = automaticSms
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                enabled = !consent || (caregiver.isNotBlank() && phone.isNotBlank())
            ) { Text("Save setup") }
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
private fun SafetyScreen(modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Safety and privacy",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item { Text("AIRA offers supportive calming guidance. It does not diagnose, monitor vital signs, or replace emergency services.") }
        item { Divider() }
        item { Text("Speech is requested for on-device processing. Android may fall back depending on the speech service installed on this device. Raw audio is not saved by AIRA.") }
        item { Text("Caregiver settings are encrypted on this device. Alert records store only severity, delivery state, and timestamps, not transcripts.") }
        item { Text("Direct SMS requires separate consent and permission. Google Play may restrict automated SMS; pilot distribution must verify eligibility.") }
        item { Text("If anyone is in immediate danger, use the Emergency dialer and contact local emergency services.") }
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
