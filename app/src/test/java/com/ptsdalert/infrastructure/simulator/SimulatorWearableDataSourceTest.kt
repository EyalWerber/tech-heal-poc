package com.ptsdalert.infrastructure.simulator

import com.ptsdalert.domain.detection.DetectionEngine
import com.ptsdalert.domain.model.ArousalState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

// Unit tests for SimulatorWearableDataSource.
//
// WHY runTest instead of runBlocking?
// runTest is from kotlinx.coroutines.test — it provides a TestCoroutineScheduler that
// lets `delay()` calls complete instantly (virtual time) instead of waiting real wall-clock time.
// This means our 1-second delay in the emission loop doesn't slow down tests.
//
// Python analogy:
//   asyncio.run(main())               # runBlocking — real time
//   loop.run_until_complete(main())   # runTest — with a mock clock that skips delays
//
// WHY flow.first()?
// `first()` collects exactly one value from the Flow and then cancels the collection.
// It's the coroutine equivalent of next(iter(generator)) in Python:
//   sample = next(source.stream_samples())  # Python
//   val sample = source.streamSamples().first()  # Kotlin
//
// The Flow's while(true) loop is cancelled automatically after first() returns.
class SimulatorWearableDataSourceTest {

    // --- Test 1: Default mode produces NORMAL classification ---
    // A freshly created SimulatorWearableDataSource starts in NORMAL mode.
    // The first emitted sample should have HR=72, which DetectionEngine classifies as NORMAL.
    @Test
    fun `default mode emits NORMAL samples`() = runTest {
        val source = SimulatorWearableDataSource()
        val sample = source.streamSamples().first()

        // Sanity check: heartRate is not null (the simulator always provides it)
        assertNotNull(sample.heartRate)

        // The real assertion: DetectionEngine should classify a 72 bpm sample as NORMAL
        assertEquals(ArousalState.NORMAL, DetectionEngine.classify(sample))
    }

    // --- Test 2: After setMode(HYPERAROUSAL), samples classify as HYPERAROUSAL ---
    // HR=118 > 100, so DetectionEngine returns HYPERAROUSAL.
    // This test verifies that setMode() actually changes what the stream emits.
    //
    // WHY set mode BEFORE calling streamSamples().first()?
    // _mode is a MutableStateFlow. setMode() changes _mode.value synchronously.
    // When streamSamples().first() is called, flatMapLatest reads the current mode value
    // and starts the correct emission loop from the beginning. No race condition.
    @Test
    fun `hyper mode emits HYPERAROUSAL samples`() = runTest {
        val source = SimulatorWearableDataSource()
        source.setMode(SimulatorMode.HYPERAROUSAL)
        val sample = source.streamSamples().first()
        assertEquals(ArousalState.HYPERAROUSAL, DetectionEngine.classify(sample))
    }

    // --- Test 3: After setMode(HYPOAROUSAL), samples classify as HYPOAROUSAL ---
    // HR=38 < 50, so DetectionEngine returns HYPOAROUSAL.
    @Test
    fun `hypo mode emits HYPOAROUSAL samples`() = runTest {
        val source = SimulatorWearableDataSource()
        source.setMode(SimulatorMode.HYPOAROUSAL)
        val sample = source.streamSamples().first()
        assertEquals(ArousalState.HYPOAROUSAL, DetectionEngine.classify(sample))
    }

    // --- Test 4: deviceLabel is exactly "Simulator" ---
    // This is a non-coroutine test — no runTest needed because deviceLabel is a plain property.
    // The label appears in the monitoring UI to tell the user which data source is active.
    @Test
    fun `deviceLabel is Simulator`() {
        val source = SimulatorWearableDataSource()
        assertEquals("Simulator", source.deviceLabel)
    }
}
