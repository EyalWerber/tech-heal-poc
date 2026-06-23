package com.ptsdalert.wear

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var pulseText: TextView
    private lateinit var toggleButton: Button
    private lateinit var btnHigh: Button
    private lateinit var btnLow: Button
    private lateinit var btnNormal: Button
    private var running = false
    private var activeFakeHr: Int? = null

    private val handler = Handler(Looper.getMainLooper())
    private val hrPoller = object : Runnable {
        override fun run() {
            if (running && activeFakeHr == null) {
                val hr = WearMonitoringService.lastHr
                pulseText.text = if (hr != null) "$hr BPM" else "-- BPM"
                pulseText.setTextColor(Color.WHITE)
            }
            handler.postDelayed(this, 1000)
        }
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.ACTIVITY_RECOGNITION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusText = TextView(this).apply {
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            textSize = 13f
            setPadding(32, 8, 32, 4)
            gravity = android.view.Gravity.CENTER
        }

        pulseText = TextView(this).apply {
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            textSize = 28f
            setTextColor(Color.WHITE)
            setPadding(32, 0, 32, 8)
            gravity = android.view.Gravity.CENTER
            text = "-- BPM"
        }

        toggleButton = Button(this).apply {
            textSize = 13f
            setOnClickListener { toggle() }
        }

        btnHigh = Button(this).apply {
            text = "120 BPM"
            textSize = 12f
            setTextColor(Color.WHITE)
            setOnClickListener { toggleFakeHr(120) }
        }

        btnLow = Button(this).apply {
            text = "30 BPM"
            textSize = 12f
            setTextColor(Color.WHITE)
            setOnClickListener { toggleFakeHr(30) }
        }

        btnNormal = Button(this).apply {
            text = "Back to real HR"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.DKGRAY)
            setOnClickListener { clearFakeHr() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(16, 16, 16, 16)
            addView(statusText)
            addView(pulseText)
            addView(toggleButton)
            addView(btnHigh)
            addView(btnLow)
            addView(btnNormal)
        }

        setContentView(ScrollView(this).apply { addView(content) })

        val missing = requiredPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            statusText.text = "Requesting permissions..."
            setTestButtonsEnabled(false)
            toggleButton.isEnabled = false
            requestPermissions(missing.toTypedArray(), 100)
        } else {
            updateUi(running)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(hrPoller)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(hrPoller)
    }

    private fun toggle() {
        if (running) {
            stopService(Intent(this, WearMonitoringService::class.java))
            running = false
            activeFakeHr = null
            pulseText.text = "-- BPM"
        } else {
            startForegroundService(Intent(this, WearMonitoringService::class.java))
            running = true
        }
        updateUi(running)
    }

    private fun toggleFakeHr(bpm: Int) {
        if (!running) return
        startService(Intent(this, WearMonitoringService::class.java).apply {
            putExtra(WearMonitoringService.EXTRA_FAKE_HR, bpm)
        })
        activeFakeHr = if (activeFakeHr == bpm) null else bpm
        updateFakeButtons()
        updatePulseDisplay()
    }

    private fun clearFakeHr() {
        activeFakeHr?.let {
            startService(Intent(this, WearMonitoringService::class.java).apply {
                putExtra(WearMonitoringService.EXTRA_FAKE_HR, it)
            })
        }
        activeFakeHr = null
        updateFakeButtons()
        updatePulseDisplay()
    }

    private fun updatePulseDisplay() {
        val bpm = activeFakeHr
        if (bpm != null) {
            pulseText.text = "$bpm BPM"
            pulseText.setTextColor(when (bpm) {
                120  -> Color.RED
                30   -> Color.rgb(80, 160, 255)
                else -> Color.WHITE
            })
        }
        // real HR mode: let hrPoller update pulseText every second
    }

    private fun updateFakeButtons() {
        btnHigh.setBackgroundColor(if (activeFakeHr == 120) Color.RED else Color.rgb(100, 0, 0))
        btnLow.setBackgroundColor(if (activeFakeHr == 30) Color.rgb(0, 120, 255) else Color.rgb(0, 40, 120))
    }

    private fun updateUi(active: Boolean) {
        toggleButton.isEnabled = true
        setTestButtonsEnabled(active)
        if (active) {
            statusText.text = "Broadcasting"
            statusText.setTextColor(Color.GREEN)
            toggleButton.text = "Stop"
            toggleButton.setBackgroundColor(Color.rgb(160, 0, 0))
        } else {
            statusText.text = "Off"
            statusText.setTextColor(Color.LTGRAY)
            toggleButton.text = "Start"
            toggleButton.setBackgroundColor(Color.DKGRAY)
            pulseText.text = "-- BPM"
            pulseText.setTextColor(Color.WHITE)
        }
    }

    private fun setTestButtonsEnabled(enabled: Boolean) {
        btnHigh.isEnabled = enabled
        btnLow.isEnabled = enabled
        btnNormal.isEnabled = enabled
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                updateUi(running)
            } else {
                statusText.text = "Permissions denied"
                toggleButton.isEnabled = false
            }
        }
    }
}
