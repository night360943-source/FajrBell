package com.example.fajrbell

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.util.Calendar
import java.util.Date

object FajrScheduler {

    // ===== عدّل الإعدادات دي =====
    const val LATITUDE = 30.0444      // خط العرض (القاهرة/الجيزة تقريباً)
    const val LONGITUDE = 31.2357     // خط الطول
    const val MINUTES_BEFORE = 0      // رنّ قبل الفجر بكام دقيقة (0 = وقت الفجر بالظبط)
    // ==============================

    /** أقرب فجر جاي (النهارده لو لسه ماجاش، وإلا بكرة) بطريقة الهيئة المصرية */
    fun nextFajr(): Date {
        val coords = Coordinates(LATITUDE, LONGITUDE)
        val params = CalculationMethod.EGYPTIAN.parameters
        for (dayOffset in 0..2) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
            val times = PrayerTimes(coords, DateComponents.from(cal.time), params)
            val ringAt = times.fajr.time - MINUTES_BEFORE * 60_000L
            if (ringAt > System.currentTimeMillis()) return Date(ringAt)
        }
        error("no upcoming fajr found")
    }

    /** بيجدول المنبه الجاي. بيتنادى كل ما المنبه يرن وبعد إعادة التشغيل */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val trigger = nextFajr().time

        val ringIntent = PendingIntent.getBroadcast(
            context, 1, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        // setAlarmClock: أدق نوع منبه، بيشتغل حتى في Doze وما بيحتاش إذن Exact Alarm
        am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, showIntent), ringIntent)
    }
}
