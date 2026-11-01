package com.example.fajrbell

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Date
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private class Row(val sw: SwitchCompat, val offsetText: TextView, val soundText: TextView)

    private lateinit var bellText: TextView
    private lateinit var nextLabel: TextView
    private lateinit var countdownText: TextView
    private lateinit var adhanText: TextView
    private lateinit var durationText: TextView
    private lateinit var dateText: TextView
    private lateinit var batteryText: TextView
    private lateinit var keepText: TextView
    private lateinit var keepButton: Button
    private lateinit var toggle: Button

    private val rows = LinkedHashMap<Prayer, Row>()
    private var pendingPrayer: Prayer? = null
    private var waitingForPermission = false

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateCountdown()
            handler.postDelayed(this, 1000)
        }
    }

    private val ringtonePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val p = pendingPrayer
            if (p != null && r.resultCode == RESULT_OK) {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= 33)
                    r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                else
                    @Suppress("DEPRECATION")
                    r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                Prefs.setSound(this, p, uri?.toString())
                refresh()
            }
        }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val p = pendingPrayer
            if (uri != null && p != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }
                Prefs.setSound(this, p, uri.toString())
                refresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedInstanceState?.getString("pp")?.let {
            try { pendingPrayer = Prayer.valueOf(it) } catch (_: Exception) { }
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        nextLabel = label(20f)
        countdownText = label(48f)
        adhanText = label(16f)
        bellText = label(14f)
        durationText = label(17f)
        dateText = label(16f)
        batteryText = label(15f)
        keepText = label(15f)

        toggle = btn("") {
            if (Prefs.enabled(this)) {
                Prefs.setEnabled(this, false)
                FajrScheduler.cancel(this)
            } else {
                tryEnable()
            }
            refresh()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(40, 48, 40, 48)
        }

        // ----- الصلاة القادمة والوقت المتبقي -----
        root.addView(nextLabel)
        root.addView(countdownText)
        root.addView(adhanText)
        root.addView(bellText)
        root.addView(toggle)

        // ----- كل صلاة -----
        root.addView(title("🕌 الصلوات (الجرس والوقت والنغمة لكل صلاة)"))
        Prayer.values().forEach { p ->
            root.addView(
                buildPrayerCard(p),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 24 }
            )
        }

        // ----- التاريخ -----
        root.addView(title("📅 التاريخ (الهجري والميلادي)"))
        root.addView(dateText)
        root.addView(stepper(
            "−1 يوم" to { changeHijri(-1) }, "+1 يوم" to { changeHijri(1) }
        ))

        // ----- البطارية -----
        root.addView(title("🔋 عشان الجرس والودجت ما يتوقفوش"))
        root.addView(batteryText)
        root.addView(btn("السماح للتطبيق بالعمل في الخلفية") { askBatteryWhitelist() })
        root.addView(btn("فتح التشغيل التلقائي (Xiaomi)") { openAutostart() })
        root.addView(btn("فتح توفير البطارية للتطبيق (Xiaomi)") { openXiaomiBattery() })
        root.addView(keepText)
        keepButton = btn("") { toggleKeepAlive() }
        root.addView(keepButton)

        // ----- مدة الرنين -----
        root.addView(title("⏱ مدة الرنين (لكل الصلوات)"))
        root.addView(durationText)
        root.addView(stepper(
            "−30" to { changeDuration(-30) }, "−10" to { changeDuration(-10) },
            "+10" to { changeDuration(10) }, "+30" to { changeDuration(30) }
        ))

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pp", pendingPrayer?.name)
    }

    override fun onResume() {
        super.onResume()
        if (waitingForPermission && FajrScheduler.schedule(this)) {
            Prefs.setEnabled(this, true)
            waitingForPermission = false
        }
        refresh()
        if (PrayerWidgetProvider.hasWidgets(this)) PrayerWidgetProvider.scheduleTick(this)
        if (Prefs.keepAlive(this)) KeepAliveService.start(this)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    // ---------- كارت كل صلاة ----------
    private fun buildPrayerCard(p: Prayer): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.argb(28, 128, 128, 128))
        }

        val sw = SwitchCompat(this).apply {
            textSize = 18f
            setPadding(0, 16, 0, 16)
            setOnClickListener {
                Prefs.setPrayerEnabled(this@MainActivity, p, isChecked)
                if (Prefs.enabled(this@MainActivity)) FajrScheduler.schedule(this@MainActivity)
                refresh()
            }
        }

        val offsetText = label(16f)
        val soundText = label(15f)

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        details.addView(offsetText)
        details.addView(stepper(
            "−10" to { changeOffset(p, -10) }, "−1" to { changeOffset(p, -1) },
            "+1" to { changeOffset(p, 1) }, "+10" to { changeOffset(p, 10) }
        ))
        details.addView(btn("الجرس على الأذان بالظبط") { setOffset(p, 0) })
        details.addView(soundText)
        details.addView(btn("تغيير النغمة") { chooseSound(p) })
        details.addView(btn("تجربة جرس " + p.ar) {
            val i = Intent(this, RingService::class.java)
                .putExtra("key", p.name)
                .putExtra("test", true)
            ContextCompat.startForegroundService(this, i)
        })

        val edit = btn("⚙ تعديل الوقت والنغمة") {
            details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        card.addView(sw)
        card.addView(edit)
        card.addView(details)

        rows[p] = Row(sw, offsetText, soundText)
        return card
    }

    private fun chooseSound(p: Prayer) {
        pendingPrayer = p
        AlertDialog.Builder(this)
            .setTitle("نغمة صلاة " + p.ar)
            .setItems(
                arrayOf(
                    "اختيار نغمة من نغمات التليفون",
                    "اختيار ملف صوت من التليفون (mp3)",
                    "النغمة الافتراضية"
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            Prefs.sound(this@MainActivity, p)?.let {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                            }
                        }
                        ringtonePicker.launch(i)
                    }
                    1 -> filePicker.launch(arrayOf("audio/*"))
                    else -> {
                        Prefs.setSound(this, p, null)
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun openAppDetails() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) { }
    }

    /** صفحة التشغيل التلقائي في Xiaomi/Redmi */
    private fun openAutostart() {
        try {
            startActivity(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
            )
        } catch (_: Exception) {
            openAppDetails()
        }
    }

    /** صفحة توفير البطارية للتطبيق في Xiaomi/Redmi */
    private fun openXiaomiBattery() {
        try {
            startActivity(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                )
                    .putExtra("package_name", packageName)
                    .putExtra("package_label", "جرس الصلاة")
            )
        } catch (_: Exception) {
            openAppDetails()
        }
    }

    private fun toggleKeepAlive() {
        val on = !Prefs.keepAlive(this)
        Prefs.setKeepAlive(this, on)
        if (on) KeepAliveService.start(this) else KeepAliveService.stop(this)
        refresh()
    }

    private fun isWhitelisted(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun askBatteryWhitelist() {
        if (isWhitelisted()) {
            Toast.makeText(this, "مسموح بالفعل ✅", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) { }
        }
    }

    private fun tryEnable() {
        if (FajrScheduler.schedule(this)) {
            Prefs.setEnabled(this, true)
            waitingForPermission = false
            return
        }
        // الإذن ناقص: افتح شاشة السماح بالمنبهات
        waitingForPermission = true
        Toast.makeText(this, "فعّل «المنبهات والتذكيرات» للتطبيق ثم ارجع", Toast.LENGTH_LONG).show()
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    // ---------- منطق ----------
    private fun setOffset(p: Prayer, v: Int) {
        Prefs.setOffset(this, p, v.coerceIn(-180, 180))
        if (Prefs.enabled(this)) FajrScheduler.schedule(this)
        refresh()
    }

    private fun changeOffset(p: Prayer, d: Int) = setOffset(p, Prefs.offset(this, p) + d)

    private fun changeHijri(d: Int) {
        Prefs.setHijriAdjust(this, (Prefs.hijriAdjust(this) + d).coerceIn(-3, 3))
        refresh()
    }

    private fun changeDuration(d: Int) {
        Prefs.setDuration(this, (Prefs.duration(this) + d).coerceIn(10, 300))
        refresh()
    }

    /** الوقت المتبقي للصلاة القادمة (مش للجرس) */
    private fun updateCountdown() {
        val now = System.currentTimeMillis()
        val next = FajrScheduler.window(now).second
        nextLabel.text = "باقي على صلاة " + next.prayer.ar
        countdownText.text = Fmt.hms(next.time.time - now)
        var line = "الأذان الساعة " + Fmt.time(next.time)
        if (!Prefs.prayerEnabled(this, next.prayer)) line += "   •   🔕 جرسها مقفول"
        adhanText.text = line
    }

    private fun refresh() {
        val on = Prefs.enabled(this)
        updateCountdown()

        val n = FajrScheduler.next(this)
        bellText.text = when {
            !on -> "🔕 الجرس متوقف"
            n == null -> "🔔 الجرس شغال بس مفيش صلاة مفعّلة"
            else -> "🔔 أقرب جرس: صلاة " + n.prayer.ar + " الساعة " + Fmt.time(n.ring)
        }
        toggle.text = if (on) "إيقاف الجرس" else "تشغيل الجرس"

        val shown = FajrScheduler.displayTimes()
        for ((p, row) in rows) {
            val time = shown.getValue(p)
            val off = Prefs.offset(this, p)
            var t = p.ar + "   " + Fmt.time(time)
            if (off != 0) t += "  ←  الجرس " + Fmt.time(Date(time.time + off * 60_000L))
            row.sw.text = t
            row.sw.isChecked = Prefs.prayerEnabled(this, p)

            row.offsetText.text = when {
                off == 0 -> "الجرس على الأذان بالظبط"
                off < 0 -> "الجرس قبل الأذان بـ " + abs(off) + " دقيقة"
                else -> "الجرس بعد الأذان بـ " + off + " دقيقة"
            }
            row.soundText.text = "النغمة: " + soundName(p)
        }

        dateText.text = Fmt.hijri(Prefs.hijriAdjust(this)) + " هـ   |   " + Fmt.gregorian() +
            "\nلو التاريخ الهجري مختلف يوم عندك، عدّله بالأزرار"
        durationText.text = "مدة الرنين: " + Prefs.duration(this) + " ثانية"
        batteryText.text = if (isWhitelisted()) "✅ مسموح للتطبيق بالعمل في الخلفية"
        else "⚠️ غير مسموح: الودجت ممكن يقف لما التليفون يتقفل. اضغط الزرار تحت"
        val ka = Prefs.keepAlive(this)
        keepText.text = if (ka) "✅ التشغيل المستمر شغال (إشعار ثابت بيحافظ على التطبيق حي)"
        else "التشغيل المستمر متوقف"
        keepButton.text = if (ka) "إيقاف التشغيل المستمر" else "تشغيل التشغيل المستمر (موصى به لـ Xiaomi)"
        PrayerWidgetProvider.refreshAll(this)
    }

    private fun soundName(p: Prayer): String {
        val s = Prefs.sound(this, p) ?: return "نغمة المنبه الافتراضية"
        val uri = Uri.parse(s)
        try {
            if (uri.scheme == "content" && uri.authority != "media" &&
                uri.authority?.startsWith("settings") != true
            ) {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) return it.getString(0)
                }
            }
            RingtoneManager.getRingtone(this, uri)?.getTitle(this)?.let { return it }
        } catch (_: Exception) { }
        return "نغمة مخصصة"
    }

    // ---------- عناصر الواجهة ----------
    private fun label(size: Float) = TextView(this).apply {
        textSize = size
        gravity = Gravity.CENTER
        setPadding(0, 12, 0, 12)
    }

    private fun title(t: String) = TextView(this).apply {
        text = t
        textSize = 19f
        gravity = Gravity.CENTER
        setPadding(0, 48, 0, 8)
    }

    private fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { onClick() }
    }

    private fun stepper(vararg items: Pair<String, () -> Unit>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        items.forEach { (t, a) ->
            addView(btn(t, a), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
}
