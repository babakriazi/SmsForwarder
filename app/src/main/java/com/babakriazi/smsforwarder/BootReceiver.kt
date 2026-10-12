package com.babakriazi.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.i(context, "BootReceiver action=${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            try {
                KeepAliveService.start(context)
                AppLog.i(context, "Boot: KeepAliveService started")
            } catch (e: Exception) {
                AppLog.e(context, "Boot: failed to start service", e)
            }
        }
    }
}
