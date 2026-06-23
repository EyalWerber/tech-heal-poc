package com.ptsdalert.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ptsdalert.DeviceProvider
import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.domain.model.LogEntry
import com.ptsdalert.domain.model.LogLevel
import com.ptsdalert.infrastructure.alert.AlertManager
import com.ptsdalert.infrastructure.simulator.SimulatorMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(
    viewModel: MonitoringViewModel = viewModel(
        factory = MonitoringViewModelFactory(
            DeviceProvider.create(),
            AlertManager(LocalContext.current.applicationContext)
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                Spacer(modifier = Modifier.height(32.dp))

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

        if (uiState.bleDevices.isNotEmpty()) {
            ModalBottomSheet(
                onDismissRequest = {},
                sheetState = sheetState
            ) {
                BleDevicePicker(
                    devices = uiState.bleDevices,
                    scanning = uiState.bleScanning,
                    onDeviceSelected = { viewModel.onDeviceSelected(it) }
                )
            }
        }
    }
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

@Composable
private fun BleDevicePicker(
    devices: List<com.ptsdalert.infrastructure.bluetooth.BleDevice>,
    scanning: Boolean,
    onDeviceSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = if (scanning) "Select your HR monitor (scanning…)" else "Select your HR monitor",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (devices.isEmpty()) {
            Text(
                text = "No devices found yet. Make sure your device is nearby and on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            devices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDeviceSelected(device.address) }
                        .padding(vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
