package com.ptsdalert.domain.model

data class DetectionConfig(
    val baselineHrv: Double? = null
) {
    // If no baseline is set, fall back to population-level defaults.
    // With a personal baseline: hyper = 50% of baseline, hypo = 250%.
    val hrvHyperThreshold: Double = baselineHrv?.let { it * 0.5 } ?: 20.0
    val hrvHypoThreshold:  Double = baselineHrv?.let { it * 2.5 } ?: 80.0
}
