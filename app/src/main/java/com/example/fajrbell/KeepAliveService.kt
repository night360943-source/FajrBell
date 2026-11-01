package com.example.fajrbell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * خدمة بتفضل شغالة مع إشعار ثابت (بيعرض الوقت المتبقي للصلاة القادمة).
 * وجودها بيمنع تليفونات زي Xiaomi من قفل التطبيق، وبتحدّث الودجت كل دقيقة
 * وعند فتح الشاشة.
 */
class KeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null

    private val minuteTick = object : Runnable {
        override fun run() {
            refreshAll()
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 60_000L - (now % 60_000L) + 200L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "التشغيل المستمر", NotificationManager.IMPORTANCE_LOW)
            )
            val n = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        registerReceivers()
        handler.removeCallbacks(minuteTick)
        handler.post(minuteTick)

        // اتأكد إن منبه الجرس والودجت متجدولين
        if (Prefs.enabled(this)) FajrScheduler.schedule(this)
        if (PrayerWidgetProvider.hasWidgets(this)) PrayerWidgetProvider.scheduleTick(this)
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val now = System.currentTimeMillis()
        val next = FajrScheduler.window(now).second
        val open = PendingIntent.getActivity(
            this, 6, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("باقي على صلاة " + next.prayer.ar + "  " + Fmt.hm(next.time.time - now))
            .setContentText("الأذان الساعة " + Fmt.time(next.time))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun refreshAll() {
        try {
            PrayerWidgetProvider.refreshAll(this)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            // مش مشكلة
        }
    }

    private fun registerReceivers() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val a = intent.action
                if (a == Intent.ACTION_TIME_CHANGED || a == Intent.ACTION_TIMEZONE_CHANGED) {
                    if (Prefs.enabled(context)) FajrScheduler.schedule(context)
                }
                refreshAll()
                if (PrayerWidgetProvider.hasWidgets(context)) PrayerWidgetProvider.scheduleTick(context)
            }
        }
        val f = IntentFilter()
        f.addAction(Intent.ACTION_TIME_TICK)
        f.addAction(Intent.ACTION_SCREEN_ON)
        f.addAction(Intent.ACTION_USER_PRESENT)
        f.addAction(Intent.ACTION_TIME_CHANGED)
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        ContextCompat.registerReceiver(this, r, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            receiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) { }
        receiver = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "keep_alive"
        private const val NOTIF_ID = 11

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
            } catch (e: Exception) {
                // ممكن النظام يمنع البدء من الخلفية، هيشتغل أول ما التطبيق يتفتح
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (e: Exception) { }
        }
    }
}
