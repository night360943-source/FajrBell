package com.example.fajrbell

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("fajr", Context.MODE_PRIVATE)

    /** التشغيل العام للجرس */
    fun enabled(c: Context) = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    /** تفعيل/إيقاف كل صلاة لوحدها (الكل شغال افتراضياً) */
    fun prayerEnabled(c: Context, p: Prayer) = sp(c).getBoolean("pray_" + p.name, true)
    fun setPrayerEnabled(c: Context, p: Prayer, v: Boolean) =
        sp(c).edit().putBoolean("pray_" + p.name, v).apply()

    /** دقايق قبل (سالب) أو بعد (موجب) الأذان، لكل صلاة */
    fun offset(c: Context, p: Prayer): Int =
        sp(c).getInt("offset_" + p.name, sp(c).getInt("offset", 0))
    fun setOffset(c: Context, p: Prayer, v: Int) =
        sp(c).edit().putInt("offset_" + p.name, v).apply()

    /** نغمة كل صلاة. null = الافتراضية */
    fun sound(c: Context, p: Prayer): String? {
        val s = sp(c)
        val key = "sound_" + p.name
        return if (s.contains(key)) {
            val v = s.getString(key, "") ?: ""
            if (v.isEmpty()) null else v
        } else {
            s.getString("sound", null)
        }
    }
    fun setSound(c: Context, p: Prayer, v: String?) =
        sp(c).edit().putString("sound_" + p.name, v ?: "").apply()

    /** مدة الرنين بالثواني (لكل الصلوات) */
    fun duration(c: Context) = sp(c).getInt("duration", 60)
    fun setDuration(c: Context, v: Int) = sp(c).edit().putInt("duration", v).apply()
}
