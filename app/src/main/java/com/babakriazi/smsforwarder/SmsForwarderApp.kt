package com.babakriazi.smsforwarder

import android.app.Application

class SmsForwarderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLog.e(
                    applicationContext,
                    "CRASH on thread=${thread.name}: ${throwable.stackTraceToString().take(1500)}",
                    throwable
                )
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
        AppLog.i(this, "App.onCreate pid=${android.os.Process.myPid()}")
    }
}
