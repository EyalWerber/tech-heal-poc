package com.ptsdalert.domain.model

// Kotlin `enum class` — identical to Python's Enum class in concept:
//
//   Python equivalent:
//     from enum import Enum
//     class ArousalState(Enum):
//         NORMAL = "NORMAL"
//         HYPERAROUSAL = "HYPERAROUSAL"
//         HYPOAROUSAL = "HYPOAROUSAL"
//
// WHY use an enum instead of raw strings?
// The compiler catches typos at build time. You can never accidentally write
// ArousalState.HYPRAROUSAL — the compiler rejects it immediately.
// `when (state)` with an enum also forces you to handle every case (exhaustive check).
enum class ArousalState {
    NORMAL,         // HR in expected resting range (50–100 bpm)
    HYPERAROUSAL,   // HR too high — fight-or-flight response
    HYPOAROUSAL     // HR too low — freeze/dissociation response
}
