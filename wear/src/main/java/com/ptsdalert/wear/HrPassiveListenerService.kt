package com.ptsdalert.wear

import android.content.Intent
import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import kotlin.math.sqrt

private const val TAG = "HrPassiveListenerService"

class HrPassiveListenerService : PassiveListenerService() {

    private val rrBuffer = ArrayDeque<Double>()

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val hr = dataPoints.getData(DataType.HEART_RATE_BPM)
            .lastOrNull()?.value?.toInt() ?: return
        if (hr <= 0) return

        val hrv = computeRmssd(hr)
        Log.d(TAG, "Background HR=$hr HRV=${hrv?.let { "%.1f".format(it) } ?: "--"}")

        startService(Intent(this, WearMonitoringService::class.java).apply {
            action = WearMonitoringService.ACTION_BACKGROUND_HR
            putExtra(WearMonitoringService.EXTRA_HR, hr)
            hrv?.let { putExtra(WearMonitoringService.EXTRA_HRV, it) }
        })
    }

    private fun computeRmssd(hr: Int): Double? {
        val rr = 60000.0 / hr
        rrBuffer.addLast(rr)
        if (rrBuffer.size > 10) rrBuffer.removeFirst()
        if (rrBuffer.size < 2) return null
        val squaredDiffs = rrBuffer.zipWithNext().map { (a, b) -> (b - a) * (b - a) }
        val result = sqrt(squaredDiffs.average())
        return if (result.isFinite()) result else null
    }
}
