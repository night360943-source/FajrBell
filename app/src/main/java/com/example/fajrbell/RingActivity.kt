package com.example.fajrbell

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Date

/** شاشة الأذان: بتظهر فوق شاشة القفل وفيها زرار كبير لإيقاف الأذان */
class RingActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val check = object : Runnable {
        override fun run() {
            if (!RingService.ringing) {
                finish()
            } else {
                handler.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prayer: Prayer? = intent?.getStringExtra("key")?.let {
            try { Prayer.valueOf(it) } catch (e: Exception) { null }
        }
        val title = if (prayer != null) "حان الآن موعد صلاة " + prayer.ar else "جرس الصلاة"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.parseColor("#0B3A5B"))
            setPadding(48, 48, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = title
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = Fmt.time(Date())
            textSize = 44f
            setTextColor(Color.parseColor("#FFD60A"))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        })
        root.addView(TextView(this).apply {
            text = "اقلب التليفون على وجهه لإسكات الأذان"
            textSize = 16f
            setTextColor(Color.argb(200, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 64)
        })
        root.addView(
            Button(this).apply {
                text = "إيقاف الأذان"
                textSize = 26f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#E53935"))
                setOnClickListener {
                    startService(
                        Intent(this@RingActivity, RingService::class.java).setAction("STOP")
                    )
                    finish()
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (140 * resources.displayMetrics.density).toInt()
            )
        )

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(check)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(check)
    }
}
