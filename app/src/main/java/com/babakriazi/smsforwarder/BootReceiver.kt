package com.babakriazi.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("SmsForwarder", "Boot completed - receiver ready")
            // Receiver is already registered in manifest, nothing else needed
        }
    }
}
