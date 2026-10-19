package com.fitnessapp.pulse.data.local

import android.content.Context

object AuthPreferences {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_REMEMBER_ME = "remember_me"

    fun setRememberMe(context: Context, remember: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REMEMBER_ME, remember)
            .apply()
    }

    fun getRememberMe(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMEMBER_ME, true) // default: remembered
    }
}