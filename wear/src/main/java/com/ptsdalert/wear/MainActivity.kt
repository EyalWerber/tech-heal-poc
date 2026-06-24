package com.ptsdalert.wear

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.ScrollView
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
    private lateinit var btnHigh: Button
    private lateinit var btnLow: Button
    private lateinit var btnReal: Button
    private lateinit var btnHighHrv: Button
    private lateinit var btnNormalHrv: Button
    private lateinit var btnLowHrv: Button
    private lateinit var btnRealHrv: Button
    private lateinit var btnNormalBreathing: Button
    private lateinit var btnFastBreathing: Button
    private lateinit var btnRealBreathing: Button
    private lateinit var breathingText: TextView
    private var broadcasting = false
    private var activeFakeHr: Int? = null
    private var activeFakeHrv: Double? = null
    private var activeFakeBr: Float? = null
    private val handler = Handler(Looper.getMainLooper())

    private val poller = object : Runnable {
        override fun run() {
            val hr = activeFakeHr ?: WearMonitoringService.lastHr
            hrText.text = hr?.let { "$it BPM" } ?: "-- BPM"
            hrText.setTextColor(when (activeFakeHr) {
                120  -> Color.RED
                30   -> Color.rgb(80, 160, 255)
                else -> Color.WHITE
            })
            val hrv = activeFakeHrv ?: WearMonitoringService.lastHrv
            hrvText.text = hrv?.let { "HRV ${"%.1f".format(it)} ms" } ?: "HRV --"
            hrvText.setTextColor(when {
                activeFakeHrv != null && activeFakeHrv!! > 50 -> Color.GREEN
                activeFakeHrv != null && activeFakeHrv!! < 50 -> Color.rgb(255, 140, 0)
                else -> Color.LTGRAY
            })
            val br = activeFakeBr ?: WearMonitoringService.lastBreathingRate
            val bd = WearMonitoringService.lastBreathingDepth
            val bl = WearMonitoringService.lastBreathingLength
            breathingText.text = if (br != null) "BR:${"%.1f".format(br)} BD:${"%.2f".format(bd ?: 0f)} BL:${"%.1f".format(bl ?: 0f)}"
                                 else "Breathing --"
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
        breathingText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            text = "Breathing --"
        }
        broadcastButton = Button(this).apply {
            textSize = 13f
            setOnClickListener { toggleBroadcast() }
        }
        updateButton()

        btnHigh = Button(this).apply {
            text = "120 BPM"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(100, 0, 0))
            setOnClickListener { sendFakeHr(120) }
        }
        btnLow = Button(this).apply {
            text = "30 BPM"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(0, 40, 120))
            setOnClickListener { sendFakeHr(30) }
        }
        btnReal = Button(this).apply {
            text = "Back to Broadcast"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.DKGRAY)
            setOnClickListener { clearFakeHr() }
        }
        btnHighHrv = Button(this).apply {
            text = "High HRV (100ms)"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(0, 80, 0))
            setOnClickListener { sendFakeHrv(100.0) }
        }
        btnNormalHrv = Button(this).apply {
            text = "Normal HRV (30ms)"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(0, 100, 80))
            setOnClickListener { sendFakeHrv(30.0) }
        }
        btnLowHrv = Button(this).apply {
            text = "Low HRV (5ms)"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(80, 40, 0))
            setOnClickListener { sendFakeHrv(5.0) }
        }
        btnRealHrv = Button(this).apply {
            text = "Back to Real HRV"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.DKGRAY)
            setOnClickListener { clearFakeHrv() }
        }
        btnNormalBreathing = Button(this).apply {
            text = "Normal BR (15/min)"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(0, 80, 100))
            setOnClickListener { sendFakeBreathing(15f) }
        }
        btnFastBreathing = Button(this).apply {
            text = "Fast BR (25/min)"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(100, 60, 0))
            setOnClickListener { sendFakeBreathing(25f) }
        }
        btnRealBreathing = Button(this).apply {
            text = "Back to Real BR"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.DKGRAY)
            setOnClickListener { clearFakeBreathing() }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(hrText)
            addView(hrvText)
            addView(breathingText)
            addView(broadcastButton)
            addView(btnHigh)
            addView(btnLow)
            addView(btnReal)
            addView(btnHighHrv)
            addView(btnNormalHrv)
            addView(btnLowHrv)
            addView(btnRealHrv)
            addView(btnNormalBreathing)
            addView(btnFastBreathing)
            addView(btnRealBreathing)
        }
        setContentView(ScrollView(this).apply { addView(layout) })

        val missing = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.BODY_SENSORS_BACKGROUND,
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

    private fun sendFakeHr(bpm: Int) {
        activeFakeHr = bpm
        btnHigh.setBackgroundColor(if (bpm == 120) Color.RED else Color.rgb(100, 0, 0))
        btnLow.setBackgroundColor(if (bpm == 30) Color.rgb(0, 120, 255) else Color.rgb(0, 40, 120))
        startService(Intent(this, WearMonitoringService::class.java).apply {
            action = WearMonitoringService.ACTION_FAKE_HR
            putExtra(WearMonitoringService.EXTRA_FAKE_HR, bpm)
        })
    }

    private fun clearFakeHr() {
        activeFakeHr = null
        btnHigh.setBackgroundColor(Color.rgb(100, 0, 0))
        btnLow.setBackgroundColor(Color.rgb(0, 40, 120))
        startService(Intent(this, WearMonitoringService::class.java).apply {
            action = WearMonitoringService.ACTION_FAKE_HR
        })
    }

    private fun sendFakeHrv(hrv: Double) {
        activeFakeHrv = hrv
        btnHighHrv.setBackgroundColor(if (hrv == 100.0) Color.rgb(0, 160, 0) else Color.rgb(0, 80, 0))
        btnNormalHrv.setBackgroundColor(if (hrv == 30.0) Color.rgb(0, 200, 160) else Color.rgb(0, 100, 80))
        btnLowHrv.setBackgroundColor(if (hrv == 5.0) Color.rgb(160, 80, 0) else Color.rgb(80, 40, 0))
        startService(Intent(this, WearMonitoringService::class.java).apply {
            action = WearMonitoringService.ACTION_FAKE_HRV
            putExtra(WearMonitoringService.EXTRA_FAKE_HRV, hrv)
        })
    }

    private fun clearFakeHrv() {
        activeFakeHrv = null
        btnHighHrv.setBackgroundColor(Color.rgb(0, 80, 0))
        btnNormalHrv.setBackgroundColor(Color.rgb(0, 100, 80))
        btnLowHrv.setBackgroundColor(Color.rgb(80, 40, 0))
        startService(Intent(this, WearMonitoringService::class.java).apply {
            action = WearMonitoringService.ACTION_FAKE_HRV
            putExtra(WearMonitoringService.EXTRA_FAKE_HRV, -1.0)
        })
    }

    private fun sendFakeBreathing(br: Float) {
        activeFakeBr = br
        btnNormalBreathing.setBackgroundColor(if (br == 15f) Color.rgb(0, 160, 200) else Color.rgb(0, 80, 100))
        btnFastBreathing.setBackgroundColor(if (br == 25f) Color.rgb(200, 120, 0) else Color.rgb(100, 60, 0))
        startService(Intent(this, WearMonitoringService::class.java).apply {
            action = WearMonitoringService.ACTION_FAKE_BREATHING
            putExtra(WearMonitoringService.EXTRA_FAKE_BR, br)
        })
    }

    private fun clearFakeBreathing() {
        activeFakeBr = null
        btnNormalBreathing.setBackgroundColor(Color.rgb(0, 80, 100))
        btnFastBreathing.setBackgroundColor(Color.rgb(100, 60, 0))
        startService(Intent(this, WearMonitoringService::class.java).apply {
            action = WearMonitoringService.ACTION_FAKE_BREATHING
            putExtra(WearMonitoringService.EXTRA_FAKE_BR, -1f)
        })
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
