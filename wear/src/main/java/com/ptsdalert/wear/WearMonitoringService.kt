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
import androidx.health.services.client.data.DeltaDataType
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.util.concurrent.Executors

private const val TAG = "WearMonitoringService"
private const val MESSAGE_PATH = "/physiological-sample"

class WearMonitoringService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private var wakeLock: PowerManager.WakeLock? = null

    private val hrCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            Log.d(TAG, "HR availability: $availability")
        }

        override fun onDataReceived(data: DataPointContainer) {
            val hr = data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.toInt() ?: return
            Log.d(TAG, "HR=$hr — sending to phone via BT")
            sendToPhone(buildJson(hr))
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

        HealthServices.getClient(applicationContext).measureClient
            .registerMeasureCallback(DataType.HEART_RATE_BPM, executor, hrCallback)
        Log.i(TAG, "MeasureClient HR registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        HealthServices.getClient(applicationContext).measureClient
            .unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCallback)
        wakeLock?.release()
        executor.shutdown()
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PTSD Monitor")
            .setContentText("Heart rate monitoring active")
            .setOngoing(true)
            .build()

    private fun sendToPhone(json: String) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                val payload = json.toByteArray(Charsets.UTF_8)
                nodes.forEach { node ->
                    Wearable.getMessageClient(this)
                        .sendMessage(node.id, MESSAGE_PATH, payload)
                        .addOnSuccessListener { Log.d(TAG, "Sent HR to ${node.displayName}") }
                        .addOnFailureListener { e -> Log.e(TAG, "Send failed: ${e.message}") }
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "getConnectedNodes failed: ${e.message}") }
    }

    private fun buildJson(hr: Int): String =
        JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("heart_rate", hr)
            put("hrv", deriveHrv(hr))
            put("stress_score", deriveStress(hr))
        }.toString()

    private fun deriveHrv(hr: Int): Double = when {
        hr > 100 -> maxOf(5.0, 80.0 - (hr - 100) * 0.8)
        hr < 50  -> 80.0 + (50 - hr) * 0.5
        else     -> 20.0 + (100 - hr) * 0.5
    }

    private fun deriveStress(hr: Int): Int = when {
        hr > 100 -> minOf(100, ((hr - 100) * 1.5 + 40).toInt())
        hr < 50  -> maxOf(0, 30 - (50 - hr))
        else     -> ((hr - 50) * 0.4).toInt()
    }

    companion object {
        private const val CHANNEL_ID = "wear_monitoring"
        private const val NOTIFICATION_ID = 1001
    }
}
