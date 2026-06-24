package com.ptsdalert.domain.model

data class PhysiologicalSample(
    val timestamp: Long,
    val heartRate: Int?,
    val hrv: Double?,
    val skinTemperature: Double?,
    val stressScore: Int?,
    val breathingRate: Float? = null,    // breaths per minute
    val breathingDepth: Float? = null,   // relative chest-movement amplitude
    val breathingLength: Float? = null   // seconds per breath
)
