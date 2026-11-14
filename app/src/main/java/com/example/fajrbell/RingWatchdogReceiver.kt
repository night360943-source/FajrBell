package com.example.fajrbell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** لو الأذان وقف فجأة (الخدمة اتقفلت) هنا بنكتشف ده ونطلّع إشعار */
class RingWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = intent.getLongExtra("session", 0L)
        if (session != 0L && Prefs.ringSession(context) == session) {
            Prefs.setRingSession(context, 0L)
            AdhanAlerts.notifyStopped(
                context, intent.getStringExtra("name"),
                "الأذان وقف قبل ما يكتمل (غالباً النظام قفل التطبيق)"
            )
        }
    }
}
