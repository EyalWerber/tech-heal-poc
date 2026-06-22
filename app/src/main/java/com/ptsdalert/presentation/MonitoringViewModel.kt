package com.ptsdalert.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptsdalert.domain.detection.DetectionEngine
import com.ptsdalert.domain.ports.SimulatorControls
import com.ptsdalert.domain.ports.WearableDataSource
import com.ptsdalert.infrastructure.simulator.SimulatorMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// WHY inherit from ViewModel?
// Android destroys and recreates Activities on screen rotation (portrait ↔ landscape).
// Without ViewModel, all state would reset every rotation — you'd lose the HR readings.
// ViewModel persists until the user explicitly navigates away (presses back / closes app).
// In Python, state just lives in memory for the lifetime of the process — no equivalent concern.
// In Android, the Activity/Fragment is the "process" that restarts, so we need ViewModel to hold state.
//
// WHY does this class accept WearableDataSource (interface) instead of SimulatorWearableDataSource?
// This class is in the `presentation` package. It must NEVER import infrastructure classes directly.
// Importing SimulatorWearableDataSource here would couple the ViewModel to one specific adapter,
// defeating the hexagonal architecture: the UI layer would know about the infrastructure layer.
// Instead, we safe-cast to SimulatorControls — an interface check that works for any future
// "demo-mode" adapter, not just the simulator.
class MonitoringViewModel(
    private val wearableDataSource: WearableDataSource
) : ViewModel() {

    // `as?` is a safe cast — returns null instead of throwing ClassCastException if the cast fails.
    // Python equivalent:
    //   simulator_controls = source if isinstance(source, SimulatorControls) else None
    //
    // simulatorControls is non-null only if the active adapter also implements SimulatorControls.
    // This lets setSimulatorMode() be a no-op when running on real hardware.
    private val simulatorControls: SimulatorControls? = wearableDataSource as? SimulatorControls

    // MutableStateFlow is like a Python asyncio.Queue, but holds the latest value at all times.
    // Prefixed with `_` by convention — this is the writable internal copy.
    // Initialized with sensible defaults so the UI never crashes on first render.
    private val _uiState = MutableStateFlow(
        MonitoringUiState(
            deviceLabel = wearableDataSource.deviceLabel,
            isSimulator = simulatorControls != null
        )
    )

    // WHY expose a StateFlow instead of the MutableStateFlow?
    // `asStateFlow()` wraps _uiState in a read-only view.
    // The UI can observe (collect) but cannot push values — only the ViewModel writes state.
    // Python analogy: exposing a property with only a getter, hiding the private setter.
    //   @property
    //   def ui_state(self): return self._ui_state  # no setter exposed
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    init {
        // `init` runs once when the ViewModel is first created (i.e., not on rotation).
        //
        // WHY `viewModelScope.launch`?
        // This starts a background coroutine tied to the ViewModel's lifecycle.
        // When the ViewModel is destroyed, the scope cancels automatically — no leak.
        // Python analogy:
        //   task = asyncio.create_task(self._stream_samples())
        //   # task is cancelled in __del__ / context manager __aexit__
        viewModelScope.launch {
            // streamSamples() returns a cold Flow — nothing runs until .collect { } is called.
            // Each emitted sample triggers this lambda (like Python's `async for sample in stream:`).
            wearableDataSource.streamSamples().collect { sample ->
                val state = DetectionEngine.classify(sample)

                // WHY `_uiState.update { }` instead of `_uiState.value = ...`?
                // `update` is a thread-safe atomic read-modify-write.
                // On a multi-core device, two coroutines could race to set `_uiState.value`.
                // `update` uses compare-and-swap internally to guarantee no lost writes.
                // Python analogy: threading.Lock() around a dict mutation, or asyncio.Lock().
                //
                // `current.copy(...)` creates a new immutable instance with only the changed fields.
                // Python analogy: dataclasses.replace(current, heart_rate=..., arousal_state=...)
                _uiState.update { current ->
                    current.copy(
                        heartRate    = sample.heartRate,
                        arousalState = state
                    )
                }
            }
        }
    }

    // Called by the UI when simulation mode buttons are pressed.
    // The `?.` safe-call operator is a no-op if simulatorControls is null
    // (i.e., we're running on real hardware — nothing happens, no crash).
    fun setSimulatorMode(mode: SimulatorMode) {
        simulatorControls?.setMode(mode)
    }
}
