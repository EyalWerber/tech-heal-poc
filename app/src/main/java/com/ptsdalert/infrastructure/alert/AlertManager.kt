package com.ptsdalert.infrastructure.alert

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.infrastructure.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val CHANNEL_ID = "PTSD_ALERT_CHANNEL"
const val ACTION_DISMISS = "com.ptsdalert.ACTION_DISMISS"
const val ALERT_NOTIFICATION_ID = 1002

private const val TAG = "AlertManager"

class AlertManager(private val context: Context) {

    private var soundJob: Job? = null

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun startAlerts(state: ArousalState, scope: CoroutineScope) {
        stopAlerts()
        AppLogger.i(TAG, "Alert started: $state")

        updateService(state)
        postAlertNotification(state)

        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 500L, 3500L), 0))

        soundJob = scope.launch {
            while (true) {
                try {
                    playAlertSound()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Sound playback failed: ${e.message}")
                }
                delay(8_000L)
            }
        }
    }

    fun stopAlerts() {
        if (soundJob != null) AppLogger.i(TAG, "Alert stopped")
        soundJob?.cancel()
        soundJob = null
        vibrator.cancel()
        NotificationManagerCompat.from(context).cancel(ALERT_NOTIFICATION_ID)
        updateService(ArousalState.NORMAL)
    }

    private fun postAlertNotification(state: ArousalState) {
        val lockScreenPi = PendingIntent.getActivity(
            context, 2,
            Intent(context, LockScreenAlertActivity::class.java).apply {
                putExtra(LockScreenAlertActivity.EXTRA_STATE, state.name)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = when (state) {
            ArousalState.HYPERAROUSAL -> "Hyperarousal detected — stay calm"
            ArousalState.HYPOAROUSAL  -> "Hypoarousal detected — low arousal"
            ArousalState.NORMAL       -> return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("PTSD Alert")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(lockScreenPi, true)
            .setAutoCancel(false)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, notification)
            AppLogger.d(TAG, "Alert notification posted")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to post alert notification: ${e.message}")
        }
    }

    private fun updateService(state: ArousalState) {
        val intent = Intent(context, AlertService::class.java).apply {
            putExtra(AlertService.EXTRA_STATE, state.name)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update AlertService: ${e.message}")
        }
    }

    private fun playAlertSound() {
        AppLogger.d(TAG, "Playing alert sound")
        // TYPE_ALARM bypasses silent mode and DND — same stream as clock alarms.
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        RingtoneManager.getRingtone(context, uri)?.play()
    }
}
