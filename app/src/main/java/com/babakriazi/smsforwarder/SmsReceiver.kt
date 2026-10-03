package com.babakriazi.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            // Combine multi-part SMS
            val sender = messages[0].displayOriginatingAddress ?: return
            val body = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }

            Log.d("SmsForwarder", "Received SMS from $sender: $body")

            val repo = RuleRepository(context)
            val rules = repo.getAllRules().filter { it.enabled && it.forwardTo.isNotBlank() }

            for (rule in rules) {
                if (rule.matches(sender, body)) {
                    forwardSms(context, rule.forwardTo, sender, body)
                }
            }
        } catch (e: Exception) {
            Log.e("SmsForwarder", "Error in SmsReceiver", e)
        }
    }

    private fun forwardSms(context: Context, to: String, originalSender: String, body: String) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val message = "از: $originalSender\n\n$body"

            // Split if too long
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(to, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            }

            Log.d("SmsForwarder", "Forwarded to $to")
        } catch (e: Exception) {
            Log.e("SmsForwarder", "Failed to forward SMS", e)
        }
    }
}
