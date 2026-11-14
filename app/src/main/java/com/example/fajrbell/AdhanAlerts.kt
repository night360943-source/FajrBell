package com.example.fajrbell

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** إشعار "الأذان وقف" + مراقب بيكتشف لو الأذان وقف فجأة */
object AdhanAlerts {

    private const val CHANNEL = "adhan_stopped"
    private const val NOTIF_ID = 50

    fun notifyStopped(c: Context, prayerName: String?, reason: String) {
        try {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "تنبيه توقف الأذان", NotificationManager.IMPORTANCE_HIGH)
            )
            val open = PendingIntent.getActivity(
                c, 9, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val title = if (prayerName != null) "⚠️ أذان $prayerName وقف" else "⚠️ الأذان وقف"
            val body = reason + "\nافتح التطبيق وراجع إعدادات البطارية والتشغيل التلقائي"
            val n = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            nm.notify(NOTIF_ID, n)
        } catch (e: Exception) { }
    }

    private fun watchdogIntent(c: Context, session: Long, name: String?): PendingIntent {
        val i = Intent(c, RingWatchdogReceiver::class.java)
        i.putExtra("session", session)
        if (name != null) i.putExtra("name", name)
        return PendingIntent.getBroadcast(
            c, 31, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** منبه بيشتغل بعد انتهاء مدة الأذان المتوقعة: لو الجلسة لسه مفتوحة يبقى الأذان وقف فجأة */
    fun scheduleWatchdog(c: Context, session: Long, name: String?, afterMs: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + afterMs
        val pi = watchdogIntent(c, session, name)
        try {
            if (FajrScheduler.canSchedule(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancelWatchdog(c: Context) {
        try {
            c.getSystemService(AlarmManager::class.java).cancel(watchdogIntent(c, 0L, null))
        } catch (e: Exception) { }
    }
}
