package com.example.fajrbell

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("fajr", Context.MODE_PRIVATE)

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    /** دقايق قبل (سالب) أو بعد (موجب) الفجر */
    fun offset(c: Context) = p(c).getInt("offset", 0)
    fun setOffset(c: Context, v: Int) = p(c).edit().putInt("offset", v).apply()

    /** مدة الرنين بالثواني */
    fun duration(c: Context) = p(c).getInt("duration", 60)
    fun setDuration(c: Context, v: Int) = p(c).edit().putInt("duration", v).apply()

    /** رابط النغمة المختارة، أو null = الافتراضية */
    fun sound(c: Context): String? = p(c).getString("sound", null)
    fun setSound(c: Context, v: String?) = p(c).edit().putString("sound", v).apply()
}
