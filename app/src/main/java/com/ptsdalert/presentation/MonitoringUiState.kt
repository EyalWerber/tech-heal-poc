package com.ptsdalert.presentation

import com.ptsdalert.domain.model.ArousalState

// Immutable snapshot of everything the UI needs to render one frame.
// The ViewModel emits a new instance whenever any field changes.
// WHY immutable? Compose diffs UI by value — if nothing changed, nothing redraws.
//
// Python analogy: a frozen dataclass.
//   @dataclass(frozen=True)
//   class MonitoringUiState:
//       heart_rate: Optional[int] = None
//       arousal_state: ArousalState = ArousalState.NORMAL
//       device_label: str = ""
//       is_simulator: bool = False
//
// WHY a separate UiState class instead of individual LiveData/StateFlow fields?
// Bundling all UI state into one object means one atomic update per frame:
// the UI either sees the old complete state or the new complete state, never a mix.
// In Python terms: replacing the whole dict at once vs. mutating individual keys.
data class MonitoringUiState(
    val heartRate: Int? = null,           // null until the first sample arrives
    val arousalState: ArousalState = ArousalState.NORMAL,
    val deviceLabel: String = "",
    val isSimulator: Boolean = false      // drives visibility of simulation buttons in the UI
)
