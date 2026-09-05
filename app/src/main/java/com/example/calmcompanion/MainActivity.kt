package com.example.calmcompanion

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.example.calmcompanion.ui.theme.CalmCompanionTheme
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmCompanionTheme(dynamicColor = false) {
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

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("AIRA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Calm support. Human connection.", style = MaterialTheme.typography.bodyLarge)
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
                onHelp = viewModel::triggerHelp,
                onStart = viewModel::startVoiceAssistant,
                onPause = viewModel::stopVoiceAssistant,
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
    onHelp: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onDemo: () -> Unit,
    onTestAlert: () -> Unit,
    onAcknowledge: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            StatusCard(assistantState, status)
        }
        if (!settings.isConfigured) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Caregiver alerts are off until setup and consent are completed. Calming guidance still works.",
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (!settings.canAutomaticallyAlert) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Automatic alerts are not configured. Caregiver call and message buttons remain available.",
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHelp()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .semantics { contentDescription = "Help me now. Starts calming guidance." },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("HELP ME", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            }
        }
        item {
            Text(
                "Put both feet down. Breathe in slowly for four. Breathe out slowly for four.",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics {
                    contentDescription = "Breathing guide. In for four. Out for four."
                }
            )
        }
        if (!hasPermission) {
            item {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) { Text("Enable private voice support") }
            }
        } else {
            item {
                OutlinedButton(
                    onClick = if (running) onPause else onStart,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) { Text(if (running) "Pause listening" else "Start listening") }
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
        latestAlert?.let { alert ->
            item {
                AlertCard(alert, onAcknowledge)
            }
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
                    Text("Recent session", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }
            items(messages.takeLast(6)) { MessageBubble(it) }
        }
        if (BuildConfig.DEMO_MODE) {
            item {
                OutlinedButton(
                    onClick = onDemo,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Demo: simulate critical phrase") }
            }
        }
    }
}

@Composable
private fun StatusCard(state: AssistantState, status: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                when (state) {
                    AssistantState.LISTENING -> "LISTENING"
                    AssistantState.PROCESSING -> "RESPONDING"
                    AssistantState.SPEAKING -> "CALMING GUIDANCE"
                    AssistantState.ERROR -> "NEEDS ATTENTION"
                    AssistantState.IDLE -> "READY"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(status, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AlertCard(alert: AlertEvent, onAcknowledge: () -> Unit) {
    Card(Modifier.fillMaxWidth().semantics { contentDescription = "Caregiver alert ${alert.status.lowercase()}" }) {
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
    var consent by remember(initial) { mutableStateOf(initial.consentToAlert) }
    var automaticSms by remember(initial) { mutableStateOf(initial.automaticSms) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Caregiver and trigger setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                label = { Text("Custom trigger phrases, separated by commas") },
                supportingText = { Text("Examples: I need my person, red balloon") },
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
        item { Text("Safety and privacy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("AIRA offers supportive calming guidance. It does not diagnose, monitor vital signs, or replace emergency services.") }
        item { Divider() }
        item { Text("Speech is requested for on-device processing. Android may fall back depending on the speech service installed on this device. Raw audio is not saved by AIRA.") }
        item { Text("Caregiver settings are encrypted on this device. Alert records store only severity, delivery state, and timestamps—not transcripts.") }
        item { Text("Direct SMS requires separate consent and permission. Google Play may restrict automated SMS; pilot distribution must verify eligibility.") }
        item { Text("If anyone is in immediate danger, use the Emergency dialer and contact local emergency services.") }
    }
}

@Composable
private fun MessageBubble(message: ConversationMessage) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(if (message.isFromUser) "Heard" else "AIRA", fontWeight = FontWeight.Bold)
            Text(message.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun <T> StateFlow<T>.collectAsStateCompat() = collectAsStateWithLifecycle()
