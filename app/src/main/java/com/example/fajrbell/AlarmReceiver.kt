package com.example.fajrbell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val at = intent.getLongExtra("at", 0L)

        // منع التكرار: نفس ميعاد الأذان ما يشتغلش مرتين
        val duplicate = at != 0L && Prefs.lastRingAt(context) >= at
        if (!duplicate) {
            if (at != 0L) Prefs.setLastRingAt(context, at)
            val svc = Intent(context, RingService::class.java)
                .putExtra("key", intent.getStringExtra("key"))
            ContextCompat.startForegroundService(context, svc)
        }

        // جدول اللي بعده، ومن غير ما نرجع لنفس الميعاد حتى لو المنبه جه بدري ثواني
        FajrScheduler.schedule(context, if (at != 0L) at + 1000L else 0L)
        PrayerWidgetProvider.refreshAll(context)
        if (PrayerWidgetProvider.hasWidgets(context)) PrayerWidgetProvider.scheduleTick(context)
    }
}
