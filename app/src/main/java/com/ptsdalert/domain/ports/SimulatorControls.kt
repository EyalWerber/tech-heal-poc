package com.ptsdalert.domain.ports

import com.ptsdalert.infrastructure.simulator.SimulatorMode

// WHY a second port instead of casting to SimulatorWearableDataSource?
// The ViewModel should never import infrastructure classes.
// This interface lets the ViewModel say "does this source support mode-switching?"
// without knowing it's a simulator specifically.
// Any future "demo mode" adapter can also implement this interface.
//
// NOTE: SimulatorMode is defined in infrastructure/simulator/ (Task 3).
// This file will not compile until Task 3 creates SimulatorMode.
// That is intentional — this is a forward reference.
interface SimulatorControls {
    fun setMode(mode: SimulatorMode)
}
