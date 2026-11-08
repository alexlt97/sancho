package com.unplugged.app

import android.content.Context

class ChatSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun metricsEnabled(): Boolean = preferences.getBoolean(METRICS_ENABLED_KEY, true)

    fun setMetricsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(METRICS_ENABLED_KEY, enabled).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "sancho_chat_settings"
        const val METRICS_ENABLED_KEY = "metrics_enabled"
    }
}
