package com.example.fajrbell

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** كل تعامل مع الاهتزاز في مكان واحد (مع بدائل لو طريقة ما اشتغلتش) */
object Vib {

    @Suppress("DEPRECATION")
    fun get(c: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= 31) {
                (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    fun hasVibrator(c: Context): Boolean = get(c)?.hasVibrator() == true

    private fun alarmAttrs(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    @Suppress("DEPRECATION")
    private fun run(v: Vibrator, effect: VibrationEffect) {
        try {
            v.vibrate(effect, alarmAttrs())
        } catch (e: Exception) {
            v.vibrate(effect)
        }
    }

    /** اهتزاز متكرر (نمط: اهتزاز ثانية، راحة ثانية تقريباً) لحد ما نوقفه */
    fun startRepeating(c: Context): Vibrator? {
        val v = get(c) ?: return null
        if (!v.hasVibrator()) return null
        return try {
            run(v, VibrationEffect.createWaveform(longArrayOf(0, 900, 700), 0))
            v
        } catch (e: Exception) {
            null
        }
    }

    /** اهتزازة واحدة بالمللي ثانية، بترجع true لو اتنفذت من غير خطأ */
    fun pulse(c: Context, ms: Long): Boolean {
        val v = get(c) ?: return false
        if (!v.hasVibrator()) return false
        return try {
            run(v, VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            true
        } catch (e: Exception) {
            false
        }
    }
}
