package com.Anchor.watchguardian.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper around SharedPreferences for auth state.
 *
 * Mirrors UserSession.ets (HarmonyOS) which used AppStorage — a global key-value
 * store available anywhere in the app. On Android, SharedPreferences serves the same
 * role but requires a Context. We use a singleton prefs name to keep it consistent.
 */
object UserSession {

    private const val PREFS_NAME = "anchor_session"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean("isLoggedIn", false)

    fun getOpenID(context: Context): String =
        prefs(context).getString("userOpenID", "") ?: ""

    fun getUnionID(context: Context): String =
        prefs(context).getString("userUnionID", "") ?: ""

    fun getIDToken(context: Context): String =
        prefs(context).getString("userIDToken", "") ?: ""

    /** Called after a successful HMS Account sign-in. */
    fun login(context: Context, openID: String, unionID: String, idToken: String) {
        prefs(context).edit()
            .putBoolean("isLoggedIn", true)
            .putString("userOpenID",  openID)
            .putString("userUnionID", unionID)
            .putString("userIDToken", idToken)
            .apply()
    }

    fun logout(context: Context) {
        prefs(context).edit()
            .putBoolean("isLoggedIn", false)
            .putString("userOpenID",  "")
            .putString("userUnionID", "")
            .putString("userIDToken", "")
            .apply()
    }
}
