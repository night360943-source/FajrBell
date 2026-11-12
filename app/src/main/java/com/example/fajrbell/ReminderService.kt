package com.example.fajrbell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Date
import java.util.Locale

/**
 * التنبيه الصوتي قبل الصلاة: بينطق "اقتربت صلاة الظهر" بصوت التليفون (Text-to-Speech).
 * لو مفيش صوت عربي متثبت على التليفون، بيشغّل نغمة إشعار بدله.
 */
class ReminderService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var fallback: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var message = ""
    private var finished = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // بينطق بالفعل: تجاهل التشغيل التاني
        if (speaking) return START_NOT_STICKY

        val prayer: Prayer? = intent?.getStringExtra("key")?.let {
            try { Prayer.valueOf(it) } catch (e: Exception) { null }
        }
        val minutes = intent?.getIntExtra("minutes", 15) ?: 15
        val adhan = intent?.getLongExtra("adhan", 0L) ?: 0L
        message = if (prayer != null) "اقتربت صلاة " + prayer.ar else "اقتربت الصلاة"

        // قناة صامتة تماماً: مفيش صوت ولا إشعار ظاهر، النطق هو الصوت الوحيد
        val channelId = "prayer_reminder_quiet"
        val nm = getSystemService(NotificationManager::class.java)
        try { nm.deleteNotificationChannel("prayer_reminder") } catch (e: Exception) { }
        val ch = NotificationChannel(channelId, "تنبيه صوتي قبل الصلاة", NotificationManager.IMPORTANCE_MIN)
        ch.setSound(null, null)
        ch.enableVibration(false)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)

        val open = PendingIntent.getActivity(
            this, 7, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val detail = if (adhan > 0L) {
            "الأذان الساعة " + Fmt.time(Date(adhan)) + " (بعد " + minutes + " دقيقة)"
        } else {
            "تجربة التنبيه"
        }
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(message)
            .setContentText(detail)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setContentIntent(open)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(12, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(12, notif)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        speaking = true
        finished = false
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ finish() }, 25_000L)   // أمان: يقفل بعد 25 ثانية في أسوأ حالة
        tts?.shutdown()
        tts = TextToSpeech(applicationContext, this)
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        val t = tts
        if (status != TextToSpeech.SUCCESS || t == null) {
            playFallback()
            return
        }
        var res = t.setLanguage(Locale("ar", "EG"))
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            res = t.setLanguage(Locale("ar"))
        }
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            playFallback()
            return
        }
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                handler.post { finish() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { playFallback() }
            }
        })
        val r = t.speak(message, TextToSpeech.QUEUE_FLUSH, null, "remind")
        if (r != TextToSpeech.SUCCESS) playFallback()
    }

    /** نغمة إشعار عادية لو النطق العربي مش متاح */
    private fun playFallback() {
        if (finished) return
        try {
            fallback?.release()
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            mp.setOnCompletionListener { finish() }
            mp.prepare()
            mp.start()
            fallback = mp
        } catch (e: Exception) {
            finish()
        }
    }

    private fun finish() {
        if (finished) return
        finished = true
        try {
            // شيل الإشعار فوراً بعد النطق: الصوت بس
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) { }
        stopSelf()
    }

    override fun onDestroy() {
        speaking = false
        handler.removeCallbacksAndMessages(null)
        try { tts?.shutdown() } catch (e: Exception) { }
        tts = null
        fallback?.release()
        fallback = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** true طول ما التنبيه بينطق */
        @Volatile
        var speaking = false
    }
}
