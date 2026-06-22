package com.ptsdalert.infrastructure.alert

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.infrastructure.logging.AppLogger

// ForegroundService: keeps the process exempt from Doze when the screen is locked.
// Without this, Android freezes the app after the screen turns off and vibration stops.
// The foreground notification IS the alert the user sees on their lock screen.
class AlertService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stateName = intent?.getStringExtra(EXTRA_STATE) ?: run {
            AppLogger.e("AlertService", "onStartCommand: missing state extra — stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        val state = ArousalState.valueOf(stateName)
        AppLogger.i("AlertService", "Foreground service started for state: $state")
        startForeground(NOTIFICATION_ID, buildNotification(state))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppLogger.i("AlertService", "Foreground service destroyed")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(state: ArousalState): Notification {
        val label = when (state) {
            ArousalState.HYPERAROUSAL -> "Hyperarousal detected — stay calm"
            ArousalState.HYPOAROUSAL  -> "Hypoarousal detected — low arousal"
            ArousalState.NORMAL       -> "Monitoring"
        }
        val dismissIntent = Intent(this, DismissReceiver::class.java).apply { action = ACTION_DISMISS }
        val pi = PendingIntent.getBroadcast(
            this, 0, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("PTSD Alert")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // show full content on lock screen
            .setOngoing(true)
            .addAction(0, "I'm working out", pi)
            .build()
    }

    companion object {
        const val EXTRA_STATE = "state"
        const val NOTIFICATION_ID = 1001
    }
}
