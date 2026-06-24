package com.ptsdalert.infrastructure.settings

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("ptsd_settings", Context.MODE_PRIVATE)

    fun getBaselineHrv(): Double? = prefs.getString("baseline_hrv", null)?.toDoubleOrNull()

    fun setBaselineHrv(hrv: Double?) = prefs.edit().apply {
        if (hrv == null) remove("baseline_hrv") else putString("baseline_hrv", hrv.toString())
    }.apply()

    fun isMetricVisible(key: String): Boolean = prefs.getBoolean(key, true)

    fun setMetricVisible(key: String, visible: Boolean) =
        prefs.edit().putBoolean(key, visible).apply()

    companion object {
        const val KEY_SHOW_HR  = "show_hr"
        const val KEY_SHOW_HRV = "show_hrv"
        const val KEY_SHOW_BD  = "show_bd"
        const val KEY_SHOW_BR  = "show_br"
        const val KEY_SHOW_BL  = "show_bl"
    }
}
