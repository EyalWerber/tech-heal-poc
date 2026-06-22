package com.ptsdalert

import com.ptsdalert.domain.model.PhysiologicalSample

// This file stands in for the real Garmin/wearable integration.
// When the real hardware is available, you replace this object with a GarminDataSource
// that implements the same three functions. The rest of the app doesn't change at all.

// `object` again — singleton, call functions directly: FakeWearableDataSource.normal()

object FakeWearableDataSource {

    // Returns a sample in the normal range (50–100 bpm).
    // We pick 72 because that's a textbook resting heart rate.
    fun normal(): PhysiologicalSample = PhysiologicalSample(
        timestamp = System.currentTimeMillis(),  // current time in milliseconds since epoch
        heartRate = 72,
        hrv = null,
        skinTemperature = null,
        stressScore = null
    )

    // Returns a sample above 100 bpm — will trigger HYPERAROUSAL in DetectionEngine.
    // 118 is plausible for an anxiety response, not so high it looks like exercise.
    fun hyper(): PhysiologicalSample = PhysiologicalSample(
        timestamp = System.currentTimeMillis(),
        heartRate = 118,
        hrv = null,
        skinTemperature = null,
        stressScore = null
    )

    // Returns a sample below 50 bpm — will trigger HYPOAROUSAL in DetectionEngine.
    // 38 is plausible for a freeze/dissociation response (or a very fit athlete at rest).
    fun hypo(): PhysiologicalSample = PhysiologicalSample(
        timestamp = System.currentTimeMillis(),
        heartRate = 38,
        hrv = null,
        skinTemperature = null,
        stressScore = null
    )
}
