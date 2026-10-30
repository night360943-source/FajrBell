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

enum class Prayer(val ar: String) {
    FAJR("الفجر"),
    DHUHR("الظهر"),
    ASR("العصر"),
    MAGHRIB("المغرب"),
    ISHA("العشاء")
}

object FajrScheduler {

    // ===== عدّل مكانك هنا =====
    const val LATITUDE = 30.0444
    const val LONGITUDE = 31.2357
    // ==========================

    /** موعد صلاة (أذان) */
    class Slot(val prayer: Prayer, val time: Date)

    /** رنة جاية: الصلاة + وقت الأذان + وقت الرنين بعد التعديل */
    class Next(val prayer: Prayer, val time: Date, val ring: Date)

    /** مواقيت الصلوات الخمس بطريقة الهيئة المصرية ليوم معين (0 = النهارده) */
    fun timesFor(dayOffset: Int): Map<Prayer, Date> {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
        val t = PrayerTimes(
            Coordinates(LATITUDE, LONGITUDE),
            DateComponents.from(cal.time),
            CalculationMethod.EGYPTIAN.parameters
        )
        return linkedMapOf(
            Prayer.FAJR to t.fajr,
            Prayer.DHUHR to t.dhuhr,
            Prayer.ASR to t.asr,
            Prayer.MAGHRIB to t.maghrib,
            Prayer.ISHA to t.isha
        )
    }

    private fun slots(): List<Slot> {
        val all = ArrayList<Slot>()
        for (d in -1..1) {
            for ((p, t) in timesFor(d)) all.add(Slot(p, t))
        }
        return all.sortedBy { it.time.time }
    }

    /** الصلاة السابقة والصلاة القادمة (بغض النظر عن الجرس) */
    fun window(now: Long = System.currentTimeMillis()): Pair<Slot, Slot> {
        val all = slots()
        val next = all.first { it.time.time > now }
        val prev = all.last { it.time.time <= now }
        return Pair(prev, next)
    }

    /** المواقيت اللي بتتعرض: النهارده، وبعد العشاء بتتحول لمواقيت بكرة */
    fun displayTimes(now: Long = System.currentTimeMillis()): Map<Prayer, Date> {
        val today = timesFor(0)
        return if (now >= today.getValue(Prayer.ISHA).time) timesFor(1) else today
    }

    /** أقرب رنة جاية بين الصلوات المفعّلة، أو null لو مفيش صلاة مفعّلة */
    fun next(context: Context): Next? {
        val now = System.currentTimeMillis()
        var best: Next? = null
        for (day in 0..3) {
            for ((prayer, time) in timesFor(day)) {
                if (!Prefs.prayerEnabled(context, prayer)) continue
                val ring = time.time + Prefs.offset(context, prayer) * 60_000L
                val b = best
                if (ring > now && (b == null || ring < b.ring.time)) {
                    best = Next(prayer, time, Date(ring))
                }
            }
        }
        return best
    }

    fun canSchedule(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun ringPendingIntent(context: Context, prayer: Prayer?): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java)
        if (prayer != null) i.putExtra("key", prayer.name)
        return PendingIntent.getBroadcast(
            context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** بيرجع true لو اتجدول بنجاح، false لو الإذن ناقص (من غير ما التطبيق يقفل) */
    fun schedule(context: Context): Boolean {
        if (!canSchedule(context)) return false
        val n = next(context)
        if (n == null) {
            cancel(context)
            return true
        }
        return try {
            val am = context.getSystemService(AlarmManager::class.java)
            val showIntent = PendingIntent.getActivity(
                context, 2, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(n.ring.time, showIntent),
                ringPendingIntent(context, n.prayer)
            )
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(ringPendingIntent(context, null))
    }
}
