package com.ptsdalert.domain.detection

import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.domain.model.DetectionConfig
import com.ptsdalert.domain.model.PhysiologicalSample

object DetectionEngine {

    fun classify(sample: PhysiologicalSample, config: DetectionConfig = DetectionConfig()): ArousalState {
        val hr  = sample.heartRate
        val hrv = sample.hrv

        val hrState = when {
            hr == null -> ArousalState.NORMAL
            hr > 100   -> ArousalState.HYPERAROUSAL
            hr < 50    -> ArousalState.HYPOAROUSAL
            else       -> ArousalState.NORMAL
        }

        val hrvState = when {
            hrv == null                    -> ArousalState.NORMAL
            hrv < config.hrvHyperThreshold -> ArousalState.HYPERAROUSAL
            hrv > config.hrvHypoThreshold  -> ArousalState.HYPOAROUSAL
            else                           -> ArousalState.NORMAL
        }

        return when {
            hrState == ArousalState.HYPERAROUSAL || hrvState == ArousalState.HYPERAROUSAL -> ArousalState.HYPERAROUSAL
            hrState == ArousalState.HYPOAROUSAL  || hrvState == ArousalState.HYPOAROUSAL  -> ArousalState.HYPOAROUSAL
            else -> ArousalState.NORMAL
        }
    }
}
