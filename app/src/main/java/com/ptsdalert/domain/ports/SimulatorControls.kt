package com.ptsdalert.domain.ports

// FORWARD DEPENDENCY NOTE (Task 3 will resolve this):
// SimulatorMode is defined in com.ptsdalert.infrastructure.simulator — created in Task 3.
// The import below is intentionally commented out until Task 3 creates SimulatorMode.
// When Task 3 runs and creates:
//   app/src/main/java/com/ptsdalert/infrastructure/simulator/SimulatorMode.kt
// un-comment the import and remove the typealias placeholder below.
//
// import com.ptsdalert.infrastructure.simulator.SimulatorMode

// Temporary placeholder so this file compiles before Task 3.
// Task 3 will delete this typealias and un-comment the real import above.
// In Python: just a forward type annotation string, e.g. def set_mode(self, mode: "SimulatorMode")
typealias SimulatorMode = String  // ← Task 3 removes this line

// WHY a second port interface instead of casting to SimulatorWearableDataSource?
//
// Python analogy — without this pattern, the ViewModel would do:
//   if isinstance(data_source, SimulatorWearableDataSource):
//       data_source.set_mode(mode)
//
// That leaks infrastructure knowledge into the ViewModel (bad).
// With this port, the ViewModel only asks: "does this source implement SimulatorControls?"
//   if isinstance(data_source, SimulatorControls):
//       data_source.set_mode(mode)
//
// Any future "demo mode" adapter can also implement this interface.
// The ViewModel never imports simulator-specific code.
interface SimulatorControls {

    // Switches the simulator between NORMAL, HYPERAROUSAL, and HYPOAROUSAL output modes.
    // Task 3 will provide the SimulatorMode enum with these values.
    fun setMode(mode: SimulatorMode)
}
