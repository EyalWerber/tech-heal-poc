package com.ptsdalert.infrastructure.wearos

import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.domain.ports.WearableDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class WearOsWearableDataSource : WearableDataSource {
    override val deviceLabel = "Pixel Watch"
    override fun streamSamples(): Flow<PhysiologicalSample> = emptyFlow()
}
