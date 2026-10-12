package com.babakriazi.smsforwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {

    companion object {
        const val CHANNEL_ID = "sms_forwarder_keepalive"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            AppLog.i(context, "KeepAliveService.start requested")
            val intent = Intent(context, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                AppLog.e(context, "KeepAliveService.start failed", e)
            }
        }

        fun stop(context: Context) {
            AppLog.i(context, "KeepAliveService.stop requested")
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLog.i(this, "KeepAliveService.onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        AppLog.i(this, "KeepAliveService foreground started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLog.i(this, "KeepAliveService.onStartCommand flags=$flags startId=$startId")
        return START_STICKY
    }

    override fun onDestroy() {
        AppLog.w(this, "KeepAliveService.onDestroy — service stopped by system or user")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        AppLog.w(this, "KeepAliveService.onTaskRemoved — app swiped away")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "سرویس فوروارد پیامک",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "برای جلوگیری از بسته شدن برنامه در پس‌زمینه"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("فوروارد پیامک فعال است")
            .setContentText("در حال گوش دادن به پیامک‌های جدید")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
