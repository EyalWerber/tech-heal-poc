package com.ptsdalert.infrastructure.simulator

import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.domain.ports.SimulatorControls
import com.ptsdalert.domain.ports.WearableDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

// ADAPTER — the only concrete data source in this POC.
// Implements WearableDataSource so the ViewModel can stream samples through the port.
// Implements SimulatorControls so the UI can switch simulation modes.
//
// WHY this class implements TWO interfaces:
// In hexagonal architecture, an adapter can satisfy multiple ports at once.
// The ViewModel sees only WearableDataSource (what data it produces).
// The UI controls see only SimulatorControls (what mode to run in).
// Neither side imports this concrete class — only the interfaces.
// This is called "interface segregation" — callers depend only on what they use.
//
// Python analogy for the overall class:
//   class SimulatorWearableDataSource(WearableDataSource, SimulatorControls):
//       def __init__(self):
//           self._mode = "NORMAL"  # observable state
//
//       async def stream_samples(self):
//           while True:
//               yield self._sample_for(self._mode)
//               await asyncio.sleep(1)
//
//       def set_mode(self, mode: str):
//           self._mode = mode  # triggers stream restart in Kotlin via flatMapLatest
@OptIn(ExperimentalCoroutinesApi::class)
class SimulatorWearableDataSource : WearableDataSource, SimulatorControls {

    // Human-readable label — shown in the monitoring UI to identify the data source.
    override val deviceLabel: String = "Simulator"

    // MutableStateFlow is an observable box around the current mode.
    // WHY MutableStateFlow instead of a plain variable?
    // Because Flow operators like flatMapLatest can "watch" a StateFlow for changes.
    // When _mode.value changes, flatMapLatest below instantly cancels the old emission
    // loop and restarts a new one for the new mode.
    //
    // Python analogy: imagine a reactive variable from asyncio that, when reassigned,
    // fires a coroutine that cancels the old watcher and starts a new one.
    //
    //   Python (approximate):
    //     self._mode = asyncio.Event()  # notifies listeners on change
    private val _mode = MutableStateFlow(SimulatorMode.NORMAL)

    // SimulatorControls implementation.
    // The UI calls this when the user taps a mode button (NORMAL / HYPER / HYPO).
    // Setting _mode.value is enough — the stream restarts automatically.
    override fun setMode(mode: SimulatorMode) {
        _mode.value = mode
    }

    // WearableDataSource implementation — the main emission loop.
    //
    // WHY flatMapLatest?
    // _mode is a Flow of SimulatorMode values. Every time _mode emits a new value
    // (because setMode() was called), flatMapLatest:
    //   1. CANCELS the previous inner flow (stopping that mode's while-loop), and
    //   2. STARTS a new inner flow for the new mode.
    // This means there is never a race where old-mode samples leak into new-mode output.
    //
    // Python analogy:
    //   async for mode in self._mode:           # flatMapLatest outer: watch mode changes
    //       async for sample in emit_loop(mode): # flatMapLatest inner: new loop each time
    //           yield sample
    //
    // WHY flow { while(true) { emit(...); delay(...) } }?
    // `flow { }` is a coroutine-based generator builder.
    // `emit()` is exactly Python's `yield` — it suspends until the collector is ready.
    // `while (true)` keeps producing samples forever (until the collector cancels).
    // `delay(1_000L)` suspends the coroutine for 1 second — non-blocking, like asyncio.sleep(1).
    // The underscore in 1_000L is a Kotlin readability feature — same as Python's 1_000.
    override fun streamSamples(): Flow<PhysiologicalSample> = _mode
        .flatMapLatest { mode ->
            flow {
                while (true) {
                    emit(sampleFor(mode))
                    delay(1_000L)
                }
            }
        }

    // Pure function: given a mode, return one hard-coded PhysiologicalSample.
    // All fields are filled in — unlike the old FakeWearableDataSource which left HRV etc. null.
    // These values are clinically plausible for a PTSD monitoring POC.
    //
    // Python analogy:
    //   def _sample_for(self, mode: SimulatorMode) -> PhysiologicalSample:
    //       match mode:
    //           case SimulatorMode.NORMAL: return PhysiologicalSample(...)
    //           case SimulatorMode.HYPERAROUSAL: return PhysiologicalSample(...)
    //           case SimulatorMode.HYPOAROUSAL:  return PhysiologicalSample(...)
    private fun sampleFor(mode: SimulatorMode): PhysiologicalSample = when (mode) {
        SimulatorMode.NORMAL -> PhysiologicalSample(
            timestamp       = System.currentTimeMillis(),
            heartRate       = 72,    // textbook resting HR — DetectionEngine classifies as NORMAL (50–100)
            hrv             = 45.0,  // moderate HRV — calm, recovered
            skinTemperature = 36.6,  // normal core temperature
            stressScore     = 20     // low stress on 0–100 scale
        )
        SimulatorMode.HYPERAROUSAL -> PhysiologicalSample(
            timestamp       = System.currentTimeMillis(),
            heartRate       = 118,   // above 100 → DetectionEngine classifies as HYPERAROUSAL
            hrv             = 15.0,  // low HRV = sympathetic nervous system dominance = high stress
            skinTemperature = 37.2,  // slight temperature rise from fight-or-flight activation
            stressScore     = 85     // high stress on 0–100 scale
        )
        SimulatorMode.HYPOAROUSAL -> PhysiologicalSample(
            timestamp       = System.currentTimeMillis(),
            heartRate       = 38,    // below 50 → DetectionEngine classifies as HYPOAROUSAL
            hrv             = 80.0,  // paradoxically high HRV — vagal shutdown / freeze response
            skinTemperature = 35.8,  // slight temperature drop — peripheral vasoconstriction
            stressScore     = 10     // low score, but low arousal is also a clinical concern
        )
    }
}
