package com.ptsdalert.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import java.util.concurrent.Executors

private const val TAG = "WearMonitoringService"

class WearMonitoringService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private var wakeLock: PowerManager.WakeLock? = null

    private val measureCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: androidx.health.services.client.data.DeltaDataType<*, *>, availability: Availability) {
            Log.d(TAG, "$dataType availability: $availability")
        }
        override fun onDataReceived(data: DataPointContainer) {
            data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.toInt()?.let {
                lastHr = it
                Log.i(TAG, "HR=$it")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "PTSD Monitoring", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(NOTIFICATION_ID, buildNotification())

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PTSDAlert:WearMonitoring")
            .also { it.acquire() }

        HealthServices.getClient(this).measureClient
            .registerMeasureCallback(DataType.HEART_RATE_BPM, executor, measureCallback)

        Log.i(TAG, "Service started — measuring HR")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        HealthServices.getClient(this).measureClient
            .unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, measureCallback)
        executor.shutdown()
        wakeLock?.release()
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PTSD Monitor")
            .setContentText("Measuring HR")
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "wear_monitoring"
        private const val NOTIFICATION_ID = 1001
        @Volatile var lastHr: Int? = null
    }
}
