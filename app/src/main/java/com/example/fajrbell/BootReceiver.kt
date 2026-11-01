package com.example.fajrbell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.enabled(context)) FajrScheduler.schedule(context)
        if (Prefs.keepAlive(context)) KeepAliveService.start(context)
        if (PrayerWidgetProvider.refreshAll(context)) PrayerWidgetProvider.scheduleTick(context)
    }
}
