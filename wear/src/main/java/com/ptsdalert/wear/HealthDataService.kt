package com.ptsdalert.wear

import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

private const val MESSAGE_PATH = "/physiological-sample"

class HealthDataService : PassiveListenerService() {

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val hrPoints = dataPoints.getData(DataType.HEART_RATE_BPM)

        val hr = hrPoints.lastOrNull()?.value?.toInt() ?: return

        val json = buildJson(hr, skinTemp = null)
        sendToPhone(json)
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
                val payload = json.toByteArray(Charsets.UTF_8)
                nodes.forEach { node ->
                    Wearable.getMessageClient(this)
                        .sendMessage(node.id, MESSAGE_PATH, payload)
                }
            }
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
