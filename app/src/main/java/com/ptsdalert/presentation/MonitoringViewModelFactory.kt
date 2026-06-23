package com.ptsdalert.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ptsdalert.domain.ports.WearableDataSource
import com.ptsdalert.infrastructure.alert.AlertManager

class MonitoringViewModelFactory(
    private val wearableDataSource: WearableDataSource,
    private val alertManager: AlertManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MonitoringViewModel(wearableDataSource, alertManager) as T
    }
}
