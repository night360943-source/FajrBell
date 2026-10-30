package com.example.fajrbell

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Fmt {
    /** مثال: 4:52 ص */
    fun time(d: Date): String {
        val c = Calendar.getInstance()
        c.time = d
        var h = c.get(Calendar.HOUR)
        if (h == 0) h = 12
        val ap = if (c.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"
        return String.format(Locale.US, "%d:%02d %s", h, c.get(Calendar.MINUTE), ap)
    }

    /** ساعة:دقيقة (بالتقريب لأعلى) */
    fun hm(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) + 59_999L) / 60_000L
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
    }

    /** ساعة:دقيقة:ثانية */
    fun hms(ms: Long): String {
        val s = ms.coerceAtLeast(0L) / 1000L
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    /** الساعة بس بدون ص/م. مثال: 4:52 */
    fun clock(d: Date): String {
        val c = Calendar.getInstance()
        c.time = d
        var h = c.get(Calendar.HOUR)
        if (h == 0) h = 12
        return String.format(Locale.US, "%d:%02d", h, c.get(Calendar.MINUTE))
    }

    /** ص أو م */
    fun ampm(d: Date): String {
        val c = Calendar.getInstance()
        c.time = d
        return if (c.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"
    }

    private val HIJRI_MONTHS = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )
    private val GREG_MONTHS = arrayOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    /** مثال: 19 جمادى الأولى 1448 (تقويم أم القرى + تعديل بالأيام) */
    fun hijri(adjustDays: Int): String {
        return try {
            val d = HijrahDate.from(LocalDate.now().plusDays(adjustDays.toLong()))
            String.format(
                Locale.US, "%d %s %d",
                d.get(ChronoField.DAY_OF_MONTH),
                HIJRI_MONTHS[d.get(ChronoField.MONTH_OF_YEAR) - 1],
                d.get(ChronoField.YEAR)
            )
        } catch (e: Exception) {
            ""
        }
    }

    /** مثال: 30 أكتوبر 2026 */
    fun gregorian(): String {
        val c = Calendar.getInstance()
        return String.format(
            Locale.US, "%d %s %d",
            c.get(Calendar.DAY_OF_MONTH), GREG_MONTHS[c.get(Calendar.MONTH)], c.get(Calendar.YEAR)
        )
    }
}
