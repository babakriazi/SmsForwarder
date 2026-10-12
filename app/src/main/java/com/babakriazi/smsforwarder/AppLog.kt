package com.babakriazi.smsforwarder

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent in-app log so the user can Copy and send it for debugging.
 * Keeps last ~400 lines in SharedPreferences.
 */
object AppLog {

    private const val PREFS = "sms_forwarder_debug_log"
    private const val KEY = "lines"
    private const val MAX_LINES = 400
    private const val TAG = "SmsForwarder"

    private val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun i(context: Context, msg: String) {
        write(context, "I", msg)
        Log.i(TAG, msg)
    }

    @Synchronized
    fun w(context: Context, msg: String) {
        write(context, "W", msg)
        Log.w(TAG, msg)
    }

    @Synchronized
    fun e(context: Context, msg: String, t: Throwable? = null) {
        val full = if (t != null) "$msg | ${t.javaClass.simpleName}: ${t.message}" else msg
        write(context, "E", full)
        if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
    }

    @Synchronized
    fun dumpSettings(context: Context) {
        try {
            val repo = RuleRepository(context)
            val rules = repo.getAllRules()
            i(context, "=== SETTINGS DUMP ===")
            i(context, "rules.count=${rules.size}")
            rules.forEachIndexed { idx, r ->
                i(
                    context,
                    "rule[$idx] id=${r.id.take(8)} name='${r.name}' enabled=${r.enabled} " +
                        "sender='${r.senderFilter}'(${r.senderMatchType}) " +
                        "body=${r.bodySummary()} logic=${r.logic} " +
                        "to='${r.forwardTo}' sim=${r.simSlot}"
                )
            }
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val ignoring = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else true
            i(context, "battery.ignoreOptimizations=$ignoring")
            i(context, "sdk=${android.os.Build.VERSION.SDK_INT} model=${android.os.Build.MODEL}")
            i(context, "=== END SETTINGS ===")
        } catch (t: Throwable) {
            e(context, "dumpSettings failed", t)
        }
    }

    @Synchronized
    fun getAll(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY, "") ?: ""
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, "").apply()
    }

    private fun write(context: Context, level: String, msg: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val prev = prefs.getString(KEY, "") ?: ""
            val line = "${sdf.format(Date())} $level  $msg"
            val combined = if (prev.isBlank()) line else prev + "\n" + line
            val lines = combined.split('\n')
            val trimmed = if (lines.size > MAX_LINES) {
                lines.takeLast(MAX_LINES).joinToString("\n")
            } else combined
            prefs.edit().putString(KEY, trimmed).apply()
        } catch (_: Exception) {
            // never crash on logging
        }
    }
}
