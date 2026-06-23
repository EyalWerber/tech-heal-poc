package com.ptsdalert.wear

import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.net.Socket

private const val TAG = "HealthDataService"
private const val MESSAGE_PATH = "/physiological-sample"
// Phone is at 10.0.0.1 on the shared WiFi network (watch is 10.0.0.5)
private const val PHONE_IP = "10.0.0.1"
private const val TCP_PORT = 9998

class HealthDataService : PassiveListenerService() {

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val hrPoints = dataPoints.getData(DataType.HEART_RATE_BPM)
        Log.d(TAG, "onNewDataPointsReceived: ${hrPoints.size} HR points")

        val hr = hrPoints.lastOrNull()?.value?.toInt() ?: return
        Log.d(TAG, "HR=$hr — sending to phone")

        val json = buildJson(hr, skinTemp = null)
        sendToPhone(json)
        sendToPhoneViaTcp(json)
    }

    private fun buildJson(hr: Int, skinTemp: Double?): String {
        val hrv = deriveHrv(hr)
        val stress = deriveStress(hr)
        return JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("heart_rate", hr)
            put("hrv", hrv)
            if (skinTemp != null) put("skin_temperature", skinTemp)
            put("stress_score", stress)
        }.toString()
    }

    private fun sendToPhone(json: String) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                Log.d(TAG, "Connected nodes: ${nodes.size}")
                val payload = json.toByteArray(Charsets.UTF_8)
                nodes.forEach { node ->
                    Wearable.getMessageClient(this)
                        .sendMessage(node.id, MESSAGE_PATH, payload)
                        .addOnSuccessListener { Log.d(TAG, "Message sent to ${node.displayName}") }
                        .addOnFailureListener { e -> Log.e(TAG, "Send failed: ${e.message}") }
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "getConnectedNodes failed: ${e.message}") }
    }

    private fun sendToPhoneViaTcp(json: String) {
        Thread {
            try {
                java.net.Socket(PHONE_IP, TCP_PORT).use { socket ->
                    socket.getOutputStream().bufferedWriter().use { writer ->
                        writer.write(json)
                        writer.newLine()
                        writer.flush()
                    }
                }
                Log.d(TAG, "TCP sent to phone: ${json.take(50)}")
            } catch (e: Exception) {
                Log.e(TAG, "TCP send failed: ${e.message}")
            }
        }.start()
    }

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
}
