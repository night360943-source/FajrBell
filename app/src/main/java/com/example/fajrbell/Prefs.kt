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

    /** تعديل التاريخ الهجري بالأيام (لو اختلف يوم عن دار الإفتاء) */
    fun hijriAdjust(c: Context) = sp(c).getInt("hijri_adjust", 0)
    fun setHijriAdjust(c: Context, v: Int) = sp(c).edit().putInt("hijri_adjust", v).apply()

    /** التشغيل المستمر (إشعار ثابت بيحافظ على التطبيق حي) */
    fun keepAlive(c: Context) = sp(c).getBoolean("keep_alive", false)
    fun setKeepAlive(c: Context, v: Boolean) = sp(c).edit().putBoolean("keep_alive", v).apply()

    /** تنبيه "اقتربت صلاة ..." قبل كل صلاة (شغال افتراضياً) */
    fun remindEnabled(c: Context, p: Prayer) = sp(c).getBoolean("remind_" + p.name, true)
    fun setRemindEnabled(c: Context, p: Prayer, v: Boolean) =
        sp(c).edit().putBoolean("remind_" + p.name, v).apply()

    /** التنبيه قبل الصلاة بكام دقيقة (افتراضي 15) */
    fun remindMinutes(c: Context, p: Prayer): Int = sp(c).getInt("remind_min_" + p.name, 15)
    fun setRemindMinutes(c: Context, p: Prayer, v: Int) =
        sp(c).edit().putInt("remind_min_" + p.name, v).apply()

    /** آخر موقع اتحدد للقبلة (بيتحفظ عشان يشتغل من غير إنترنت) */
    fun hasLocation(c: Context) = sp(c).contains("loc_lat")
    fun locLat(c: Context, def: Double): Double = sp(c).getString("loc_lat", null)?.toDoubleOrNull() ?: def
    fun locLon(c: Context, def: Double): Double = sp(c).getString("loc_lon", null)?.toDoubleOrNull() ?: def
    fun setLocation(c: Context, lat: Double, lon: Double) =
        sp(c).edit().putString("loc_lat", lat.toString()).putString("loc_lon", lon.toString()).apply()

    /** اسم المكان (محافظة + مكان) واللي اتحسب عليه الاسم */
    fun locName(c: Context): String? = sp(c).getString("loc_name", null)
    fun locNameLat(c: Context, def: Double): Double = sp(c).getString("loc_name_lat", null)?.toDoubleOrNull() ?: def
    fun locNameLon(c: Context, def: Double): Double = sp(c).getString("loc_name_lon", null)?.toDoubleOrNull() ?: def
    fun setLocName(c: Context, lat: Double, lon: Double, name: String) =
        sp(c).edit().putString("loc_name", name)
            .putString("loc_name_lat", lat.toString())
            .putString("loc_name_lon", lon.toString()).apply()
}
