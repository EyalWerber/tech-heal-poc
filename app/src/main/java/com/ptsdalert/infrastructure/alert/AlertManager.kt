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
import com.ptsdalert.domain.model.ArousalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val CHANNEL_ID = "PTSD_ALERT_CHANNEL"
private const val NOTIFICATION_ID = 1001
const val ACTION_DISMISS = "com.ptsdalert.ACTION_DISMISS"

class AlertManager(private val context: Context) {

    private var alertJob: Job? = null

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun startAlerts(state: ArousalState, scope: CoroutineScope) {
        stopAlerts()
        postNotification(state)
        alertJob = scope.launch {
            var count = 0
            while (true) {
                delay(4_000L)
                vibrate()
                count++
                if (count % 3 == 0) playAlertSound()
            }
        }
    }

    fun stopAlerts() {
        alertJob?.cancel()
        alertJob = null
        cancelNotification()
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    private fun playAlertSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, uri)?.play()
    }

    private fun postNotification(state: ArousalState) {
        val label = when (state) {
            ArousalState.HYPERAROUSAL -> "Hyperarousal detected — stay calm"
            ArousalState.HYPOAROUSAL -> "Hypoarousal detected — low arousal"
            ArousalState.NORMAL -> return
        }
        val dismissIntent = Intent(context, DismissReceiver::class.java).apply {
            action = ACTION_DISMISS
        }
        val pi = PendingIntent.getBroadcast(
            context, 0, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("PTSD Alert")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(0, "I'm working out", pi)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
