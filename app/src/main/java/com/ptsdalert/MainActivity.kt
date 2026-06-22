package com.ptsdalert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import com.ptsdalert.infrastructure.alert.CHANNEL_ID
import com.ptsdalert.infrastructure.logging.AppLogger
import com.ptsdalert.infrastructure.logging.sqlite.SqliteLogRepository
import com.ptsdalert.presentation.MonitoringScreen
import com.ptsdalert.ui.theme.PTSDAlertPOCTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.init(SqliteLogRepository(applicationContext))
        AppLogger.i("MainActivity", "App started")
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            PTSDAlertPOCTheme {
                MonitoringScreen()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Arousal Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for hypo/hyperarousal state transitions"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    0
                )
            }
        }
    }
}
