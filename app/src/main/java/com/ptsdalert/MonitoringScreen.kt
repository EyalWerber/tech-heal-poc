package com.ptsdalert

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ptsdalert.domain.detection.DetectionEngine
import com.ptsdalert.domain.model.ArousalState

// ---- What is a @Composable function? ----
// In Jetpack Compose, the UI is built by calling functions annotated with @Composable.
// These functions describe WHAT the screen looks like — they don't return a View object
// like in old Android. Compose re-runs them automatically whenever state changes,
// similar to how React re-renders components when state updates.

// ---- What is `remember`? ----
// `remember` tells Compose: "keep this value across recompositions."
// Without it, every time the screen re-draws, the variable would reset to its initial value.
// In Python terms: it's like a closure variable that persists between calls.

// ---- What is `mutableStateOf`? ----
// `mutableStateOf(x)` creates an observable box holding value x.
// When you change the value inside the box, Compose notices and redraws the UI.
// `remember { mutableStateOf(x) }` = "create this observable box once and keep it."

// ---- What is `by`? ----
// `var sample by remember { mutableStateOf(...) }` is Kotlin's property delegation.
// Without `by`, you'd write: `sample.value = ...` and `sample.value` everywhere.
// With `by`, Kotlin unwraps it automatically so you just write `sample = ...` and `sample`.
// It's syntactic sugar — the behavior is identical.

@Composable
fun MonitoringScreen() {

    // State: the current sensor reading.
    // We start with a normal sample so the screen isn't blank on launch.
    var sample by remember { mutableStateOf(FakeWearableDataSource.normal()) }

    // State: what the DetectionEngine decided about that sample.
    var arousalState by remember { mutableStateOf(ArousalState.NORMAL) }

    // Surface fills the whole screen and applies the Material 3 background color.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Column stacks children vertically — like a vertical LinearLayout or a flex column.
        // `verticalArrangement` controls spacing between children.
        // `horizontalAlignment` centers children on the horizontal axis.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),             // 32dp padding on all sides
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ---- Title ----
            Text(
                text = "PTSD Alert Monitor",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(48.dp))  // empty vertical space

            // ---- Heart Rate Display ----
            Text(
                text = "Heart Rate",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                // String template: ${ } evaluates an expression inside a string — same as Python's f""
                // heartRate is now nullable (Int?) — use ?: to show "--" when null
                text = "${sample.heartRate ?: "--"} bpm",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ---- Arousal State Display ----
            Text(
                text = "State",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                // `.name` on an enum gives you its string name — "NORMAL", "HYPERAROUSAL", etc.
                text = arousalState.name,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                // Pick a color based on the current state using `when` as an expression.
                // In Kotlin, `when` can return a value — the whole block evaluates to one Color.
                color = when (arousalState) {
                    ArousalState.NORMAL       -> Color(0xFF2E7D32)  // dark green
                    ArousalState.HYPERAROUSAL -> Color(0xFFC62828)  // dark red
                    ArousalState.HYPOAROUSAL  -> Color(0xFF1565C0)  // dark blue
                }
            )

            Spacer(modifier = Modifier.height(64.dp))

            // ---- Buttons ----

            // Button: Simulate Hyperarousal
            // `onClick` is a lambda (anonymous function) — equivalent to a Python lambda or def.
            // The `{ }` block is the code that runs when the button is tapped.
            Button(
                onClick = {
                    sample = FakeWearableDataSource.hyper()         // get fake high-HR sample
                    arousalState = DetectionEngine.classify(sample)  // classify it
                    // Compose sees both state variables changed → redraws the UI automatically
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC62828)  // red to match HYPERAROUSAL
                )
            ) {
                Text(text = "Simulate Hyperarousal", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Button: Simulate Hypoarousal
            Button(
                onClick = {
                    sample = FakeWearableDataSource.hypo()
                    arousalState = DetectionEngine.classify(sample)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1565C0)  // blue to match HYPOAROUSAL
                )
            ) {
                Text(text = "Simulate Hypoarousal", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Button: Reset — OutlinedButton has a border but no fill (secondary action style)
            OutlinedButton(
                onClick = {
                    sample = FakeWearableDataSource.normal()  // reset to resting HR
                    arousalState = ArousalState.NORMAL         // force state back to normal
                    // We skip classify() here because we want a guaranteed NORMAL state,
                    // not one inferred from the number (which would be the same, but this is clearer).
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Reset", fontSize = 16.sp)
            }
        }
    }
}
