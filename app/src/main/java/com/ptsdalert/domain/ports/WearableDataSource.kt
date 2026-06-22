package com.ptsdalert.domain.ports

import com.ptsdalert.domain.model.PhysiologicalSample
import kotlinx.coroutines.flow.Flow

// This is the PORT — the boundary between the PTSD detection core and any real hardware.
// "Port" is Hexagonal Architecture terminology: it defines the shape of a slot that
// any adapter (Garmin, BLE, Simulator, etc.) must fit into.
//
// Python equivalent using ABC:
//   from abc import ABC, abstractmethod
//   class WearableDataSource(ABC):
//       @property
//       @abstractmethod
//       def device_label(self) -> str: ...
//
//       @abstractmethod
//       def stream_samples(self): ...  # async generator
//
// WHY an interface instead of a concrete class?
// The ViewModel and detection engine depend ONLY on this interface.
// Swapping the wearable backend = swapping which class implements this interface.
// Nothing in the domain or UI layer changes.
//
// WHY Flow<PhysiologicalSample> instead of a callback or List?
// Flow is Kotlin's equivalent of Python's async generator (async def ...: yield ...).
//   Python analogy:
//     async def stream_samples(self):
//         while True:
//             yield PhysiologicalSample(...)
//             await asyncio.sleep(1.0)
//
// Flow emits values over time, supports backpressure, and is automatically cancelled
// when the UI coroutine scope is destroyed. Perfect for continuous sensor streams.
interface WearableDataSource {

    // Human-readable label displayed in the monitoring UI.
    // e.g., "Simulator", "Bluetooth HR Belt", "Garmin Fenix 7"
    val deviceLabel: String

    // Returns a cold Flow that emits one sample per hardware tick (typically ~1 Hz).
    // "Cold" means: nothing happens until someone calls .collect { } on the Flow.
    // The caller (ViewModel) collects — it has no idea where the data comes from.
    fun streamSamples(): Flow<PhysiologicalSample>
}
