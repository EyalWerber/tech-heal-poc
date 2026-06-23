package com.ptsdalert.wear

import android.app.Application
import android.content.Intent

class WearApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startForegroundService(Intent(this, WearMonitoringService::class.java))
    }
}
