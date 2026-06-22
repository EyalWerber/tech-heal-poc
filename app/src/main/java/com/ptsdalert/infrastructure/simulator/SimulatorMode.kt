package com.ptsdalert.infrastructure.simulator

// Simulator-specific enum — lives in infrastructure, not domain.
//
// WHY not in domain?
// The concept of "simulate hyperarousal" only makes sense for the simulator adapter.
// Domain knows nothing about simulations, test modes, or tooling concerns.
// Keeping this in infrastructure means swapping it out (or deleting it entirely
// for production) requires zero changes to domain code.
//
// HOW this unblocks SimulatorControls.kt:
// SimulatorControls.kt has an import for com.ptsdalert.infrastructure.simulator.SimulatorMode.
// That was a forward reference — the file couldn't compile until THIS file existed.
// Now that this file exists, that import resolves and SimulatorControls.kt compiles cleanly.
// No edits to SimulatorControls.kt were needed — the forward import just works.
//
// Python analogy:
//   from enum import Enum
//   class SimulatorMode(Enum):
//       NORMAL = "normal"
//       HYPERAROUSAL = "hyperarousal"
//       HYPOAROUSAL = "hypoarousal"
enum class SimulatorMode {
    NORMAL,
    HYPERAROUSAL,
    HYPOAROUSAL
}
