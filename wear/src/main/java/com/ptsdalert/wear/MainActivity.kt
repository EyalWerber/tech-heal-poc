package com.ptsdalert.wear

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var hrText: TextView
    private lateinit var hrvText: TextView
    private lateinit var broadcastButton: Button
    private var broadcasting = false
    private val handler = Handler(Looper.getMainLooper())

    private val poller = object : Runnable {
        override fun run() {
            hrText.text = WearMonitoringService.lastHr?.let { "$it BPM" } ?: "-- BPM"
            hrvText.text = WearMonitoringService.lastHrv?.let { "HRV ${"%.1f".format(it)} ms" } ?: "HRV --"
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hrText = TextView(this).apply {
            textSize = 36f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            text = "-- BPM"
        }
        hrvText = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            text = "HRV --"
        }
        broadcastButton = Button(this).apply {
            textSize = 13f
            setOnClickListener { toggleBroadcast() }
        }
        updateButton()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(hrText)
            addView(hrvText)
            addView(broadcastButton)
        }
        setContentView(layout)

        val missing = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 100)
        } else {
            startForegroundService(Intent(this, WearMonitoringService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(poller)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(poller)
    }

    private fun toggleBroadcast() {
        broadcasting = !broadcasting
        startService(Intent(this, WearMonitoringService::class.java).apply {
            action = if (broadcasting) WearMonitoringService.ACTION_START_BROADCAST
                     else WearMonitoringService.ACTION_STOP_BROADCAST
        })
        updateButton()
    }

    private fun updateButton() {
        if (broadcasting) {
            broadcastButton.text = "Stop Broadcasting"
            broadcastButton.setBackgroundColor(Color.rgb(160, 0, 0))
        } else {
            broadcastButton.text = "Start Broadcasting"
            broadcastButton.setBackgroundColor(Color.DKGRAY)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startForegroundService(Intent(this, WearMonitoringService::class.java))
        } else {
            hrText.text = "No permission"
        }
    }
}
