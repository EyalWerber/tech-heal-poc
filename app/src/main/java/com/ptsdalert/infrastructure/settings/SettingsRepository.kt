package com.ptsdalert.infrastructure.settings

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("ptsd_settings", Context.MODE_PRIVATE)

    fun getBaselineHrv(): Double? = prefs.getString("baseline_hrv", null)?.toDoubleOrNull()

    fun setBaselineHrv(hrv: Double?) = prefs.edit().apply {
        if (hrv == null) remove("baseline_hrv") else putString("baseline_hrv", hrv.toString())
    }.apply()
}
