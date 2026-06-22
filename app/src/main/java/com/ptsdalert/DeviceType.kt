package com.ptsdalert

// One value per adapter in infrastructure/.
// To add a new wearable: add an entry here + a case in DeviceProvider.create().
enum class DeviceType {
    SIMULATOR,
    BLUETOOTH,
    USB,
    GARMIN,
    POLAR
}
