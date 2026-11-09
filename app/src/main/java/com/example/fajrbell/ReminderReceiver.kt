package com.example.fajrbell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** بيشتغل قبل الصلاة بالمدة المحددة، وبيشغّل التنبيه الصوتي "اقتربت صلاة ..." */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val svc = Intent(context, ReminderService::class.java)
            .putExtra("key", intent.getStringExtra("key"))
            .putExtra("minutes", intent.getIntExtra("minutes", 15))
            .putExtra("adhan", intent.getLongExtra("adhan", 0L))
        ContextCompat.startForegroundService(context, svc)
        FajrScheduler.schedule(context) // جدول التنبيه والجرس اللي بعده
    }
}
