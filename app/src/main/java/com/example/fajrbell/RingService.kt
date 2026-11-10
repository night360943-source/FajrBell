package com.example.fajrbell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat

class RingService : Service() {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var sensorManager: SensorManager? = null
    private var flipListener: SensorEventListener? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        val prayer: Prayer? = intent?.getStringExtra("key")?.let {
            try { Prayer.valueOf(it) } catch (e: Exception) { null }
        }
        val isTest = intent?.getBooleanExtra("test", false) ?: false
        val title = when {
            prayer == null -> "تجربة الجرس"
            isTest -> "تجربة جرس صلاة " + prayer.ar
            else -> "حان الآن موعد صلاة " + prayer.ar
        }

        val channelId = "fajr_bell"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "جرس الصلاة", NotificationManager.IMPORTANCE_HIGH)
        )
        val stopPi = PendingIntent.getService(
            this, 3, Intent(this, RingService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )

        // شاشة الأذان اللي بتظهر فوق القفل
        val ringIntent = Intent(this, RingActivity::class.java)
            .putExtra("key", prayer?.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val ringPi = PendingIntent.getActivity(
            this, 4, ringIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("اضغط إيقاف، أو اقلب التليفون على وجهه")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(ringPi)
            .setFullScreenIntent(ringPi, true)
            .addAction(0, "إيقاف", stopPi)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(10, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(10, notif)
        }

        ringing = true
        if (!isTest) {
            // نوّر الشاشة وافتح شاشة الأذان (لو الأذونات مسموحة)
            wakeScreen()
            try { startActivity(ringIntent) } catch (e: Exception) { }
        }
        startSound(prayer)
        startFlipListener()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, Prefs.duration(this) * 1000L)
        return START_NOT_STICKY
    }

    /** بينوّر الشاشة 15 ثانية عشان ماتفضلش سودا لما الأذان يرن */
    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "fajrbell:ring-screen"
            )
            wl.acquire(15_000L)
        } catch (e: Exception) { }
    }

    private fun buildPlayer(uri: Uri): MediaPlayer {
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            return mp
        } catch (e: Exception) {
            mp.release()
            throw e
        }
    }

    private fun startSound(prayer: Prayer?) {
        player?.release()
        player = null
        val saved = if (prayer != null) Prefs.sound(this, prayer)?.let { Uri.parse(it) } else null
        val resId = resources.getIdentifier("bell", "raw", packageName)
        val raw = if (resId != 0) Uri.parse("android.resource://$packageName/$resId") else null
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        // الأولوية: نغمة الصلاة اللي اخترتها > bell.mp3 > نغمة المنبه الافتراضية
        for (uri in listOfNotNull(saved, raw, fallback)) {
            try {
                player = buildPlayer(uri)
                return
            } catch (_: Exception) { }
        }
    }

    /**
     * قلب التليفون على وجهه = إسكات الأذان.
     * لازم التليفون يكون مرفوع الأول (مش مقلوب من البداية)، وبعدين تقلبه.
     */
    private fun startFlipListener() {
        stopFlipListener()
        val sm = getSystemService(SensorManager::class.java) ?: return
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val l = object : SensorEventListener {
            private var wasUp = false
            private var downSince = 0L

            override fun onSensorChanged(event: SensorEvent) {
                val z = event.values[2]
                if (z > -3f) {
                    wasUp = true
                    downSince = 0L
                } else if (z < -7f) {
                    if (wasUp) {
                        val now = SystemClock.elapsedRealtime()
                        if (downSince == 0L) {
                            downSince = now
                        } else if (now - downSince >= 400L) {
                            stopSelf()
                        }
                    }
                } else {
                    downSince = 0L
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager = sm
        flipListener = l
    }

    private fun stopFlipListener() {
        flipListener?.let { sensorManager?.unregisterListener(it) }
        flipListener = null
        sensorManager = null
    }

    override fun onDestroy() {
        ringing = false
        stopFlipListener()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** true طول ما الأذان بيرن (شاشة الأذان بتقفل لما يبقى false) */
        @Volatile
        var ringing = false
    }
}
