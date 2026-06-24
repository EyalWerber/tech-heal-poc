package com.ptsdalert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.infrastructure.alert.AlertService
import com.ptsdalert.infrastructure.alert.CHANNEL_ID
import com.ptsdalert.infrastructure.logging.AppLogger
import com.ptsdalert.infrastructure.logging.sqlite.SqliteLogRepository
import com.ptsdalert.presentation.MonitoringScreen
import com.ptsdalert.ui.theme.PTSDAlertPOCTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceProvider.init(this)
        AppLogger.init(SqliteLogRepository(applicationContext))
        AppLogger.i("MainActivity", "App started")
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        requestFullScreenIntentPermissionIfNeeded()
        requestBlePermissionsIfNeeded()
        startMonitoringService()
        enableEdgeToEdge()
        setContent {
            PTSDAlertPOCTheme {
                MonitoringScreen()
            }
        }
    }

    private fun requestFullScreenIntentPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                AppLogger.w("MainActivity", "USE_FULL_SCREEN_INTENT not granted — opening settings")
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
        }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, AlertService::class.java).apply {
            putExtra(AlertService.EXTRA_STATE, ArousalState.NORMAL.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
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
                // USAGE_ALARM bypasses silent mode and DND — required for a safety alert app.
                setSound(
                    Settings.System.DEFAULT_ALARM_ALERT_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
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

    private fun requestBlePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val missing = listOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ).filter {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
            }
        }
    }
}
