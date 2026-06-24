package com.ptsdalert.infrastructure.bluetooth

internal fun parseHrBytes(bytes: ByteArray): Int? {
    if (bytes.isEmpty()) return null
    val flags = bytes[0].toInt() and 0xFF
    val isUint16 = flags and 0x01 != 0
    return if (isUint16) {
        if (bytes.size < 3) null
        else ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
    } else {
        if (bytes.size < 2) null
        else bytes[1].toInt() and 0xFF
    }
}

internal fun deriveHrv(hr: Int): Double = when {
    hr > 100 -> maxOf(5.0, 80.0 - (hr - 100) * 0.8)
    hr < 50  -> 80.0 + (50 - hr) * 0.5
    else     -> 20.0 + (100 - hr) * 0.5
}

internal fun deriveStress(hr: Int): Int = when {
    hr > 100 -> minOf(100, ((hr - 100) * 1.5 + 40).toInt())
    hr < 50  -> maxOf(0, 30 - (50 - hr))
    else     -> ((hr - 50) * 0.4).toInt()
}
