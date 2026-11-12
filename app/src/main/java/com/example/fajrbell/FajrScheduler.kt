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
import java.util.Locale

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

    /** تنبيه "اقتربت صلاة ...": الصلاة + وقت أذانها + وقت التنبيه + قبلها بكام دقيقة */
    class NextReminder(val prayer: Prayer, val time: Date, val at: Date, val minutes: Int)

    private fun inEgypt(lat: Double, lon: Double): Boolean =
        lat in 21.9..31.8 && lon in 24.6..36.95

    /** طريقة حساب المواقيت حسب الدولة (مصر: الهيئة المصرية العامة للمساحة) */
    fun method(c: Context): CalculationMethod {
        return when (Prefs.country(c)?.uppercase(Locale.US)) {
            "EG" -> CalculationMethod.EGYPTIAN
            "SA" -> CalculationMethod.UMM_AL_QURA
            "AE" -> CalculationMethod.DUBAI
            "KW" -> CalculationMethod.KUWAIT
            "QA" -> CalculationMethod.QATAR
            null, "" ->
                if (inEgypt(Prefs.locLat(c, LATITUDE), Prefs.locLon(c, LONGITUDE))) {
                    CalculationMethod.EGYPTIAN
                } else {
                    CalculationMethod.MUSLIM_WORLD_LEAGUE
                }
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        }
    }

    fun methodName(c: Context): String = when (method(c)) {
        CalculationMethod.EGYPTIAN -> "الهيئة المصرية العامة للمساحة"
        CalculationMethod.UMM_AL_QURA -> "أم القرى"
        CalculationMethod.DUBAI -> "دبي"
        CalculationMethod.KUWAIT -> "الكويت"
        CalculationMethod.QATAR -> "قطر"
        else -> "رابطة العالم الإسلامي"
    }

    /** مواقيت الصلوات الخمس على الموقع المحفوظ ليوم معين (0 = النهارده) */
    fun timesFor(dayOffset: Int): Map<Prayer, Date> {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
        val app = FajrApp.instance
        val t = PrayerTimes(
            Coordinates(Prefs.locLat(app, LATITUDE), Prefs.locLon(app, LONGITUDE)),
            DateComponents.from(cal.time),
            method(app).parameters
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
    fun next(context: Context, after: Long = 0L): Next? {
        val now = maxOf(System.currentTimeMillis(), after)
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

    private fun ringPendingIntent(context: Context, prayer: Prayer?, at: Long = 0L): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java)
        if (prayer != null) {
            i.putExtra("key", prayer.name)
            i.putExtra("at", at)
        }
        return PendingIntent.getBroadcast(
            context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reminderPendingIntent(context: Context, r: NextReminder?): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java)
        if (r != null) {
            i.putExtra("key", r.prayer.name)
            i.putExtra("minutes", r.minutes)
            i.putExtra("adhan", r.time.time)
            i.putExtra("at", r.at.time)
        }
        return PendingIntent.getBroadcast(
            context, 8, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** أقرب تنبيه "اقتربت صلاة ..." جاي بين الصلوات اللي تنبيهها مفعّل */
    fun nextReminder(context: Context, after: Long = 0L): NextReminder? {
        val now = maxOf(System.currentTimeMillis(), after)
        var best: NextReminder? = null
        for (day in 0..3) {
            for ((prayer, time) in timesFor(day)) {
                if (!Prefs.remindEnabled(context, prayer)) continue
                val mins = Prefs.remindMinutes(context, prayer)
                val at = time.time - mins * 60_000L
                val b = best
                if (at > now && (b == null || at < b.at.time)) {
                    best = NextReminder(prayer, time, Date(at), mins)
                }
            }
        }
        return best
    }

    /** بيرجع true لو اتجدول بنجاح، false لو الإذن ناقص (من غير ما التطبيق يقفل) */
    fun schedule(context: Context, after: Long = 0L): Boolean {
        if (!canSchedule(context)) return false
        return try {
            val am = context.getSystemService(AlarmManager::class.java)
            val showIntent = PendingIntent.getActivity(
                context, 2, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )

            // جرس الأذان
            val n = next(context, after)
            if (n != null) {
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(n.ring.time, showIntent),
                    ringPendingIntent(context, n.prayer, n.ring.time)
                )
            } else {
                am.cancel(ringPendingIntent(context, null))
            }

            // تنبيه "اقتربت صلاة ..."
            val r = nextReminder(context, after)
            if (r != null) {
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(r.at.time, showIntent),
                    reminderPendingIntent(context, r)
                )
            } else {
                am.cancel(reminderPendingIntent(context, null))
            }
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(ringPendingIntent(context, null))
        am.cancel(reminderPendingIntent(context, null))
    }
}
