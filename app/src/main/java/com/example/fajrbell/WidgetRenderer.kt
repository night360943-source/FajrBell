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

/** بيرسم الودجت كله كصورة: دايرة رصاصي مهشّرة + مواقيت الصلوات + الوقت المتبقي */
object WidgetRenderer {

    // ===== الألوان (تقدر تغيّرها) =====
    private val SKY = Color.parseColor("#0A8FD4")      // الخلفية سماوي
    private val GRAY = Color.parseColor("#A7AEB5")     // الدايرة رصاصي
    private val RED = Color.parseColor("#E53935")      // تنبيه قرب الصلاة
    private val YELLOW = Color.parseColor("#FFD60A")   // الوقت المتبقي
    private const val WARN_MS = 5 * 60_000L            // ينقلب أحمر قبل الصلاة بـ 5 دقايق

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
        val next = window.second   // الصلاة القادمة (سواء جرسها شغال أو مقفول)

        val span = (next.time.time - prev.time.time).coerceAtLeast(1L)
        // يبدأ فاضي عند بداية الفترة ويمتلئ كله عند دخول وقت الصلاة القادمة
        val progress = ((now - prev.time.time).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        val remaining = next.time.time - now

        val density = context.resources.displayMetrics.density
        val wf = w.toFloat()
        val hf = h.toFloat()

        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.color = SKY
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

    private fun fullRing(cx: Float, cy: Float, outerR: Float, innerR: Float): Path {
        val p = Path()
        p.fillType = Path.FillType.EVEN_ODD
        p.addCircle(cx, cy, outerR, Path.Direction.CW)
        p.addCircle(cx, cy, innerR, Path.Direction.CW)
        return p
    }

    private fun drawRing(
        c: Canvas, r: RectF, progress: Float, remaining: Long, next: FajrScheduler.Slot
    ) {
        val cx = r.centerX()
        val cy = r.centerY()
        val outerR = r.width() / 2f
        val innerR = outerR * 0.72f
        val outer = RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
        val inner = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)

        // رصاصي، وبيتحول أحمر لما يفضل 5 دقايق أو أقل
        val ringColor = if (remaining <= WARN_MS) RED else GRAY

        // الجزء اللي لسه ماعداش من الوقت: رصاصي مصمت
        val solid = Paint(Paint.ANTI_ALIAS_FLAG)
        solid.color = ringColor
        solid.style = Paint.Style.FILL
        if (progress < 0.002f) {
            c.drawPath(fullRing(cx, cy, outerR, innerR), solid)
        } else if (progress < 0.999f) {
            val sweep = 360f * progress
            val rest = Path()
            rest.arcTo(outer, -90f + sweep, 360f - sweep)
            rest.arcTo(inner, 270f, -(360f - sweep))
            rest.close()
            c.drawPath(rest, solid)
        }

        // الجزء اللي عدى من الوقت: تهشير شفاف (خطوط مائلة بينها فراغ شفاف)
        if (progress > 0.002f) {
            val elapsed: Path
            if (progress >= 0.999f) {
                elapsed = fullRing(cx, cy, outerR, innerR)
            } else {
                val sweep = 360f * progress
                elapsed = Path()
                elapsed.arcTo(outer, -90f, sweep)
                elapsed.arcTo(inner, -90f + sweep, -sweep)
                elapsed.close()
            }

            c.save()
            c.clipPath(elapsed)
            val hatch = Paint(Paint.ANTI_ALIAS_FLAG)
            hatch.color = ringColor
            hatch.style = Paint.Style.STROKE
            hatch.strokeWidth = max(2f, outerR * 0.04f)
            val step = max(6f, outerR * 0.11f)
            var x = cx - outerR * 3f
            while (x < cx + outerR) {
                c.drawLine(x, cy + outerR, x + outerR * 2f, cy - outerR, hatch)
                x += step
            }
            c.restore()

            val edge = Paint(Paint.ANTI_ALIAS_FLAG)
            edge.color = ringColor
            edge.style = Paint.Style.STROKE
            edge.strokeWidth = max(1.5f, outerR * 0.015f)
            c.drawPath(elapsed, edge)
        }

        // النص جوه الدايرة: اسم الصلاة، وقت الأذان كبير، والمتبقي بالأصفر
        val numSize = innerR * 0.56f
        val apSize = numSize * 0.42f
        val nameSize = innerR * 0.22f
        val remSize = innerR * 0.27f

        c.drawText(
            "صلاة " + next.prayer.ar, cx, cy - numSize * 0.80f,
            textPaint(nameSize, false, Color.WHITE, Paint.Align.CENTER)
        )

        val num = Fmt.clock(next.time)
        val ap = Fmt.ampm(next.time)
        val numPaint = textPaint(numSize, true, Color.WHITE, Paint.Align.LEFT)
        val apPaint = textPaint(apSize, true, Color.WHITE, Paint.Align.LEFT)
        val gap = numSize * 0.12f
        val total = numPaint.measureText(num) + gap + apPaint.measureText(ap)
        val x0 = cx - total / 2f
        val numBase = cy + numSize * 0.15f
        c.drawText(num, x0, numBase, numPaint)
        c.drawText(ap, x0 + numPaint.measureText(num) + gap, numBase, apPaint)

        c.drawText(
            "باقي " + Fmt.hm(remaining), cx, numBase + remSize * 1.55f,
            textPaint(remSize, true, YELLOW, Paint.Align.CENTER)
        )
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
            val col = if (isNext) YELLOW else Color.argb(alpha, 255, 255, 255)
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
