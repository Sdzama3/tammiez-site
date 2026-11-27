package com.teccalls.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager

/**
 * Listens for phone-call state changes. When a call connects it starts the
 * recording service; when the call ends it tells the service to stop and send.
 * Only acts if the user turned on "Automatically record calls" in Settings.
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (!Prefs.autoRecordCalls(context)) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val svc = Intent(context, CallRecordingService::class.java)

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                svc.action = CallRecordingService.ACTION_START
                startService(context, svc)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                svc.action = CallRecordingService.ACTION_STOP
                startService(context, svc)
            }
        }
    }

    private fun startService(context: Context, svc: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        } catch (e: Exception) {
            // Some OS versions block starting a service from the background.
        }
    }
}
