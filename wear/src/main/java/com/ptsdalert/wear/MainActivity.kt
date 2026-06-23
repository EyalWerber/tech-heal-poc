package com.ptsdalert.wear

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusText = TextView(this).apply {
            text = "PTSD Alert\nMonitoring active"
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            textSize = 14f
            setPadding(48, 0, 48, 0)
            gravity = android.view.Gravity.CENTER
        }
        val frame = android.widget.FrameLayout(this).apply {
            addView(statusText, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ).also { it.gravity = android.view.Gravity.CENTER })
        }
        setContentView(frame)
        requestSensorPermissionIfNeeded()
    }

    private fun requestSensorPermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "PTSD Alert\nRequesting body sensor permission..."
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                statusText.text = "PTSD Alert\nMonitoring active"
                startService(Intent(this, WearMonitoringService::class.java))
            } else {
                statusText.text = "PTSD Alert\nBody sensor permission denied"
            }
        }
    }
}
