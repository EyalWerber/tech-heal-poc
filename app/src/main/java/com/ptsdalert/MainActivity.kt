// app/src/main/java/com/ptsdalert/MainActivity.kt
package com.ptsdalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ptsdalert.presentation.MonitoringScreen
import com.ptsdalert.ui.theme.PTSDAlertPOCTheme

// Entry point — equivalent to Python's `if __name__ == "__main__"`.
// Android's runtime calls onCreate() when the user taps the app icon.
// MainActivity's ONLY job is to hand control to Compose.
// All wiring (DeviceProvider → WearableDataSource → ViewModel → Screen)
// happens inside MonitoringScreen — this file stays clean and trivial.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PTSDAlertPOCTheme {
                MonitoringScreen()
            }
        }
    }
}
