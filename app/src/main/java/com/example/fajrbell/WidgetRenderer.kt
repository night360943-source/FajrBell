package com.example.fajrbell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/** بيرسم الودجت كله كصورة: دايرة مهشّرة + مواقيت الصلوات + الوقت المتبقي */
object WidgetRenderer {

    private val GREEN = Color.parseColor("#0B6E4F")
    private val GOLD = Color.parseColor("#F2C14E")

    private fun textPaint(size: Float, bold: Boolean, color: Int, align: Paint.Align): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = size
        p.color = color
        p.textAlign = align
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return p
    }

    fun render(context: Context, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val now = System.currentTimeMillis()
        val window = FajrScheduler.window(now)
        val prev = window.first
        val next = window.second

        val span = (next.time.time - prev.time.time).coerceAtLeast(1L)
        // يبدأ فاضي عند بداية الفترة ويمتلئ كله عند دخول وقت الصلاة القادمة
        val progress = ((now - prev.time.time).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        val remaining = next.time.time - now

        val density = context.resources.displayMetrics.density
        val wf = w.toFloat()
        val hf = h.toFloat()

        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.color = GREEN
        val corner = min(wf, hf) * 0.10f
        c.drawRoundRect(RectF(0f, 0f, wf, hf), corner, corner, bg)

        val pad = min(wf, hf) * 0.06f
        val ratio = wf / hf
        val hDp = hf / density

        val ring: RectF
        var list: RectF? = null
        if (ratio >= 1.45f) {
            // عريض: الدايرة على الشمال والمواقيت على اليمين
            val side = min(hf - 2 * pad, wf * 0.45f)
            val top = (hf - side) / 2f
            ring = RectF(pad, top, pad + side, top + side)
            list = RectF(pad * 2 + side, pad, wf - pad, hf - pad)
        } else if (hDp >= 200f) {
            // طويل أو كبير: الدايرة فوق والمواقيت تحتها
            val side = min(wf - 2 * pad, (hf - 2 * pad) * 0.5f)
            val left = (wf - side) / 2f
            ring = RectF(left, pad, left + side, pad + side)
            list = RectF(pad, pad * 2 + side, wf - pad, hf - pad)
        } else {
            // صغير: الدايرة بس
            val side = min(wf, hf) - 2 * pad
            val left = (wf - side) / 2f
            val top = (hf - side) / 2f
            ring = RectF(left, top, left + side, top + side)
        }

        drawRing(c, ring, progress, remaining, next)
        if (list != null) drawList(c, list, next, now)
        return bmp
    }

    private fun drawRing(
        c: Canvas, r: RectF, progress: Float, remaining: Long, next: FajrScheduler.Slot
    ) {
        val cx = r.centerX()
        val cy = r.centerY()
        val outerR = r.width() / 2f
        val innerR = outerR * 0.72f

        // المجرى الأبيض
        val track = Path()
        track.fillType = Path.FillType.EVEN_ODD
        track.addCircle(cx, cy, outerR, Path.Direction.CW)
        track.addCircle(cx, cy, innerR, Path.Direction.CW)
        val white = Paint(Paint.ANTI_ALIAS_FLAG)
        white.color = Color.argb(240, 255, 255, 255)
        c.drawPath(track, white)

        // الجزء المهشّر (بيكبر مع الوقت)
        val sector = Path()
        if (progress >= 0.999f) {
            sector.fillType = Path.FillType.EVEN_ODD
            sector.addCircle(cx, cy, outerR, Path.Direction.CW)
            sector.addCircle(cx, cy, innerR, Path.Direction.CW)
        } else if (progress > 0.002f) {
            val sweep = 360f * progress
            val outer = RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
            val inner = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
            sector.arcTo(outer, -90f, sweep)
            sector.arcTo(inner, -90f + sweep, -sweep)
            sector.close()
        }
        if (!sector.isEmpty) {
            c.save()
            c.clipPath(sector)
            val hatch = Paint(Paint.ANTI_ALIAS_FLAG)
            hatch.color = GREEN
            hatch.style = Paint.Style.STROKE
            hatch.strokeWidth = max(2f, outerR * 0.035f)
            val step = max(6f, outerR * 0.11f)
            var x = cx - outerR * 3f
            while (x < cx + outerR) {
                c.drawLine(x, cy + outerR, x + outerR * 2f, cy - outerR, hatch)
                x += step
            }
            c.restore()

            val edge = Paint(Paint.ANTI_ALIAS_FLAG)
            edge.color = GREEN
            edge.style = Paint.Style.STROKE
            edge.strokeWidth = max(2f, outerR * 0.02f)
            c.drawPath(sector, edge)
        }

        // النص جوه الدايرة
        val bigSize = innerR * 0.55f
        val nameSize = innerR * 0.27f
        val subSize = innerR * 0.22f
        c.drawText(
            "صلاة " + next.prayer.ar, cx, cy - bigSize * 0.55f,
            textPaint(nameSize, false, Color.WHITE, Paint.Align.CENTER)
        )
        c.drawText(
            Fmt.hm(remaining), cx, cy + bigSize * 0.32f,
            textPaint(bigSize, true, Color.WHITE, Paint.Align.CENTER)
        )
        if (innerR > 55f) {
            c.drawText(
                "الأذان " + Fmt.time(next.time), cx, cy + bigSize * 0.32f + subSize * 1.6f,
                textPaint(subSize, false, GOLD, Paint.Align.CENTER)
            )
        }
    }

    private fun drawList(c: Canvas, r: RectF, next: FajrScheduler.Slot, now: Long) {
        val shown = FajrScheduler.displayTimes(now)
        val rowH = r.height() / 5f
        val size = min(rowH * 0.48f, r.width() * 0.11f)
        var i = 0
        for (p in Prayer.values()) {
            val t = shown.getValue(p)
            val isNext = (p == next.prayer) && (t.time == next.time.time)
            val top = r.top + rowH * i

            if (isNext) {
                val hi = Paint(Paint.ANTI_ALIAS_FLAG)
                hi.color = Color.argb(55, 255, 255, 255)
                c.drawRoundRect(
                    RectF(r.left, top + rowH * 0.08f, r.right, top + rowH * 0.92f),
                    rowH * 0.25f, rowH * 0.25f, hi
                )
            }

            val alpha = if (t.time < now) 150 else 255
            val col = if (isNext) GOLD else Color.argb(alpha, 255, 255, 255)
            val base = top + rowH / 2f + size * 0.35f
            c.drawText(
                p.ar, r.right - rowH * 0.25f, base,
                textPaint(size, isNext, col, Paint.Align.RIGHT)
            )
            c.drawText(
                Fmt.time(t), r.left + rowH * 0.25f, base,
                textPaint(size, isNext, col, Paint.Align.LEFT)
            )
            i++
        }
    }
}
