package com.example.fajrbell

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * تحديث الموقع في الخلفية بأقل استهلاك بطارية:
 * النظام نفسه بيبعتلنا الموقع الجديد لما تتحرك أكتر من 3 كم (حتى لو التطبيق مقفول).
 */
object LocationTracker {

    const val ACTION = "com.example.fajrbell.LOCATION_UPDATE"

    fun hasBackgroundPermission(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return LocationHelper.hasPermission(c)
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun pending(c: Context): PendingIntent {
        val i = Intent(c, LocationUpdateReceiver::class.java).setAction(ACTION)
        // لازم يبقى Mutable عشان النظام يضيف الموقع جوا الـ Intent
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(c, 21, i, flags)
    }

    /** بيسجّل (أو بيجدد تسجيل) استقبال تحديثات الموقع. آمن إنه يتنادى أكتر من مرة */
    @SuppressLint("MissingPermission")
    fun start(c: Context) {
        if (!Prefs.bgLocation(c) || !LocationHelper.hasPermission(c)) {
            stop(c)
            return
        }
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val pi = pending(c)
        try { lm.removeUpdates(pi) } catch (e: Exception) { }
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 20 * 60_000L, 3000f, pi)
            }
        } catch (e: Exception) { }
        try {
            // بيستفيد من مواقع التطبيقات التانية من غير استهلاك إضافي
            lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 10 * 60_000L, 3000f, pi)
        } catch (e: Exception) { }
    }

    fun stop(c: Context) {
        try {
            val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.removeUpdates(pending(c))
        } catch (e: Exception) { }
    }
}
