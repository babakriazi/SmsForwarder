package com.babakriazi.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val sender = messages[0].displayOriginatingAddress ?: return
            val body = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }

            Log.d("SmsForwarder", "Received SMS from $sender: $body")

            val repo = RuleRepository(context)
            val rules = repo.getAllRules().filter { it.enabled && it.forwardTo.isNotBlank() }

            for (rule in rules) {
                if (rule.matches(sender, body)) {
                    forwardSms(context, rule.forwardTo, sender, body, rule.simSlot)
                }
            }
        } catch (e: Exception) {
            Log.e("SmsForwarder", "Error in SmsReceiver", e)
        }
    }

    private fun forwardSms(
        context: Context,
        to: String,
        originalSender: String,
        body: String,
        simSlot: Int
    ) {
        try {
            val smsManager = getSmsManagerForSlot(context, simSlot)

            val message = "از: $originalSender\n\n$body"

            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(to, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            }

            Log.d("SmsForwarder", "Forwarded to $to using SIM slot $simSlot")
        } catch (e: Exception) {
            Log.e("SmsForwarder", "Failed to forward SMS", e)
        }
    }

    private fun getSmsManagerForSlot(context: Context, simSlot: Int): SmsManager {
        // simSlot: -1 = default, 0 = first SIM, 1 = second SIM
        if (simSlot < 0) {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }

        try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            val subList = subscriptionManager.activeSubscriptionInfoList
            if (subList != null && subList.size > simSlot) {
                val subId = subList[simSlot].subscriptionId
                return SmsManager.getSmsManagerForSubscriptionId(subId)
            }
        } catch (e: Exception) {
            Log.e("SmsForwarder", "Failed to get SIM $simSlot, falling back to default", e)
        }

        // fallback
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
}
