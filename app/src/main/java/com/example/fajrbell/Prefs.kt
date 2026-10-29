package com.example.fajrbell

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("fajr", Context.MODE_PRIVATE)

    /** التشغيل العام للجرس */
    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    /** تفعيل/إيقاف كل صلاة لوحدها (الكل شغال افتراضياً) */
    fun prayerEnabled(c: Context, p: Prayer) = p(c).getBoolean("pray_" + p.name, true)
    fun setPrayerEnabled(c: Context, p: Prayer, v: Boolean) =
        p(c).edit().putBoolean("pray_" + p.name, v).apply()

    /** دقايق قبل (سالب) أو بعد (موجب) الأذان */
    fun offset(c: Context) = p(c).getInt("offset", 0)
    fun setOffset(c: Context, v: Int) = p(c).edit().putInt("offset", v).apply()

    /** مدة الرنين بالثواني */
    fun duration(c: Context) = p(c).getInt("duration", 60)
    fun setDuration(c: Context, v: Int) = p(c).edit().putInt("duration", v).apply()

    /** رابط النغمة المختارة، أو null = الافتراضية */
    fun sound(c: Context): String? = p(c).getString("sound", null)
    fun setSound(c: Context, v: String?) = p(c).edit().putString("sound", v).apply()
}
