package com.ptsdalert.infrastructure.garmin

import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.domain.ports.WearableDataSource
import kotlinx.coroutines.flow.Flow

// ADAPTER for Garmin wearables.
// Garmin uses the Connect IQ SDK for watch apps, and the Garmin Mobile SDK
// for phone-side communication via BLE or Wi-Fi.
//
// TO INTEGRATE THE GARMIN MOBILE SDK:
//   1. Add the Garmin Mobile SDK AAR to app/libs/ and reference it in build.gradle.kts:
//      implementation(files("libs/garmin-mobile-sdk.aar"))
//   2. Initialize the SDK in MainActivity or Application:
//      ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS)
//   3. Register a message listener on the target ConnectIQ app.
//   4. In onMessageReceived(), map the Garmin data packet to PhysiologicalSample.
//   5. Emit mapped samples into a Flow and return it from streamSamples().
//
// Garmin exposes: HR, HRV, stress score, SpO2, body battery.
// Map those fields to PhysiologicalSample's nullable fields.
class GarminWearableDataSource : WearableDataSource {

    override val deviceLabel: String = "Garmin"

    override fun streamSamples(): Flow<PhysiologicalSample> {
        // TODO: Initialize ConnectIQ SDK and set up message listener.
        // See class-level comment for integration steps.
        TODO("Garmin Mobile SDK integration not yet implemented")
    }
}
