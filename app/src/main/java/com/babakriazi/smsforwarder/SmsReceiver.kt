package com.babakriazi.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppLog.i(context, "SmsReceiver.onReceive action=${intent.action}")
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            AppLog.w(context, "Ignored non-SMS action")
            return
        }

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                AppLog.w(context, "getMessagesFromIntent returned empty")
                return
            }

            val sender = messages[0].displayOriginatingAddress ?: run {
                AppLog.w(context, "sender is null")
                return
            }
            val body = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }
            AppLog.i(context, "SMS received from='$sender' bodyLen=${body.length} bodyPreview='${body.take(80)}'")

            val repo = RuleRepository(context)
            val all = repo.getAllRules()
            val rules = all.filter { it.enabled && it.forwardTo.isNotBlank() }
            AppLog.i(context, "rules total=${all.size} active=${rules.size}")

            if (rules.isEmpty()) {
                AppLog.w(context, "No active rules with forwardTo — nothing to send")
                return
            }

            var matched = 0
            for (rule in rules) {
                val ok = rule.matches(sender, body)
                AppLog.i(
                    context,
                    "rule '${rule.name.ifBlank { rule.id.take(8) }}' match=$ok " +
                        "senderFilter='${rule.senderFilter}' body=${rule.bodySummary()}"
                )
                if (ok) {
                    matched++
                    forwardSms(context, rule.forwardTo, sender, body, rule.simSlot)
                }
            }
            if (matched == 0) {
                AppLog.w(context, "SMS did not match any rule")
            } else {
                AppLog.i(context, "Matched $matched rule(s), forward attempted")
            }
        } catch (e: Exception) {
            AppLog.e(context, "Error in SmsReceiver", e)
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
            AppLog.i(context, "Sending to='$to' simSlot=$simSlot parts=${parts.size} msgLen=${message.length}")
            if (parts.size == 1) {
                smsManager.sendTextMessage(to, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            }
            AppLog.i(context, "sendTextMessage called OK → $to")
        } catch (e: Exception) {
            AppLog.e(context, "Failed to forward SMS to $to", e)
        }
    }

    private fun getSmsManagerForSlot(context: Context, simSlot: Int): SmsManager {
        if (simSlot < 0) {
            AppLog.i(context, "Using default SmsManager")
            return defaultSmsManager(context)
        }
        try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subList = subscriptionManager.activeSubscriptionInfoList
            AppLog.i(context, "active SIMs=${subList?.size ?: 0} requestedSlot=$simSlot")
            if (subList != null && subList.size > simSlot) {
                val subId = subList[simSlot].subscriptionId
                AppLog.i(context, "SmsManager for subId=$subId")
                return SmsManager.getSmsManagerForSubscriptionId(subId)
            }
        } catch (e: Exception) {
            AppLog.e(context, "Failed to get SIM $simSlot, fallback default", e)
        }
        return defaultSmsManager(context)
    }

    private fun defaultSmsManager(context: Context): SmsManager {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
}
