package com.example.fajrbell

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs

/** قراءة موقعك، وحفظه، وتحديث المواقيت والجرس والودجت لما تغيّر مكانك */
object LocationHelper {

    private val main = Handler(Looper.getMainLooper())

    fun hasPermission(c: Context): Boolean {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun near(lat1: Double, lon1: Double, lat2: Double, lon2: Double, meters: Float): Boolean {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0] < meters
    }

    fun coordsText(lat: Double, lon: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return String.format(Locale.US, "%.2f° %s ، %.2f° %s", abs(lat), ns, abs(lon), ew)
    }

    /** اسم المكان الحالي (المحفوظ) أو الإحداثيات */
    fun currentName(c: Context): String {
        if (!Prefs.hasLocation(c)) return "القاهرة / الجيزة (افتراضي)"
        val lat = Prefs.locLat(c, FajrScheduler.LATITUDE)
        val lon = Prefs.locLon(c, FajrScheduler.LONGITUDE)
        val n = Prefs.locName(c)
        if (n != null && near(lat, lon, Prefs.locNameLat(c, lat), Prefs.locNameLon(c, lon), 3000f)) {
            return n
        }
        return coordsText(lat, lon)
    }

    /**
     * بيجيب آخر موقع معروف (لو لسه جديد) وبعدين موقع جديد.
     * onLocation بيتنادى على الـ main thread، ممكن أكتر من مرة.
     */
    @SuppressLint("MissingPermission")
    fun detect(c: Context, onLocation: (Location) -> Unit) {
        if (!hasPermission(c)) return
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 1) آخر موقع معروف (لو أحدث من نص ساعة)
        var best: Location? = null
        val providers = listOf(
            LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER
        )
        for (p in providers) {
            try {
                val l = lm.getLastKnownLocation(p) ?: continue
                val b = best
                if (b == null || l.time > b.time) best = l
            } catch (e: Exception) { }
        }
        val lastKnown = best
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 30 * 60_000L) {
            onLocation(lastKnown)
        }

        // 2) موقع جديد
        val provider: String? = try {
            when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        if (provider == null) return
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(c)) { loc ->
                    if (loc != null) onLocation(loc)
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        onLocation(location)
                    }

                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }, Looper.getMainLooper())
            }
        } catch (e: Exception) { }
    }

    /** المكان + المحافظة (+ الدولة لو برا مصر) وكود الدولة */
    @Suppress("DEPRECATION")
    private fun geocode(c: Context, lat: Double, lon: Double): Pair<String, String?>? {
        return try {
            if (!Geocoder.isPresent()) return null
            val g = Geocoder(c, Locale("ar"))
            val a = g.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
            val place = a.subLocality ?: a.locality ?: a.subAdminArea ?: a.featureName
            val gov = a.adminArea
            val country = if (a.countryCode != "EG") a.countryName else null
            val parts = listOfNotNull(place, gov, country).filter { it.isNotBlank() }.distinct()
            if (parts.isEmpty()) null else Pair(parts.joinToString("، "), a.countryCode)
        } catch (e: Exception) {
            null
        }
    }

    /** بيحدّث المواقيت والجرس والودجت على الموقع الجديد */
    fun afterChanged(c: Context) {
        try {
            if (Prefs.enabled(c)) FajrScheduler.schedule(c)
            PrayerWidgetProvider.refreshAll(c)
        } catch (e: Exception) { }
    }

    /**
     * بيحفظ الموقع. لو اتغير أكتر من 3 كم بيحدّث المواقيت والجرس والودجت.
     * onName بيتنادى باسم المكان (من المحفوظ أو بعد ما يتجاب من الإنترنت).
     */
    fun save(
        c: Context,
        loc: Location,
        onFinished: (() -> Unit)? = null,
        onName: ((String) -> Unit)? = null
    ) {
        val lat = loc.latitude
        val lon = loc.longitude
        val had = Prefs.hasLocation(c)
        val oldLat = Prefs.locLat(c, FajrScheduler.LATITUDE)
        val oldLon = Prefs.locLon(c, FajrScheduler.LONGITUDE)

        Prefs.setLocation(c, lat, lon)
        // لو انتقلت بعيد، كود الدولة القديم مابقاش صح لحد ما الاسم الجديد يتجاب
        if (had && !near(oldLat, oldLon, lat, lon, 30_000f)) Prefs.setCountry(c, null)
        if (!had || !near(oldLat, oldLon, lat, lon, 3000f)) afterChanged(c)

        val cached = Prefs.locName(c)
        if (cached != null && near(lat, lon, Prefs.locNameLat(c, lat), Prefs.locNameLon(c, lon), 3000f)) {
            onName?.invoke(cached)
            onFinished?.invoke()
            return
        }
        onName?.invoke(coordsText(lat, lon) + "  (جاري تحديد اسم المكان…)")
        Thread {
            val r = geocode(c, lat, lon)
            main.post {
                if (r != null) {
                    Prefs.setLocName(c, lat, lon, r.first)
                    val oldCc = Prefs.country(c)
                    val cc = r.second
                    if (cc != null) Prefs.setCountry(c, cc)
                    onName?.invoke(r.first)
                    if (cc != null && cc != oldCc) afterChanged(c)
                } else {
                    onName?.invoke(coordsText(lat, lon) + "  (اسم المكان محتاج إنترنت)")
                }
                onFinished?.invoke()
            }
        }.start()
    }
}
