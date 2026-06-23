package com.ptsdalert.infrastructure.wearos

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.infrastructure.logging.AppLogger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONObject

private const val TAG = "WearDataListenerService"

class WearDataListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != MESSAGE_PATH) return
        val json = String(event.data, Charsets.UTF_8)
        val sample = parseSample(json) ?: return
        sampleFlow.tryEmit(sample)
        AppLogger.d(TAG, "Sample received from watch: HR=${sample.heartRate}")
    }

    companion object {
        const val MESSAGE_PATH = "/physiological-sample"

        val sampleFlow = MutableSharedFlow<PhysiologicalSample>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        fun parseSample(json: String): PhysiologicalSample? = try {
            val obj = JSONObject(json)
            PhysiologicalSample(
                timestamp       = obj.optLong("timestamp", System.currentTimeMillis()),
                heartRate       = obj.optInt("heart_rate").takeIf { obj.has("heart_rate") },
                hrv             = obj.optDouble("hrv").takeIf { obj.has("hrv") },
                skinTemperature = obj.optDouble("skin_temperature").takeIf { obj.has("skin_temperature") },
                stressScore     = obj.optInt("stress_score").takeIf { obj.has("stress_score") }
            )
        } catch (e: Exception) {
            null
        }
    }
}
