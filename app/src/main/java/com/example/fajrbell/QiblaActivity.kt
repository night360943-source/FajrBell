package com.example.fajrbell

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** حساب اتجاه القبلة من موقعك للكعبة (بالدرجات من الشمال الحقيقي) */
object QiblaMath {
    private const val KAABA_LAT = 21.422487
    private const val KAABA_LON = 39.826206

    fun bearing(lat: Double, lon: Double): Float {
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LON - lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }
}

class QiblaActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var qiblaView: QiblaView
    private var sm: SensorManager? = null
    private val rot = FloatArray(9)
    private val rot2 = FloatArray(9)
    private val ori = FloatArray(3)
    private var declination = 0f
    private var smooth = 0f
    private var first = true
    private var wasAligned = false

    private val handler = Handler(Looper.getMainLooper())
    private var lat = FajrScheduler.LATITUDE
    private var lon = FajrScheduler.LONGITUDE
    private var detecting = false

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasLocationPermission()) {
                detectLocation()
            } else {
                qiblaView.placeName = "اسمح بالموقع عشان القبلة تتحسب على مكانك"
                qiblaView.invalidate()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "🧭 اتجاه القبلة"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // آخر موقع محفوظ (أو القاهرة/الجيزة لو لسه ما اتحددش)
        lat = Prefs.locLat(this, FajrScheduler.LATITUDE)
        lon = Prefs.locLon(this, FajrScheduler.LONGITUDE)

        qiblaView = QiblaView(this)
        qiblaView.qibla = QiblaMath.bearing(lat, lon)
        qiblaView.hint = "ضع التليفون مسطحاً بعيداً عن المعادن والمغناطيس"
        qiblaView.placeName = savedPlaceName()
        setContentView(qiblaView)

        declination = computeDeclination(lat, lon, 0f)
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun onResume() {
        super.onResume()
        val s = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (s == null) {
            qiblaView.hasSensor = false
            qiblaView.invalidate()
        } else {
            sm?.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
        }
        detectLocation()
    }

    override fun onPause() {
        super.onPause()
        sm?.unregisterListener(this)
    }

    // ---------------- الموقع ----------------

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun computeDeclination(lat: Double, lon: Double, alt: Float): Float {
        return try {
            GeomagneticField(lat.toFloat(), lon.toFloat(), alt, System.currentTimeMillis()).declination
        } catch (e: Exception) {
            0f
        }
    }

    private fun coordsText(lat: Double, lon: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return String.format(Locale.US, "%.2f° %s ، %.2f° %s", abs(lat), ns, abs(lon), ew)
    }

    private fun near(lat1: Double, lon1: Double, lat2: Double, lon2: Double, meters: Float): Boolean {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0] < meters
    }

    /** الاسم المحفوظ لو لسه قريب من الموقع الحالي، وإلا الإحداثيات */
    private fun savedPlaceName(): String {
        val name = Prefs.locName(this)
        if (!Prefs.hasLocation(this)) return "القاهرة / الجيزة (مكان افتراضي)"
        if (name != null &&
            near(lat, lon, Prefs.locNameLat(this, lat), Prefs.locNameLon(this, lon), 3000f)
        ) {
            return name
        }
        return coordsText(lat, lon)
    }

    private fun detectLocation() {
        if (!hasLocationPermission()) {
            permLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        if (detecting) return
        detecting = true

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 1) آخر موقع معروف (سريع)
        val last = bestLastKnown(lm)
        if (last != null) {
            applyLocation(last)
        } else {
            qiblaView.placeName = "جاري تحديد مكانك…"
            qiblaView.invalidate()
        }

        // 2) موقع جديد
        requestFresh(lm)
        handler.postDelayed({
            if (detecting) {
                detecting = false
                if (last == null && !Prefs.hasLocation(this)) {
                    qiblaView.placeName = "تعذّر تحديد مكانك. فعّل الموقع (GPS) وجرّب تحديث الموقع"
                    qiblaView.invalidate()
                }
            }
        }, 25_000L)
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(lm: LocationManager): Location? {
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
        return best
    }

    @SuppressLint("MissingPermission")
    private fun requestFresh(lm: LocationManager) {
        val provider: String? = try {
            when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        if (provider == null) {
            detecting = false
            if (!Prefs.hasLocation(this)) {
                qiblaView.placeName = "الموقع مقفول. فعّله من إعدادات التليفون"
                qiblaView.invalidate()
            }
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(this)) { loc ->
                    detecting = false
                    if (loc != null) applyLocation(loc)
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        detecting = false
                        applyLocation(location)
                    }

                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            detecting = false
        }
    }

    /** بيظبط القبلة على الموقع ده ويجيب اسم المكان */
    private fun applyLocation(loc: Location) {
        lat = loc.latitude
        lon = loc.longitude
        Prefs.setLocation(this, lat, lon)
        qiblaView.qibla = QiblaMath.bearing(lat, lon)
        declination = computeDeclination(lat, lon, loc.altitude.toFloat())
        resolveName(lat, lon)
        qiblaView.invalidate()
    }

    private fun resolveName(lat: Double, lon: Double) {
        val cached = Prefs.locName(this)
        if (cached != null &&
            near(lat, lon, Prefs.locNameLat(this, lat), Prefs.locNameLon(this, lon), 3000f)
        ) {
            qiblaView.placeName = cached
            qiblaView.invalidate()
            return
        }
        qiblaView.placeName = coordsText(lat, lon) + "  (جاري تحديد اسم المكان…)"
        qiblaView.invalidate()
        Thread {
            val name = geocodeName(lat, lon)
            handler.post {
                if (name != null) {
                    Prefs.setLocName(this, lat, lon, name)
                    qiblaView.placeName = name
                } else {
                    qiblaView.placeName = coordsText(lat, lon) + "  (اسم المكان محتاج إنترنت)"
                }
                qiblaView.invalidate()
            }
        }.start()
    }

    /** المكان + المحافظة (+ الدولة لو برا مصر) */
    @Suppress("DEPRECATION")
    private fun geocodeName(lat: Double, lon: Double): String? {
        return try {
            if (!Geocoder.isPresent()) return null
            val g = Geocoder(this, Locale("ar"))
            val list = g.getFromLocation(lat, lon, 1)
            val a = list?.firstOrNull() ?: return null
            val place = a.subLocality ?: a.locality ?: a.subAdminArea ?: a.featureName
            val gov = a.adminArea
            val country = if (a.countryCode != "EG") a.countryName else null
            val parts = listOfNotNull(place, gov, country).filter { it.isNotBlank() }.distinct()
            if (parts.isEmpty()) null else parts.joinToString("، ")
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- الحساسات ----------------

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rot, event.values)
        SensorManager.getOrientation(rot, ori)
        val flat = abs(ori[1]) <= 1.0f
        if (!flat) {
            // التليفون واقف: نحسب الاتجاه بالنسبة لظهر التليفون
            SensorManager.remapCoordinateSystem(
                rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, rot2
            )
            SensorManager.getOrientation(rot2, ori)
        }
        var deg = Math.toDegrees(ori[0].toDouble()).toFloat() + declination
        deg = (deg % 360f + 360f) % 360f

        // تنعيم الحركة على الدايرة
        if (first) {
            smooth = deg
            first = false
        } else {
            var d = deg - smooth
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            smooth = (smooth + d * 0.18f + 360f) % 360f
        }
        qiblaView.azimuth = smooth

        val aligned = qiblaView.isAligned()
        if (aligned && !wasAligned) buzz()
        wasAligned = aligned
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        qiblaView.lowAccuracy = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
        qiblaView.invalidate()
    }

    @Suppress("DEPRECATION")
    private fun buzz() {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(70)
            }
        } catch (e: Exception) { }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 101, 0, "📍 تحديث الموقع")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        if (item.itemId == 101) {
            detecting = false
            detectLocation()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

/** بوصلة القبلة: خلفية خضرا بزخرفة إسلامية، وقرص بوصلة ذهبي، وكعبة على حافة القرص */
class QiblaView(context: Context) : View(context) {

    var azimuth = 0f              // اتجاه راس التليفون (0 = شمال حقيقي)
        set(v) {
            field = v
            invalidate()
        }
    var qibla = 0f                // اتجاه القبلة من الشمال
    var hint = ""
    var placeName = ""            // المكان اللي القبلة محسوبة عليه
    var lowAccuracy = false
    var hasSensor = true

    private val gold = Color.parseColor("#D4AF37")
    private val goldLight = Color.parseColor("#F4D675")
    private val green = Color.parseColor("#2ECC71")

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(36, 212, 175, 55)
    }
    private var patternBmp: Bitmap? = null

    /** الفرق بين اتجاه القبلة واتجاه التليفون (موجب = القبلة على يمينك) */
    fun diff(): Float = ((qibla - azimuth + 540f) % 360f) - 180f

    fun isAligned(): Boolean = hasSensor && abs(diff()) <= 3f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawPattern(Canvas(bmp), w.toFloat(), h.toFloat())
            patternBmp = bmp
        }
    }

    /** نجمة ثمانية (مربعين متداخلين) */
    private fun drawStar(c: Canvas, x: Float, y: Float, r: Float, paint: Paint) {
        for (k in 0..1) {
            val path = Path()
            for (i in 0..3) {
                val a = Math.toRadians((i * 90 + k * 45 + 45).toDouble())
                val px = x + (r * cos(a)).toFloat()
                val py = y + (r * sin(a)).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            c.drawPath(path, paint)
        }
    }

    private fun drawPattern(c: Canvas, w: Float, h: Float) {
        val g = w / 5f
        var row = 0
        var y = 0f
        while (y < h + g) {
            var x = if (row % 2 == 0) 0f else g / 2f
            while (x < w + g) {
                drawStar(c, x, y, g * 0.30f, patternPaint)
                x += g
            }
            y += g * 0.5f
            row++
        }
    }

    private fun drawKaaba(c: Canvas, x: Float, y: Float, s: Float) {
        // هالة ذهبية
        fill.color = Color.argb(90, 212, 175, 55)
        c.drawCircle(x, y, s * 1.45f, fill)
        // المكعب الأسود
        val body = RectF(x - s, y - s, x + s, y + s)
        fill.color = Color.parseColor("#111111")
        c.drawRoundRect(body, s * 0.12f, s * 0.12f, fill)
        // الحزام الذهبي
        fill.color = gold
        c.drawRect(x - s, y - s * 0.62f, x + s, y - s * 0.30f, fill)
        // الباب
        fill.color = goldLight
        c.drawRoundRect(
            RectF(x - s * 0.24f, y + s * 0.08f, x + s * 0.24f, y + s), s * 0.1f, s * 0.1f, fill
        )
        // حدود
        stroke.color = gold
        stroke.strokeWidth = max(1.5f, s * 0.06f)
        c.drawRoundRect(body, s * 0.12f, s * 0.12f, stroke)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val aligned = isAligned()
        val accent = if (aligned) green else gold

        // الخلفية
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h, Color.parseColor("#0E4A38"), Color.parseColor("#04201A"), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, bgPaint)
        patternBmp?.let { c.drawBitmap(it, 0f, 0f, null) }

        // توزيع المساحة: فوق (المكان) - البوصلة - تحت (النصوص)
        val topArea = h * 0.13f
        val bottomArea = max(h * 0.21f, w * 0.30f)
        val avail = h - topArea - bottomArea
        val r = min(w * 0.43f, avail / 2.3f)
        val cx = w / 2f
        val cy = topArea + (avail - 2.3f * r) / 2f + r * 1.15f

        // ----- فوق: القبلة محسوبة على + المكان -----
        text.typeface = Typeface.DEFAULT
        text.color = goldLight
        text.textSize = w * 0.040f
        c.drawText("القبلة محسوبة على", cx, topArea * 0.38f, text)

        val shown = "📍 " + placeName
        var size = w * 0.058f
        text.typeface = Typeface.DEFAULT_BOLD
        text.color = Color.WHITE
        text.textSize = size
        while (text.measureText(shown) > w * 0.92f && size > 10f) {
            size *= 0.94f
            text.textSize = size
        }
        c.drawText(shown, cx, topArea * 0.38f + w * 0.040f * 1.7f, text)

        // هالة حوالين البوصلة (بتبقى خضرا لما تواجه القبلة)
        fill.shader = RadialGradient(
            cx, cy, r * 1.4f,
            intArrayOf(
                Color.argb(if (aligned) 120 else 64, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r * 1.4f, fill)
        fill.shader = null

        // قرص البوصلة
        fill.color = Color.parseColor("#0A3328")
        c.drawCircle(cx, cy, r, fill)
        fill.color = Color.parseColor("#0F4636")
        c.drawCircle(cx, cy, r * 0.80f, fill)

        // الحلقات الذهبية
        stroke.color = accent
        stroke.strokeWidth = r * 0.035f
        c.drawCircle(cx, cy, r, stroke)
        stroke.strokeWidth = r * 0.012f
        c.drawCircle(cx, cy, r * 0.80f, stroke)

        // ----- القرص بيلف عكس اتجاه التليفون عشان الشمال يفضل شمال -----
        c.save()
        c.rotate(-azimuth, cx, cy)

        for (deg in 0 until 360 step 5) {
            val big = deg % 30 == 0
            val len = if (big) r * 0.08f else r * 0.04f
            val a = Math.toRadians(deg - 90.0)
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            stroke.color = when {
                deg == 0 -> Color.parseColor("#E74C3C")
                big -> goldLight
                else -> Color.argb(150, 255, 255, 255)
            }
            stroke.strokeWidth = if (big) r * 0.016f else r * 0.008f
            c.drawLine(
                cx + ca * (r * 0.975f - len), cy + sa * (r * 0.975f - len),
                cx + ca * r * 0.975f, cy + sa * r * 0.975f, stroke
            )
        }

        // الجهات الأربع (بتفضل مقروءة)
        val labels = arrayOf("شمال", "شرق", "جنوب", "غرب")
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = r * 0.14f
        for (i in 0..3) {
            val a = Math.toRadians(i * 90.0 - 90.0)
            val lx = cx + (cos(a) * r * 0.62f).toFloat()
            val ly = cy + (sin(a) * r * 0.62f).toFloat()
            text.color = if (i == 0) Color.parseColor("#FF8A80") else Color.WHITE
            c.save()
            c.rotate(azimuth, lx, ly)
            c.drawText(labels[i], lx, ly + text.textSize * 0.35f, text)
            c.restore()
        }

        // خط القبلة والكعبة
        val qa = Math.toRadians(qibla - 90.0)
        val qx = cos(qa).toFloat()
        val qy = sin(qa).toFloat()
        stroke.color = accent
        stroke.strokeWidth = r * 0.022f
        c.drawLine(cx + qx * r * 0.14f, cy + qy * r * 0.14f, cx + qx * r * 0.74f, cy + qy * r * 0.74f, stroke)
        val kx = cx + qx * r * 0.90f
        val ky = cy + qy * r * 0.90f
        c.save()
        c.rotate(azimuth, kx, ky)
        drawKaaba(c, kx, ky, r * 0.10f)
        c.restore()

        c.restore()

        // المؤشر الثابت فوق: اتجاه راس التليفون
        val tri = Path()
        tri.moveTo(cx - r * 0.08f, cy - r * 1.14f)
        tri.lineTo(cx + r * 0.08f, cy - r * 1.14f)
        tri.lineTo(cx, cy - r * 1.02f)
        tri.close()
        fill.color = accent
        c.drawPath(tri, fill)

        // زخرفة المركز
        fill.color = accent
        drawStar(c, cx, cy, r * 0.11f, fill)
        fill.color = Color.parseColor("#0A3328")
        c.drawCircle(cx, cy, r * 0.045f, fill)

        // ----- النصوص تحت -----
        val ty = cy + r + w * 0.10f
        text.typeface = Typeface.DEFAULT_BOLD
        if (!hasSensor) {
            text.color = Color.WHITE
            text.textSize = w * 0.05f
            c.drawText("حساس البوصلة غير متاح في التليفون", cx, ty, text)
        } else if (aligned) {
            text.color = green
            text.textSize = w * 0.075f
            c.drawText("أنت في اتجاه القبلة ✓", cx, ty, text)
        } else {
            val d = diff()
            text.color = Color.WHITE
            text.textSize = w * 0.068f
            val msg = if (d > 0) "لف يمينك " + abs(d).roundToInt() + "°"
            else "لف يسارك " + abs(d).roundToInt() + "°"
            c.drawText(msg, cx, ty, text)
        }

        text.typeface = Typeface.DEFAULT
        text.color = goldLight
        text.textSize = w * 0.046f
        c.drawText("اتجاه القبلة: " + qibla.roundToInt() + "° من الشمال", cx, ty + w * 0.085f, text)

        text.color = Color.argb(190, 255, 255, 255)
        text.textSize = w * 0.037f
        val hintText = if (lowAccuracy) "حرّك التليفون على شكل 8 لمعايرة البوصلة" else hint
        c.drawText(hintText, cx, ty + w * 0.155f, text)
    }
}
