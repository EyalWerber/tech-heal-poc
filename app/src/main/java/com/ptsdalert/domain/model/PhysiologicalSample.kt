package com.ptsdalert.domain.model

// `data class` auto-generates equals/hashCode/toString/copy — like Python's @dataclass.
//
//   Python equivalent:
//     from dataclasses import dataclass
//     from typing import Optional
//
//     @dataclass
//     class PhysiologicalSample:
//         timestamp: int
//         heart_rate: Optional[int] = None
//         hrv: Optional[float] = None
//         skin_temperature: Optional[float] = None
//         stress_score: Optional[int] = None
//
// WHY are most fields nullable (the `?` suffix)?
// Different wearables expose different metrics.
// A Garmin Fenix gives HRV + temperature; a basic BLE HR belt gives only heartRate.
// Null means "this device didn't provide this measurement" — not zero, not error.
// The detection engine and UI both check for null before using any field.
data class PhysiologicalSample(
    val timestamp: Long,            // Unix epoch milliseconds — System.currentTimeMillis()
    val heartRate: Int?,            // Beats per minute — null if device doesn't provide it
    val hrv: Double?,               // Heart Rate Variability (ms) — stress/recovery indicator
    val skinTemperature: Double?,   // Degrees Celsius — some wearables expose this
    val stressScore: Int?           // 0-100 composite score — Garmin-style computed metric
)
