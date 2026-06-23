package com.ptsdalert.infrastructure.wearos

import com.ptsdalert.domain.model.PhysiologicalSample
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WearOsWearableDataSourceTest {

    @Test fun `streamSamples emits sample forwarded from WearDataListenerService`() = runTest {
        val source = WearOsWearableDataSource()
        val sample = PhysiologicalSample(
            timestamp = 1000L,
            heartRate = 80,
            hrv = 34.0,
            skinTemperature = 36.6,
            stressScore = 12
        )
        val results = mutableListOf<PhysiologicalSample>()
        val job = launch { source.streamSamples().take(1).collect { results.add(it) } }

        WearDataListenerService.sampleFlow.emit(sample)
        job.join()

        assertEquals(1, results.size)
        assertEquals(80, results[0].heartRate)
        assertEquals(36.6, results[0].skinTemperature)
    }

    @Test fun `deviceLabel is Pixel Watch`() {
        assertEquals("Pixel Watch", WearOsWearableDataSource().deviceLabel)
    }
}
