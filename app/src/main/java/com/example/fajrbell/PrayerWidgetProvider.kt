package com.example.fajrbell

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import kotlin.math.sqrt

class PrayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateOne(context, appWidgetManager, id)
        scheduleTick(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        updateOne(context, appWidgetManager, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        scheduleTick(context)
    }

    override fun onDisabled(context: Context) {
        cancelTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            if (refreshAll(context)) scheduleTick(context)
        }
    }

    companion object {
        const val ACTION_TICK = "com.example.fajrbell.WIDGET_TICK"

        /** بيحدّث كل الودجتس. بيرجع true لو فيه ودجت موجود */
        fun refreshAll(context: Context): Boolean {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, PrayerWidgetProvider::class.java))
            for (id in ids) updateOne(context, mgr, id)
            return ids.isNotEmpty()
        }

        fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            try {
                val opts = mgr.getAppWidgetOptions(id)
                val dm = context.resources.displayMetrics
                val portrait =
                    context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                val wDp = if (portrait) opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                else opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
                val hDp = if (portrait) opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
                else opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)

                var w = (if (wDp > 0) wDp else 180) * dm.density
                var h = (if (hDp > 0) hDp else 180) * dm.density
                val maxPixels = 900_000f
                if (w * h > maxPixels) {
                    val s = sqrt(maxPixels / (w * h))
                    w *= s
                    h *= s
                }

                val bmp = WidgetRenderer.render(
                    context, w.toInt().coerceAtLeast(80), h.toInt().coerceAtLeast(80)
                )
                val views = RemoteViews(context.packageName, R.layout.widget_prayer)
                views.setImageViewBitmap(R.id.widget_img, bmp)
                val open = PendingIntent.getActivity(
                    context, 5, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_img, open)
                mgr.updateAppWidget(id, views)
            } catch (e: Exception) {
                // ما نقفلش التطبيق بسبب الودجت
            }
        }

        private fun tickIntent(context: Context): PendingIntent {
            val i = Intent(context, PrayerWidgetProvider::class.java).setAction(ACTION_TICK)
            return PendingIntent.getBroadcast(
                context, 77, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** تحديث الودجت كل دقيقة */
        fun scheduleTick(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            val t = (System.currentTimeMillis() / 60_000L + 1) * 60_000L + 300L
            val pi = tickIntent(context)
            try {
                if (FajrScheduler.canSchedule(context)) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC, t, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC, t, pi)
                }
            } catch (e: SecurityException) {
                am.set(AlarmManager.RTC, t, pi)
            }
        }

        fun cancelTick(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))
        }
    }
}
