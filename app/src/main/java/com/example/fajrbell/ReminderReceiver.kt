package com.example.fajrbell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** بيشتغل قبل الصلاة بالمدة المحددة، وبينطق "اقتربت صلاة ..." (صوت بس) */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val at = intent.getLongExtra("at", 0L)

        // منع التكرار: نفس التنبيه ما ينطقش مرتين
        val duplicate = at != 0L && Prefs.lastRemindAt(context) >= at
        if (!duplicate) {
            if (at != 0L) Prefs.setLastRemindAt(context, at)
            val svc = Intent(context, ReminderService::class.java)
                .putExtra("key", intent.getStringExtra("key"))
                .putExtra("minutes", intent.getIntExtra("minutes", 15))
                .putExtra("adhan", intent.getLongExtra("adhan", 0L))
            ContextCompat.startForegroundService(context, svc)
        }

        FajrScheduler.schedule(context, if (at != 0L) at + 1000L else 0L)
    }
}
