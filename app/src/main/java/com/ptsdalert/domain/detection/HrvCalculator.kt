package com.ptsdalert.domain.detection

import kotlin.math.sqrt

class HrvCalculator(private val windowSize: Int = 30) {

    private val rrWindow = ArrayDeque<Double>()

    fun addSample(heartRateBpm: Int): Double? {
        val rr = 60_000.0 / heartRateBpm
        rrWindow.addLast(rr)
        if (rrWindow.size > windowSize) rrWindow.removeFirst()
        if (rrWindow.size < 2) return null
        val squaredDiffs = rrWindow.zipWithNext { a, b -> (b - a) * (b - a) }
        return sqrt(squaredDiffs.sum() / squaredDiffs.size)
    }

    fun reset() = rrWindow.clear()
}
