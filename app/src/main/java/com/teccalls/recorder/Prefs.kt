package com.teccalls.recorder

import android.content.Context

/** Stores the one-time choice of where recordings should go. */
object Prefs {
    const val DEST_PHONE = "phone"
    const val DEST_EMAIL = "email"

    private fun p(c: Context) = c.getSharedPreferences("teccalls", Context.MODE_PRIVATE)

    fun isConfigured(c: Context) = p(c).getBoolean("configured", false)

    fun autoRecordCalls(c: Context) = p(c).getBoolean("autoRecordCalls", false)
    fun setAutoRecordCalls(c: Context, v: Boolean) =
        p(c).edit().putBoolean("autoRecordCalls", v).apply()

    fun destination(c: Context) = p(c).getString("dest", DEST_PHONE) ?: DEST_PHONE
    fun senderEmail(c: Context) = p(c).getString("sender", "") ?: ""
    fun appPassword(c: Context) = p(c).getString("apppw", "") ?: ""
    fun recipient(c: Context) = p(c).getString("recipient", "") ?: ""

    fun save(c: Context, dest: String, sender: String, pw: String, recipient: String) {
        p(c).edit()
            .putString("dest", dest)
            .putString("sender", sender)
            .putString("apppw", pw)
            .putString("recipient", recipient)
            .putBoolean("configured", true)
            .apply()
    }

    fun emailConfigured(c: Context) =
        destination(c) == DEST_EMAIL &&
            senderEmail(c).isNotBlank() &&
            appPassword(c).isNotBlank() &&
            recipient(c).isNotBlank()

    fun summary(c: Context): String =
        if (destination(c) == DEST_EMAIL) "Emailing recordings to ${recipient(c)}, then freeing phone space"
        else "Keeping recordings on this phone"
}
