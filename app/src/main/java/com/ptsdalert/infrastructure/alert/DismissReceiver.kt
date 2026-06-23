package com.ptsdalert.infrastructure.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow

// Singleton signal bus: DismissReceiver → ViewModel, without coupling them.
// The BroadcastReceiver can't hold a reference to the ViewModel (Android creates
// it fresh per broadcast), so we route through a shared reactive stream.
object DismissSignal {
    val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DismissSignal.flow.tryEmit(Unit)
    }
}
