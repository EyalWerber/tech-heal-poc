package com.ptsdalert.infrastructure.alert

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.infrastructure.logging.AppLogger
import com.ptsdalert.infrastructure.wearos.WearDataListenerService

class AlertService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    private val wearMessageListener = MessageClient.OnMessageReceivedListener { event ->
        if (event.path == WearDataListenerService.MESSAGE_PATH) {
            val json = String(event.data, Charsets.UTF_8)
            val sample = WearDataListenerService.parseSample(json) ?: return@OnMessageReceivedListener
            WearDataListenerService.sampleFlow.tryEmit(sample)
            AppLogger.d(TAG, "WearOS sample via MessageClient: HR=${sample.heartRate}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            Wearable.getMessageClient(this).addListener(wearMessageListener)
            AppLogger.i(TAG, "WearOS MessageClient listener registered")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to register WearOS listener: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stateName = intent?.getStringExtra(EXTRA_STATE) ?: run {
            AppLogger.e("AlertService", "onStartCommand: missing state extra — stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        val state = ArousalState.valueOf(stateName)
        AppLogger.i("AlertService", "Foreground service started for state: $state")
        try {
            startForeground(NOTIFICATION_ID, buildNotification(state))
        } catch (e: Exception) {
            AppLogger.e("AlertService", "startForeground failed: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppLogger.i(TAG, "Foreground service destroyed")
        try {
            Wearable.getMessageClient(this).removeListener(wearMessageListener)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to unregister WearOS listener: ${e.message}")
        }
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PTSDAlert:AlertWakeLock").apply {
            acquire(10 * 60 * 1000L)
        }
        AppLogger.d("AlertService", "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        AppLogger.d("AlertService", "WakeLock released")
    }

    private fun buildNotification(state: ArousalState): Notification {
        val label = when (state) {
            ArousalState.HYPERAROUSAL -> "Hyperarousal detected — stay calm"
            ArousalState.HYPOAROUSAL  -> "Hypoarousal detected — low arousal"
            ArousalState.NORMAL       -> "Monitoring"
        }
        val dismissIntent = Intent(this, DismissReceiver::class.java).apply { action = ACTION_DISMISS }
        val dismissPi = PendingIntent.getBroadcast(
            this, 0, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val lockScreenPi = PendingIntent.getActivity(
            this, 1,
            Intent(this, LockScreenAlertActivity::class.java).apply {
                putExtra(LockScreenAlertActivity.EXTRA_STATE, state.name)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("PTSD Alert")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(lockScreenPi)

        if (state != ArousalState.NORMAL) {
            builder
                .setFullScreenIntent(lockScreenPi, true)
                .addAction(0, "I'm working out", dismissPi)
        }

        return builder.build()
    }

    companion object {
        private const val TAG = "AlertService"
        const val EXTRA_STATE = "state"
        const val NOTIFICATION_ID = 1001
    }
}
