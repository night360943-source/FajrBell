package com.example.fajrbell

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        status = TextView(this).apply { textSize = 18f; gravity = Gravity.CENTER }
        val toggle = Button(this)
        val test = Button(this).apply { text = "تجربة الجرس الآن" }
        val prefs = getSharedPreferences("fajr", Context.MODE_PRIVATE)

        fun refresh() {
            val on = prefs.getBoolean("enabled", false)
            toggle.text = if (on) "إيقاف جرس الفجر" else "تشغيل جرس الفجر"
            val fmt = SimpleDateFormat("EEEE d MMM - hh:mm a", Locale("ar"))
            status.text = if (on) "الفجر القادم:\n" + fmt.format(FajrScheduler.nextFajr())
            else "الجرس متوقف"
        }

        toggle.setOnClickListener {
            val on = !prefs.getBoolean("enabled", false)
            prefs.edit().putBoolean("enabled", on).apply()
            if (on) FajrScheduler.schedule(this)
            refresh()
        }
        test.setOnClickListener {
            ContextCompat.startForegroundService(this, Intent(this, RingService::class.java))
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(status); addView(toggle); addView(test)
        })
        refresh()
    }
}
