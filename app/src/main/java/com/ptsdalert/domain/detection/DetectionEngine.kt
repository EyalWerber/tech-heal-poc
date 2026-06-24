package com.ptsdalert.domain.detection

import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.domain.model.PhysiologicalSample

// `object` = singleton — there is exactly one instance, created automatically by Kotlin.
//
// Python equivalent (module-level function pattern):
//   # detection_engine.py
//   def classify(sample: PhysiologicalSample) -> ArousalState:
//       ...
//
// Or as a static-method class:
//   class DetectionEngine:
//       @staticmethod
//       def classify(sample: PhysiologicalSample) -> ArousalState:
//           ...
//
// You call it as:  DetectionEngine.classify(sample)
// Never as:        DetectionEngine().classify(sample)  ← no () needed, it's already instantiated
//
// WHY keep this separate from WearableDataSource?
// Detection logic is pure computation — no hardware, no coroutines, no Android context.
// It can be tested with plain JUnit (no Android emulator needed).
// In the future, a real ML model replaces classify() — no UI or ViewModel changes needed.
object DetectionEngine {

    // Classifies a single sensor reading into an arousal state.
    //
    // Rules (thresholds are illustrative — a real clinical tool would calibrate per patient):
    //   - heartRate is null → NORMAL (device didn't send HR; don't alarm)
    //   - heartRate > 100  → HYPERAROUSAL (elevated — fight-or-flight territory)
    //   - heartRate < 50   → HYPOAROUSAL  (low — freeze/dissociation territory)
    //   - otherwise        → NORMAL
    //
    // Boundaries are INCLUSIVE of NORMAL:
    //   HR == 100 → NORMAL  (not HYPERAROUSAL)
    //   HR == 50  → NORMAL  (not HYPOAROUSAL)
    // HRV thresholds (RMSSD ms):
    //   < 20ms  → HYPERAROUSAL (low HRV = stress/fight-or-flight)
    //   > 80ms  → HYPOAROUSAL  (very high HRV = freeze/dissociation)
    private const val HRV_HYPER_THRESHOLD = 20.0
    private const val HRV_HYPO_THRESHOLD  = 80.0

    fun classify(sample: PhysiologicalSample): ArousalState {
        val hr  = sample.heartRate
        val hrv = sample.hrv

        // HR check
        val hrState = when {
            hr == null   -> ArousalState.NORMAL
            hr > 100     -> ArousalState.HYPERAROUSAL
            hr < 50      -> ArousalState.HYPOAROUSAL
            else         -> ArousalState.NORMAL
        }

        // HRV check (only when HRV is available)
        val hrvState = when {
            hrv == null          -> ArousalState.NORMAL
            hrv < HRV_HYPER_THRESHOLD -> ArousalState.HYPERAROUSAL
            hrv > HRV_HYPO_THRESHOLD  -> ArousalState.HYPOAROUSAL
            else                 -> ArousalState.NORMAL
        }

        // Either metric being abnormal triggers the alert; HYPERAROUSAL takes priority
        return when {
            hrState == ArousalState.HYPERAROUSAL || hrvState == ArousalState.HYPERAROUSAL -> ArousalState.HYPERAROUSAL
            hrState == ArousalState.HYPOAROUSAL  || hrvState == ArousalState.HYPOAROUSAL  -> ArousalState.HYPOAROUSAL
            else -> ArousalState.NORMAL
        }
    }
}
