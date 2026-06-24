package com.ptsdalert.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ptsdalert.DeviceProvider
import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.domain.model.LogEntry
import com.ptsdalert.domain.model.LogLevel
import com.ptsdalert.infrastructure.alert.AlertManager
import com.ptsdalert.infrastructure.settings.SettingsRepository
import com.ptsdalert.infrastructure.simulator.SimulatorMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitoringScreen(
    viewModel: MonitoringViewModel = viewModel(
        factory = MonitoringViewModelFactory(
            DeviceProvider.create(),
            AlertManager(LocalContext.current.applicationContext),
            SettingsRepository(LocalContext.current.applicationContext)
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var showBaselineDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Main monitoring panel ──────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PTSD Alert Monitor",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Source: ${uiState.deviceLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "Heart Rate",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${uiState.heartRate ?: "--"} bpm",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "est. HRV",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = uiState.estimatedHrv?.let { "${"%.1f".format(it)} ms" } ?: "-- ms",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                // ── HRV baseline row ──────────────────────────────────────
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val baselineLabel = uiState.baselineHrv
                        ?.let { "Baseline: ${"%.0f".format(it)}ms  (alert <${"%.0f".format(uiState.hrvHyperThreshold)}ms)" }
                        ?: "Baseline HRV: not set  (alert <20ms)"
                    Text(
                        text = baselineLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showBaselineDialog = true }) {
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "State",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = uiState.arousalState.name,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (uiState.arousalState) {
                        ArousalState.NORMAL       -> Color(0xFF2E7D32)
                        ArousalState.HYPERAROUSAL -> Color(0xFFC62828)
                        ArousalState.HYPOAROUSAL  -> Color(0xFF1565C0)
                    }
                )
                Spacer(modifier = Modifier.height(64.dp))

                if (uiState.isSimulator) {
                    Button(
                        onClick = { viewModel.setSimulatorMode(SimulatorMode.HYPERAROUSAL) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) { Text("Simulate Hyperarousal", fontSize = 16.sp) }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.setSimulatorMode(SimulatorMode.HYPOAROUSAL) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) { Text("Simulate Hypoarousal", fontSize = 16.sp) }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.setSimulatorMode(SimulatorMode.NORMAL) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Reset", fontSize = 16.sp) }
                }
            }
        }

        // ── Log panel ─────────────────────────────────────────────────────
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "LOGS",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            LazyColumn(reverseLayout = false) {
                items(uiState.recentLogs) { entry ->
                    LogLine(entry)
                }
            }
        }
    }

    // ── Baseline HRV dialog ────────────────────────────────────────────────
    if (showBaselineDialog) {
        BaselineHrvDialog(
            current = uiState.baselineHrv,
            onConfirm = { hrv ->
                viewModel.setBaselineHrv(hrv)
                showBaselineDialog = false
            },
            onDismiss = { showBaselineDialog = false }
        )
    }
}

@Composable
private fun BaselineHrvDialog(
    current: Double?,
    onConfirm: (Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(current?.let { "%.0f".format(it) } ?: "") }
    val parsed = input.trim().toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("My normal HRV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter your resting HRV (ms). Detection will alert when your HRV drops below 50% of this value.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Baseline HRV (ms)") },
                    placeholder = { Text("e.g. 15") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (parsed != null && parsed > 0) onConfirm(parsed)
                    })
                )
                if (parsed != null && parsed > 0) {
                    Text(
                        "Alert thresholds: hyper < ${"%.1f".format(parsed * 0.5)}ms  |  hypo > ${"%.1f".format(parsed * 2.5)}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (parsed != null && parsed > 0) onConfirm(parsed) },
                enabled = parsed != null && parsed > 0
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current != null) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text("Use defaults", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun LogLine(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.ERROR -> Color(0xFFFF5555)
        LogLevel.WARN  -> Color(0xFFFFAA00)
        LogLevel.INFO  -> Color(0xFF88FF88)
        LogLevel.DEBUG -> Color(0xFF888888)
    }
    val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(entry.timestamp))
    Text(
        text = "$time ${entry.level.name[0]} ${entry.tag}: ${entry.message}",
        color = color,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 14.sp
    )
}
