package com.babakriazi.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("SmsForwarder", "Boot completed - starting KeepAliveService")
            try {
                KeepAliveService.start(context)
            } catch (e: Exception) {
                Log.e("SmsForwarder", "Failed to start service on boot", e)
            }
        }
    }
}
