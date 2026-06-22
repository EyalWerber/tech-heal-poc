package com.ptsdalert.infrastructure.usb

import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.domain.ports.WearableDataSource
import kotlinx.coroutines.flow.Flow

// ADAPTER for future USB serial devices.
// Some medical-grade wearables communicate via USB serial (CDC ACM profile).
// They typically send newline-delimited JSON messages over the serial port.
//
// Example incoming message from the device:
//   {"heartRate": 88, "stressScore": 30, "timestamp": 1718000000000}
//
// TO INTEGRATE A USB SERIAL DEVICE:
//   1. Add permission to AndroidManifest.xml:
//      <uses-feature android:name="android.hardware.usb.host" />
//      <uses-permission android:name="android.permission.USB_PERMISSION" />
//   2. Use UsbManager to enumerate connected devices and request permission.
//   3. Open UsbDeviceConnection + UsbEndpoint (bulk-in endpoint).
//   4. Read bytes in a loop, buffer lines, parse JSON (using kotlinx.serialization).
//   5. Map JSON fields to PhysiologicalSample and emit to a Flow.
//   6. Replace TODO() in streamSamples() with that Flow.
class UsbWearableDataSource : WearableDataSource {

    override val deviceLabel: String = "USB Serial"

    override fun streamSamples(): Flow<PhysiologicalSample> {
        // TODO: Implement UsbManager device enumeration, connection, and JSON parsing.
        // See class-level comment for integration steps.
        TODO("USB serial integration not yet implemented")
    }

    // Conversion layer placeholder — will parse USB JSON payloads into domain model.
    // Future JSON shape: {"heartRate": 88, "stressScore": 30, "timestamp": 1718000000000}
    private fun parsePayload(json: String): PhysiologicalSample {
        TODO("JSON parsing not yet implemented — add kotlinx.serialization when ready")
    }
}
