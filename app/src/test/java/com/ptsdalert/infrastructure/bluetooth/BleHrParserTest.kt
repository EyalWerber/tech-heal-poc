package com.ptsdalert.infrastructure.bluetooth

import org.junit.Assert.*
import org.junit.Test

class BleHrParserTest {

    // parseHrBytes: Flags byte bit 0 = 0 → UINT8, bit 0 = 1 → UINT16 (little-endian)

    @Test fun `parseHrBytes UINT8 returns correct HR`() {
        // flags=0x00 (UINT8), value=75
        val bytes = byteArrayOf(0x00, 75)
        assertEquals(75, parseHrBytes(bytes))
    }

    @Test fun `parseHrBytes UINT16 returns correct HR`() {
        // flags=0x01 (UINT16), value=180 = 0xB4 0x00 little-endian
        val bytes = byteArrayOf(0x01, 0xB4.toByte(), 0x00)
        assertEquals(180, parseHrBytes(bytes))
    }

    @Test fun `parseHrBytes UINT8 high value 200`() {
        val bytes = byteArrayOf(0x00, 200.toByte())
        assertEquals(200, parseHrBytes(bytes))
    }

    @Test fun `parseHrBytes empty returns null`() {
        assertNull(parseHrBytes(byteArrayOf()))
    }

    @Test fun `parseHrBytes UINT8 too short returns null`() {
        assertNull(parseHrBytes(byteArrayOf(0x00)))
    }

    @Test fun `parseHrBytes UINT16 too short returns null`() {
        assertNull(parseHrBytes(byteArrayOf(0x01, 0xB4.toByte())))
    }

    // deriveHrv: same formula as fake_ble_server.py

    @Test fun `deriveHrv normal range HR=72`() {
        // normal: 20.0 + (100 - 72) * 0.5 = 34.0
        assertEquals(34.0, deriveHrv(72), 0.01)
    }

    @Test fun `deriveHrv hyperarousal HR=120`() {
        // hyper: max(5.0, 80.0 - (120 - 100) * 0.8) = max(5.0, 64.0) = 64.0
        assertEquals(64.0, deriveHrv(120), 0.01)
    }

    @Test fun `deriveHrv hypoarousal HR=40`() {
        // hypo: 80.0 + (50 - 40) * 0.5 = 85.0
        assertEquals(85.0, deriveHrv(40), 0.01)
    }

    // deriveStress: same formula as fake_ble_server.py

    @Test fun `deriveStress normal range HR=72`() {
        // normal: (72 - 50) * 0.4 = 8
        assertEquals(8, deriveStress(72))
    }

    @Test fun `deriveStress hyperarousal HR=120`() {
        // hyper: min(100, (120-100)*1.5 + 40) = min(100, 70) = 70
        assertEquals(70, deriveStress(120))
    }

    @Test fun `deriveStress hypoarousal HR=40`() {
        // hypo: max(0, 30 - (50-40)) = max(0, 20) = 20
        assertEquals(20, deriveStress(40))
    }
}
