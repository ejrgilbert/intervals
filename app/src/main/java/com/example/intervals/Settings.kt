package com.example.intervalrunner

import android.content.Context

object Settings {
    private const val PREFS = "intervals_settings"
    private const val KEY_WARN_HALFWAY = "warn_halfway"

    fun isWarnHalfwayEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WARN_HALFWAY, false)

    fun setWarnHalfwayEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WARN_HALFWAY, enabled)
            .apply()
    }
}
