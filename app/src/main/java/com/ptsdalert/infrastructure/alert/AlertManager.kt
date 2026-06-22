package com.ptsdalert.infrastructure.alert

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ptsdalert.domain.model.ArousalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val CHANNEL_ID = "PTSD_ALERT_CHANNEL"
const val ACTION_DISMISS = "com.ptsdalert.ACTION_DISMISS"

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

        // ForegroundService keeps the process out of Doze when screen is locked,
        // and shows the alert notification on the lock screen.
        val intent = Intent(context, AlertService::class.java).apply {
            putExtra(AlertService.EXTRA_STATE, state.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        // OS-managed repeating vibration — works even when screen is locked.
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 500L, 3500L), 0))

        // Sound every 3rd vibration. Reliable now that the process won't be frozen.
        soundJob = scope.launch {
            var count = 0
            while (true) {
                delay(4_000L)
                count++
                if (count % 3 == 0) playAlertSound()
            }
        }
    }

    fun stopAlerts() {
        soundJob?.cancel()
        soundJob = null
        vibrator.cancel()
        context.stopService(Intent(context, AlertService::class.java))
    }

    private fun playAlertSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, uri)?.play()
    }
}
