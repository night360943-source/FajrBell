package com.example.fajrbell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val svc = Intent(context, RingService::class.java)
            .putExtra("prayer", intent.getStringExtra("prayer"))
        ContextCompat.startForegroundService(context, svc)
        FajrScheduler.schedule(context) // جدول الصلاة اللي بعدها
    }
}
