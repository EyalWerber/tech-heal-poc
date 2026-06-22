package com.ptsdalert.infrastructure.alert

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.infrastructure.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val CHANNEL_ID = "PTSD_ALERT_CHANNEL"
const val ACTION_DISMISS = "com.ptsdalert.ACTION_DISMISS"

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

        val intent = Intent(context, AlertService::class.java).apply {
            putExtra(AlertService.EXTRA_STATE, state.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 500L, 3500L), 0))

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
        if (soundJob != null) AppLogger.i(TAG, "Alert stopped")
        soundJob?.cancel()
        soundJob = null
        vibrator.cancel()
        context.stopService(Intent(context, AlertService::class.java))
    }

    private fun playAlertSound() {
        AppLogger.d(TAG, "Playing alert sound")
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, uri)?.play()
    }
}
