package com.ptsdalert.domain.detection

import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.domain.model.PhysiologicalSample
import org.junit.Assert.assertEquals
import org.junit.Test

// JUnit 4 unit test — runs on the JVM directly, no Android emulator needed.
//
// Python equivalent:
//   import pytest
//   def test_hr_above_100_is_hyperarousal():
//       sample = PhysiologicalSample(timestamp=0, heart_rate=101, ...)
//       assert DetectionEngine.classify(sample) == ArousalState.HYPERAROUSAL
//
// WHY test the boundary values (100, 50) explicitly?
// Off-by-one errors are the most common classification bugs.
// Checking both sides of each threshold catches ">" vs ">=" mistakes immediately.
class DetectionEngineTest {

    // Helper: build a sample with only heartRate filled in — all other fields null.
    // In Python: sample = PhysiologicalSample(timestamp=0, heart_rate=hr)
    private fun sample(hr: Int) = PhysiologicalSample(
        timestamp = 0L,
        heartRate = hr,
        hrv = null,
        skinTemperature = null,
        stressScore = null
    )

    // Backtick function names allow spaces — makes test output readable in test reports.
    // Python equivalent: def test_hr_above_100_is_hyperarousal(self): ...

    @Test fun `hr above 100 is HYPERAROUSAL`() {
        assertEquals(ArousalState.HYPERAROUSAL, DetectionEngine.classify(sample(101)))
    }

    @Test fun `hr exactly 100 is NORMAL`() {
        // Boundary: 100 is NOT above 100, so it should be NORMAL, not HYPERAROUSAL
        assertEquals(ArousalState.NORMAL, DetectionEngine.classify(sample(100)))
    }

    @Test fun `hr below 50 is HYPOAROUSAL`() {
        assertEquals(ArousalState.HYPOAROUSAL, DetectionEngine.classify(sample(49)))
    }

    @Test fun `hr exactly 50 is NORMAL`() {
        // Boundary: 50 is NOT below 50, so it should be NORMAL, not HYPOAROUSAL
        assertEquals(ArousalState.NORMAL, DetectionEngine.classify(sample(50)))
    }

    @Test fun `null heartRate returns NORMAL`() {
        // Device didn't send HR — don't alarm the user
        val sample = PhysiologicalSample(0L, null, null, null, null)
        assertEquals(ArousalState.NORMAL, DetectionEngine.classify(sample))
    }
}
