package com.ptsdalert.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ptsdalert.domain.ports.WearableDataSource

// WHY a custom factory?
// Android's default ViewModel factory only instantiates ViewModels with zero-arg constructors.
// MonitoringViewModel needs a WearableDataSource injected at construction time.
// There is no DI framework (no Hilt/Dagger/Koin) in this project, so we wire it manually here.
// This factory is the "glue" that tells Android how to build the ViewModel with the right dependency.
//
// Python analogy: a partial function or a factory function.
//   factory = functools.partial(MonitoringViewModel, wearable_source=source)
//   vm = factory()   # Android calls this equivalent internally
class MonitoringViewModelFactory(
    private val wearableDataSource: WearableDataSource
) : ViewModelProvider.Factory {

    // `@Suppress("UNCHECKED_CAST")` silences the compiler warning for the `as T` cast below.
    // The cast is safe in practice: Android guarantees it only calls this method when
    // modelClass == MonitoringViewModel::class, so the cast will never actually fail at runtime.
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MonitoringViewModel(wearableDataSource) as T
    }
}
