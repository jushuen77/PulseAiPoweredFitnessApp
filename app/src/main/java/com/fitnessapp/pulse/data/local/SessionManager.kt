package com.fitnessapp.pulse.data.local

import android.content.Context

/**
 * Tracks the signed-in user against our own `app_users` table instead of Supabase Auth.
 * [userId]/[username] are cached in memory for the life of the process; call [restore] once
 * at app start to repopulate them from disk (if the user chose to be remembered at login).
 */
object SessionManager {
    private const val PREFS_NAME = "session_prefs"
    private const val KEY_UID = "uid"
    private const val KEY_USERNAME = "username"

    var userId: String? = null
        private set
    var username: String? = null
        private set

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        userId = prefs.getString(KEY_UID, null)
        username = prefs.getString(KEY_USERNAME, null)
    }

    /** Signs [uid]/[username] in for this process, persisting them to disk only if [remember] is true. */
    fun login(context: Context, uid: String, username: String, remember: Boolean) {
        this.userId = uid
        this.username = username
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (remember) {
            editor.putString(KEY_UID, uid).putString(KEY_USERNAME, username)
        } else {
            editor.remove(KEY_UID).remove(KEY_USERNAME)
        }
        editor.apply()
    }

    fun logout(context: Context) {
        userId = null
        username = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
