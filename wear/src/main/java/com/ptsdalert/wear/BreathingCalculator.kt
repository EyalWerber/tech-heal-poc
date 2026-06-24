package com.ptsdalert.wear

import kotlin.math.sqrt

data class BreathingMetrics(
    val breathingRate: Float,    // breaths per minute
    val breathingDepth: Float,   // amplitude of filtered chest-motion signal
    val breathingLength: Float   // seconds per breath
)

class BreathingCalculator {
    private val sampleWindow = ArrayDeque<Pair<Long, Float>>()
    private val windowMs = 20_000L
    private var ema = 0f
    private val alpha = 0.08f  // ~0.3 Hz low-pass at 10 Hz sampling
    private var initialized = false
    private var lastBreathingNotifyMs = 0L

    fun addSample(x: Float, y: Float, z: Float, timestampMs: Long): BreathingMetrics? {
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (!initialized) { ema = magnitude; initialized = true }
        ema = alpha * magnitude + (1f - alpha) * ema

        sampleWindow.addLast(Pair(timestampMs, ema))
        while (sampleWindow.isNotEmpty() && timestampMs - sampleWindow.first().first > windowMs) {
            sampleWindow.removeFirst()
        }

        if (sampleWindow.size < 25) return null

        // Rate-limit output to once every 2 seconds
        if (timestampMs - lastBreathingNotifyMs < 2_000L) return null

        val values = sampleWindow.map { it.second }
        val times = sampleWindow.map { it.first }
        val mean = values.average().toFloat()

        // Find zero crossings
        val risingCrossings = mutableListOf<Long>()
        var prevAbove = values.first() > mean
        for (i in 1 until values.size) {
            val above = values[i] > mean
            if (above && !prevAbove) risingCrossings.add(times[i])
            prevAbove = above
        }

        if (risingCrossings.size < 2) return null

        val periods = risingCrossings.zipWithNext().map { (a, b) -> b - a }
        val avgPeriodMs = periods.average()
        if (avgPeriodMs <= 0) return null

        val br = (60_000.0 / avgPeriodMs).toFloat()
        if (br < 4f || br > 40f) return null

        val bl = (avgPeriodMs / 1000.0).toFloat()
        val bd = (values.max() - values.min()).coerceAtLeast(0f)

        lastBreathingNotifyMs = timestampMs
        return BreathingMetrics(br, bd, bl)
    }

    fun reset() {
        sampleWindow.clear()
        initialized = false
        ema = 0f
        lastBreathingNotifyMs = 0L
    }
}
