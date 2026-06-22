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
    fun classify(sample: PhysiologicalSample): ArousalState {
        // Elvis operator ?: — Python equivalent: hr = sample.heart_rate; if hr is None: return NORMAL
        // If heartRate is null, early-return NORMAL without alarming.
        val hr = sample.heartRate
            ?: return ArousalState.NORMAL

        // `when` = Kotlin's match/case (or Python's if/elif/else, but as an expression).
        // Returns the matched ArousalState value directly.
        return when {
            hr > 100 -> ArousalState.HYPERAROUSAL
            hr < 50  -> ArousalState.HYPOAROUSAL
            else     -> ArousalState.NORMAL
        }
    }
}
