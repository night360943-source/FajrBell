package com.example.fajrbell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** بيستقبل الموقع الجديد من النظام ويحدّث المواقيت والجرس والودجت عليه */
class LocationUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.bgLocation(context)) return

        val loc: Location? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED, Location::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED)
        }
        if (loc == null) return

        // نستنى لحد ما اسم المكان وكود الدولة يتجابوا (بحد أقصى 9 ثواني)
        val pending = goAsync()
        var done = false
        fun finish() {
            if (!done) {
                done = true
                try { pending.finish() } catch (e: Exception) { }
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 9_000L)
        LocationHelper.save(context, loc, { finish() })
    }
}
