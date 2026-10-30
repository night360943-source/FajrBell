package com.example.fajrbell

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
}
