package com.example.fajrbell

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.util.Calendar
import java.util.Date

object FajrScheduler {

    // ===== عدّل مكانك هنا =====
    const val LATITUDE = 30.0444
    const val LONGITUDE = 31.2357
    // ==========================

    class Next(val fajr: Date, val ring: Date)

    /** أقرب رنة جاية: وقت الفجر المحسوب + وقت الرنين بعد التعديل */
    fun next(context: Context): Next {
        val coords = Coordinates(LATITUDE, LONGITUDE)
        val params = CalculationMethod.EGYPTIAN.parameters
        val offsetMs = Prefs.offset(context) * 60_000L
        for (dayOffset in 0..3) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
            val fajr = PrayerTimes(coords, DateComponents.from(cal.time), params).fajr
            val ring = fajr.time + offsetMs
            if (ring > System.currentTimeMillis()) return Next(fajr, Date(ring))
        }
        error("no upcoming fajr found")
    }

    fun canSchedule(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** بيرجع true لو اتجدول بنجاح، false لو الإذن ناقص (من غير ما التطبيق يقفل) */
    fun schedule(context: Context): Boolean {
        if (!canSchedule(context)) return false
        return try {
            val am = context.getSystemService(AlarmManager::class.java)
            val trigger = next(context).ring.time

            val ringIntent = PendingIntent.getBroadcast(
                context, 1, Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val showIntent = PendingIntent.getActivity(
                context, 2, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, showIntent), ringIntent)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val ringIntent = PendingIntent.getBroadcast(
            context, 1, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(ringIntent)
    }
}
