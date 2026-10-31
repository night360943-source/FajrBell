package com.example.fajrbell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val svc = Intent(context, RingService::class.java)
            .putExtra("key", intent.getStringExtra("key"))
        ContextCompat.startForegroundService(context, svc)
        FajrScheduler.schedule(context) // جدول الجرس اللي بعده
        PrayerWidgetProvider.refreshAll(context)
        if (PrayerWidgetProvider.hasWidgets(context)) PrayerWidgetProvider.scheduleTick(context)
    }
}
