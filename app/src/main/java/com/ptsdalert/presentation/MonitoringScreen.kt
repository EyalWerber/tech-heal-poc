// app/src/main/java/com/ptsdalert/presentation/MonitoringScreen.kt
package com.ptsdalert.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ptsdalert.DeviceProvider
import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.infrastructure.simulator.SimulatorMode

// WHY does this screen have zero hardware knowledge?
// In hexagonal architecture, the UI (presentation layer) only talks to the ViewModel,
// which only talks to a WearableDataSource port (interface). The screen never imports
// SimulatorWearableDataSource, BleWearableDataSource, or any concrete adapter.
// Python analogy: this module only imports an abstract base class, never the subclasses.
// Swapping hardware = swap the adapter behind DeviceProvider — this file never changes.

@Composable
fun MonitoringScreen(
    // WHY `viewModel()` composable?
    // `viewModel()` retrieves an existing ViewModel from the Compose ViewModelStore,
    // or creates a new one using the factory if none exists yet.
    // This means the ViewModel (and its data) survives recompositions and screen rotations.
    // Python analogy: it's like a singleton factory — you get the same instance every call
    // unless the Activity is destroyed.
    // We pass MonitoringViewModelFactory so the framework can inject WearableDataSource
    // into the ViewModel constructor (no DI framework needed).
    viewModel: MonitoringViewModel = viewModel(
        factory = MonitoringViewModelFactory(DeviceProvider.create())
    )
) {
    // WHY `collectAsState()`?
    // `viewModel.uiState` is a StateFlow — a Kotlin coroutine stream of state snapshots.
    // `collectAsState()` subscribes to that flow and converts each emission into a
    // Compose State object. When a new MonitoringUiState arrives, Compose redraws only
    // the composables that read from `uiState`.
    // Python analogy: it's like attaching an observer callback to an asyncio Queue —
    // every new item triggers a UI refresh.
    //
    // WHY `by` delegation?
    // `by` is Kotlin property delegation. It unwraps `.value` so we write `uiState.heartRate`
    // instead of `uiState.value.heartRate`. Same data, less noise — purely syntactic sugar.
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Title ──────────────────────────────────────────────────────────
            Text(
                text = "PTSD Alert Monitor",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Device source badge ───────────────────────────────────────────
            // `deviceLabel` comes from the ViewModel, which got it from the adapter.
            // This screen has no idea whether the label is "Simulator", "Garmin", "Polar", etc.
            // It just renders whatever string the ViewModel provides — clean separation of concerns.
            Text(
                text = "Source: ${uiState.deviceLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Heart Rate ────────────────────────────────────────────────────
            Text(
                text = "Heart Rate",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                // `?: "--"` is the Elvis operator — if heartRate is null, display "--".
                // Python equivalent: f"{ui_state.heart_rate or '--'} bpm"
                text = "${uiState.heartRate ?: "--"} bpm",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Arousal State ─────────────────────────────────────────────────
            Text(
                text = "State",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                // `.name` on a Kotlin enum returns its string identifier — "NORMAL", etc.
                text = uiState.arousalState.name,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = when (uiState.arousalState) {
                    ArousalState.NORMAL       -> Color(0xFF2E7D32)  // dark green
                    ArousalState.HYPERAROUSAL -> Color(0xFFC62828)  // dark red
                    ArousalState.HYPOAROUSAL  -> Color(0xFF1565C0)  // dark blue
                }
            )

            Spacer(modifier = Modifier.height(64.dp))

            // ── Simulation Controls ───────────────────────────────────────────
            // WHY only show buttons when `isSimulator`?
            // This is hexagonal architecture in action: the UI doesn't probe hardware.
            // `isSimulator` is set once in the ViewModel constructor based on which adapter
            // DeviceProvider returned. The screen trusts that flag and adapts its UI accordingly.
            // If tomorrow DeviceProvider returns a real BLE adapter, `isSimulator` is false
            // and these buttons simply disappear — zero changes to this file.
            // Python analogy: `if isinstance(adapter, SimulatorAdapter)` — but without
            // the type check happening in the UI layer.
            if (uiState.isSimulator) {
                Button(
                    onClick = { viewModel.setSimulatorMode(SimulatorMode.HYPERAROUSAL) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Simulate Hyperarousal", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.setSimulatorMode(SimulatorMode.HYPOAROUSAL) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    Text("Simulate Hypoarousal", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // OutlinedButton = secondary action style — border but no fill color
                OutlinedButton(
                    onClick = { viewModel.setSimulatorMode(SimulatorMode.NORMAL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset", fontSize = 16.sp)
                }
            }
        }
    }
}
