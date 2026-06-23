package com.ptsdalert.wear

import android.app.Application
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import java.util.concurrent.Executors

class WearApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerPassiveListener()
    }

    private fun registerPassiveListener() {
        val client = HealthServices.getClient(this).passiveMonitoringClient
        val config = PassiveListenerConfig.builder()
            .setDataTypes(setOf(DataType.HEART_RATE_BPM))
            .build()
        val future = client.setPassiveListenerServiceAsync(HealthDataService::class.java, config)
        Futures.addCallback(
            future,
            object : FutureCallback<Void> {
                override fun onSuccess(result: Void?) = Unit
                override fun onFailure(t: Throwable) {
                    android.util.Log.e("WearApplication", "Failed to register passive listener: ${t.message}")
                }
            },
            Executors.newSingleThreadExecutor()
        )
    }
}
