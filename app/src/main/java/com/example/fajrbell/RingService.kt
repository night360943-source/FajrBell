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
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class RingService : Service() {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        val channelId = "fajr_bell"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "جرس الفجر", NotificationManager.IMPORTANCE_HIGH)
        )
        val stopPi = PendingIntent.getService(
            this, 3, Intent(this, RingService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان الآن موعد الفجر")
            .setContentText("اضغط إيقاف لإسكات الجرس")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(0, "إيقاف", stopPi)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(10, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(10, notif)
        }

        startSound()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, Prefs.duration(this) * 1000L)
        return START_NOT_STICKY
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

    private fun startSound() {
        player?.release()
        player = null
        val saved = Prefs.sound(this)?.let { Uri.parse(it) }
        val resId = resources.getIdentifier("bell", "raw", packageName)
        val raw = if (resId != 0) Uri.parse("android.resource://$packageName/$resId") else null
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        // الأولوية: النغمة اللي اخترتها > bell.mp3 > نغمة المنبه الافتراضية
        for (uri in listOfNotNull(saved, raw, fallback)) {
            try {
                player = buildPlayer(uri)
                return
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
