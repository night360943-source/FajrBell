package com.example.fajrbell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("fajr", Context.MODE_PRIVATE)
        if (prefs.getBoolean("enabled", false)) FajrScheduler.schedule(context)
    }
}
