package com.ptsdalert.infrastructure.wearos

import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.domain.ports.WearableDataSource
import kotlinx.coroutines.flow.Flow

class WearOsWearableDataSource : WearableDataSource {

    override val deviceLabel: String = "Pixel Watch"

    override fun streamSamples(): Flow<PhysiologicalSample> =
        WearDataListenerService.sampleFlow
}
