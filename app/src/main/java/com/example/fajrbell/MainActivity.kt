package com.example.fajrbell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var prayerNameText: TextView
    private lateinit var ringTimeText: TextView
    private lateinit var dateText: TextView
    private lateinit var offsetText: TextView
    private lateinit var soundText: TextView
    private lateinit var durationText: TextView
    private lateinit var toggle: Button
    private val switches = LinkedHashMap<Prayer, SwitchCompat>()

    private val ringtonePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK) {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= 33)
                    r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                else
                    @Suppress("DEPRECATION")
                    r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                Prefs.setSound(this, uri?.toString())
                refresh()
            }
        }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }
                Prefs.setSound(this, uri.toString())
                refresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        statusText = label(16f)
        prayerNameText = label(22f)
        ringTimeText = label(44f)
        dateText = label(16f)
        offsetText = label(17f)
        soundText = label(15f)
        durationText = label(17f)

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

        root.addView(statusText)
        root.addView(prayerNameText)
        root.addView(ringTimeText)
        root.addView(dateText)
        root.addView(toggle)

        // ----- الصلوات -----
        root.addView(title("🕌 الصلوات (فعّل أو أوقف جرس كل صلاة)"))
        Prayer.values().forEach { p ->
            val sw = SwitchCompat(this).apply {
                textSize = 17f
                setPadding(0, 20, 0, 20)
                setOnClickListener {
                    Prefs.setPrayerEnabled(this@MainActivity, p, isChecked)
                    if (Prefs.enabled(this@MainActivity)) FajrScheduler.schedule(this@MainActivity)
                    refresh()
                }
            }
            switches[p] = sw
            root.addView(sw)
        }

        // ----- وقت الرنين -----
        root.addView(title("⏰ تعديل وقت الرنين (لكل الصلوات)"))
        root.addView(offsetText)
        root.addView(stepper(
            "−10" to { changeOffset(-10) }, "−1" to { changeOffset(-1) },
            "+1" to { changeOffset(1) }, "+10" to { changeOffset(10) }
        ))
        root.addView(btn("رجوع لوقت الأذان بالظبط") { setOffset(0) })

        // ----- المنبه -----
        root.addView(title("🔔 تعديل المنبه"))
        root.addView(soundText)
        root.addView(btn("اختيار نغمة من نغمات التليفون") {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                Prefs.sound(this@MainActivity)?.let {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                }
            }
            ringtonePicker.launch(i)
        })
        root.addView(btn("اختيار ملف صوت من التليفون (mp3)") {
            filePicker.launch(arrayOf("audio/*"))
        })
        root.addView(btn("نغمة المنبه الافتراضية") {
            Prefs.setSound(this, null); refresh()
        })

        root.addView(durationText)
        root.addView(stepper(
            "−30" to { changeDuration(-30) }, "−10" to { changeDuration(-10) },
            "+10" to { changeDuration(10) }, "+30" to { changeDuration(30) }
        ))

        root.addView(btn("تجربة الجرس الآن") {
            ContextCompat.startForegroundService(this, Intent(this, RingService::class.java))
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private var waitingForPermission = false

    override fun onResume() {
        super.onResume()
        if (waitingForPermission && FajrScheduler.schedule(this)) {
            Prefs.setEnabled(this, true)
            waitingForPermission = false
        }
        refresh()
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
    private fun setOffset(v: Int) {
        Prefs.setOffset(this, v.coerceIn(-180, 180))
        if (Prefs.enabled(this)) FajrScheduler.schedule(this)
        refresh()
    }

    private fun changeOffset(d: Int) = setOffset(Prefs.offset(this) + d)

    private fun changeDuration(d: Int) {
        Prefs.setDuration(this, (Prefs.duration(this) + d).coerceIn(10, 300))
        refresh()
    }

    private fun refresh() {
        val on = Prefs.enabled(this)
        val n = FajrScheduler.next(this)
        val tf = SimpleDateFormat("hh:mm a", Locale("ar"))
        val df = SimpleDateFormat("EEEE d MMMM", Locale("ar"))

        statusText.text = if (on) "✅ الجرس شغال، أقرب رنة:" else "⛔ الجرس متوقف (أقرب رنة لو شغلته):"
        if (n != null) {
            prayerNameText.text = "صلاة " + n.prayer.ar
            ringTimeText.text = tf.format(n.ring)
            dateText.text = df.format(n.ring)
        } else {
            prayerNameText.text = "مفيش صلاة مفعّلة"
            ringTimeText.text = "--:--"
            dateText.text = ""
        }
        toggle.text = if (on) "إيقاف الجرس" else "تشغيل الجرس"

        val off = Prefs.offset(this)
        val today = FajrScheduler.timesFor(0)
        for ((p, sw) in switches) {
            val time = today.getValue(p)
            var t = p.ar + "   " + tf.format(time)
            if (off != 0) t += "  ←  الجرس " + tf.format(java.util.Date(time.time + off * 60_000L))
            sw.text = t
            sw.isChecked = Prefs.prayerEnabled(this, p)
        }

        offsetText.text = when {
            off == 0 -> "الجرس على وقت الأذان بالظبط"
            off < 0 -> "الجرس قبل الأذان بـ ${abs(off)} دقيقة"
            else -> "الجرس بعد الأذان بـ $off دقيقة"
        }
        durationText.text = "مدة الرنين: ${Prefs.duration(this)} ثانية"
        soundText.text = "النغمة الحالية: " + soundName()
    }

    private fun soundName(): String {
        val s = Prefs.sound(this) ?: return "نغمة المنبه الافتراضية"
        val uri = Uri.parse(s)
        try {
            if (uri.scheme == "content" && uri.authority != "media" && uri.authority?.startsWith("settings") != true) {
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
