package com.example.fajrbell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * بيرسم الودجت كله كصورة:
 * فوق (لبني): التاريخ الهجري والميلادي + الدايرة. تحت (رصاصي): مواعيد الصلوات.
 */
object WidgetRenderer {

    // ===== الألوان (تقدر تغيّرها) =====
    private val BABY_TOP = Color.parseColor("#1F6599")      // خارج الدايرة: نفس كحلي كبسولة "باقي" بس أفتح شوية
    private val BABY_BOTTOM = Color.parseColor("#18527F")
    private val PANEL_TOP = Color.parseColor("#7B848C")     // الرصاصي تحت
    private val PANEL_BOTTOM = Color.parseColor("#59626A")
    private val NAVY = Color.parseColor("#0B3A5B")          // نصوص فوق
    private val BLUE = Color.parseColor("#4C9FD6")          // التهشير: نفس أزرق الخلفية (أغمق شوية عشان يبان)
    private val TRACK = Color.argb(235, 46, 53, 59)         // الرصاصي الغامق
    private val RED = Color.parseColor("#E53935")           // تنبيه
    private val RED_TRACK = Color.argb(235, 211, 47, 47)
    private val YELLOW = Color.parseColor("#FFD60A")        // الوقت المتبقي
    private const val WARN_MS = 5 * 60_000L                 // أحمر قبل الصلاة بـ 5 دقايق

    private fun textPaint(size: Float, bold: Boolean, color: Int, align: Paint.Align): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = size
        p.color = color
        p.textAlign = align
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return p
    }

    /** بيصغّر الخط لحد ما النص يتسع في العرض المتاح */
    private fun fit(text: String, size: Float, maxW: Float, bold: Boolean, color: Int, align: Paint.Align): Paint {
        var s = size
        var p = textPaint(s, bold, color, align)
        while (p.measureText(text) > maxW && s > 6f) {
            s *= 0.93f
            p = textPaint(s, bold, color, align)
        }
        return p
    }

    private fun roundedPath(rect: RectF, tl: Float, tr: Float, br: Float, bl: Float): Path {
        val p = Path()
        p.addRoundRect(rect, floatArrayOf(tl, tl, tr, tr, br, br, bl, bl), Path.Direction.CW)
        return p
    }

    private fun fullRing(cx: Float, cy: Float, outerR: Float, innerR: Float): Path {
        val p = Path()
        p.fillType = Path.FillType.EVEN_ODD
        p.addCircle(cx, cy, outerR, Path.Direction.CW)
        p.addCircle(cx, cy, innerR, Path.Direction.CW)
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
        val progress = ((now - prev.time.time).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        val remaining = next.time.time - now

        val density = context.resources.displayMetrics.density
        val wf = w.toFloat()
        val hf = h.toFloat()
        val wDp = wf / density
        val hDp = hf / density
        val pad = min(wf, hf) * 0.045f
        val corner = min(wf, hf) * 0.09f

        val showPanel = hDp >= 130f      // لو أقصر من كده: دايرة بس
        val useColumns = wDp >= 200f     // عريض: الصلوات في صف، ضيق: في عمود
        val panelH = when {
            !showPanel -> 0f
            useColumns -> (hf * 0.30f).coerceIn(38f * density, 84f * density)
            else -> hf * 0.40f
        }
        val topH = hf - panelH

        // ----- المنطقة اللبني (فوق) -----
        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        topPaint.shader = LinearGradient(0f, 0f, 0f, topH, BABY_TOP, BABY_BOTTOM, Shader.TileMode.CLAMP)
        val topRect = RectF(0f, 0f, wf, topH)
        if (showPanel) {
            c.drawPath(roundedPath(topRect, corner, corner, 0f, 0f), topPaint)
        } else {
            c.drawPath(roundedPath(topRect, corner, corner, corner, corner), topPaint)
        }

        // ----- المنطقة الرصاصي (تحت) -----
        if (showPanel) {
            val pp = Paint(Paint.ANTI_ALIAS_FLAG)
            pp.shader = LinearGradient(0f, topH, 0f, hf, PANEL_TOP, PANEL_BOTTOM, Shader.TileMode.CLAMP)
            c.drawPath(roundedPath(RectF(0f, topH - 1f, wf, hf), 0f, 0f, corner, corner), pp)
        }

        // ----- التاريخ: هجري على الشمال وميلادي على اليمين -----
        val headerH = (hf * 0.105f).coerceIn(13f * density, 24f * density)
        val dateSize = headerH * 0.62f
        val half = (wf - 3f * pad) / 2f
        val hijri = Fmt.hijri(Prefs.hijriAdjust(context))
        val greg = Fmt.gregorian()
        val dateBase = pad * 0.6f + headerH * 0.72f
        c.drawText(hijri, pad * 1.6f, dateBase, fit(hijri, dateSize, half, true, Color.WHITE, Paint.Align.LEFT))
        c.drawText(greg, wf - pad * 1.6f, dateBase, fit(greg, dateSize, half, false, Color.WHITE, Paint.Align.RIGHT))

        // ----- الدايرة -----
        val ringTop = pad * 0.6f + headerH + pad * 0.4f
        val ringBottom = topH - pad
        val side = min(ringBottom - ringTop, wf - 2f * pad).coerceAtLeast(40f)
        val left = (wf - side) / 2f
        drawRing(c, RectF(left, ringTop, left + side, ringTop + side), progress, remaining, next)

        // ----- مواعيد الصلوات تحت الدايرة -----
        if (showPanel) {
            drawPanel(c, RectF(0f, topH, wf, hf), pad, useColumns, next, now)
        }
        return bmp
    }

    private fun drawRing(
        c: Canvas, r: RectF, progress: Float, remaining: Long, next: FajrScheduler.Slot
    ) {
        val cx = r.centerX()
        val cy = r.centerY()
        val outerR = r.width() / 2f
        val innerR = outerR * 0.74f
        val outer = RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
        val inner = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)

        val warn = remaining <= WARN_MS
        val trackColor = if (warn) RED_TRACK else TRACK
        val hatchColor = if (warn) RED else BLUE

        // جوه الدايرة: أبيض مصمت بلون واحد
        val disc = Paint(Paint.ANTI_ALIAS_FLAG)
        disc.color = Color.WHITE
        c.drawCircle(cx, cy, innerR, disc)

        // الجزء اللي لسه ماعداش: رصاصي شفاف مصمت (من غير تهشير)
        val solid = Paint(Paint.ANTI_ALIAS_FLAG)
        solid.color = trackColor
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

        // الجزء اللي عدى: تهشير أزرق (بينه فراغات شفافة)
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
            hatch.color = hatchColor
            hatch.style = Paint.Style.STROKE
            hatch.strokeWidth = max(2f, outerR * 0.045f)
            val step = max(6f, outerR * 0.12f)
            var x = cx - outerR * 3f
            while (x < cx + outerR) {
                c.drawLine(x, cy + outerR, x + outerR * 2f, cy - outerR, hatch)
                x += step
            }
            c.restore()

            val edge = Paint(Paint.ANTI_ALIAS_FLAG)
            edge.color = hatchColor
            edge.style = Paint.Style.STROKE
            edge.strokeWidth = max(1.5f, outerR * 0.018f)
            c.drawPath(elapsed, edge)
        }

        // حافة بيضا رفيعة بره وجوه الدايرة
        val rim = Paint(Paint.ANTI_ALIAS_FLAG)
        rim.style = Paint.Style.STROKE
        rim.color = Color.argb(230, 255, 255, 255)
        rim.strokeWidth = max(1.5f, outerR * 0.028f)
        c.drawCircle(cx, cy, outerR, rim)
        c.drawCircle(cx, cy, innerR, rim)

        // ----- النص جوه الدايرة -----
        val numSize = innerR * 0.50f
        val apSize = numSize * 0.42f
        val nameSize = innerR * 0.20f
        val remSize = innerR * 0.24f

        // اسم الصلاة
        c.drawText(
            "صلاة " + next.prayer.ar, cx, cy - numSize * 0.85f,
            textPaint(nameSize, false, NAVY, Paint.Align.CENTER)
        )

        // وقت الأذان: الرقم الكبير
        val num = Fmt.clock(next.time)
        val ap = Fmt.ampm(next.time)
        val numPaint = textPaint(numSize, true, NAVY, Paint.Align.LEFT)
        val apPaint = textPaint(apSize, true, NAVY, Paint.Align.LEFT)
        val gap = numSize * 0.12f
        val total = numPaint.measureText(num) + gap + apPaint.measureText(ap)
        val x0 = cx - total / 2f
        val numBase = cy + numSize * 0.12f
        c.drawText(num, x0, numBase, numPaint)
        c.drawText(ap, x0 + numPaint.measureText(num) + gap, numBase, apPaint)

        // الوقت المتبقي بالأصفر داخل كبسولة كحلي
        val remText = "باقي " + Fmt.hm(remaining)
        val remPaint = textPaint(remSize, true, YELLOW, Paint.Align.CENTER)
        val remBase = numBase + remSize * 1.7f
        val pw = remPaint.measureText(remText) + remSize * 0.9f
        val pill = RectF(cx - pw / 2f, remBase - remSize * 1.2f, cx + pw / 2f, remBase + remSize * 0.55f)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        pillPaint.color = NAVY
        c.drawRoundRect(pill, pill.height() / 2f, pill.height() / 2f, pillPaint)
        c.drawText(remText, cx, remBase, remPaint)
    }

    private fun drawPanel(
        c: Canvas, r: RectF, pad: Float, columns: Boolean, next: FajrScheduler.Slot, now: Long
    ) {
        val shown = FajrScheduler.displayTimes(now)
        val prayers = Prayer.values()
        val ph = r.height()

        if (columns) {
            // صف أفقي: الفجر على اليمين والعشاء على الشمال
            val colW = (r.width() - 2f * pad) / 5f
            val timeSz = min(ph * 0.27f, colW * 0.26f)
            val apSz = timeSz * 0.55f
            val nameSz = min(ph * 0.22f, colW * 0.24f)
            for (i in prayers.indices) {
                val p = prayers[i]
                val t = shown.getValue(p)
                val isNext = (p == next.prayer) && (t.time == next.time.time)
                val cx = r.right - pad - colW * (i + 0.5f)

                if (isNext) {
                    val hi = Paint(Paint.ANTI_ALIAS_FLAG)
                    hi.color = Color.argb(60, 255, 255, 255)
                    c.drawRoundRect(
                        RectF(cx - colW * 0.47f, r.top + ph * 0.07f, cx + colW * 0.47f, r.bottom - ph * 0.07f),
                        ph * 0.2f, ph * 0.2f, hi
                    )
                }
                val alpha = if (t.time < now) 150 else 255
                val col = if (isNext) YELLOW else Color.argb(alpha, 255, 255, 255)

                c.drawText(p.ar, cx, r.top + ph * 0.40f, textPaint(nameSz, isNext, col, Paint.Align.CENTER))

                val numPaint = textPaint(timeSz, true, col, Paint.Align.LEFT)
                val apPaint = textPaint(apSz, true, col, Paint.Align.LEFT)
                val num = Fmt.clock(t)
                val ap = Fmt.ampm(t)
                val gap = timeSz * 0.1f
                val total = numPaint.measureText(num) + gap + apPaint.measureText(ap)
                val x0 = cx - total / 2f
                val base = r.top + ph * 0.80f
                c.drawText(num, x0, base, numPaint)
                c.drawText(ap, x0 + numPaint.measureText(num) + gap, base, apPaint)
            }
        } else {
            // عمود: اسم الصلاة على اليمين والوقت على الشمال
            val rowH = (ph - pad) / 5f
            val size = min(rowH * 0.52f, r.width() * 0.12f)
            for (i in prayers.indices) {
                val p = prayers[i]
                val t = shown.getValue(p)
                val isNext = (p == next.prayer) && (t.time == next.time.time)
                val top = r.top + pad * 0.3f + rowH * i

                if (isNext) {
                    val hi = Paint(Paint.ANTI_ALIAS_FLAG)
                    hi.color = Color.argb(60, 255, 255, 255)
                    c.drawRoundRect(
                        RectF(r.left + pad, top + rowH * 0.06f, r.right - pad, top + rowH * 0.94f),
                        rowH * 0.25f, rowH * 0.25f, hi
                    )
                }
                val alpha = if (t.time < now) 150 else 255
                val col = if (isNext) YELLOW else Color.argb(alpha, 255, 255, 255)
                val base = top + rowH / 2f + size * 0.35f
                c.drawText(
                    p.ar, r.right - pad * 1.8f, base,
                    textPaint(size, isNext, col, Paint.Align.RIGHT)
                )
                c.drawText(
                    Fmt.time(t), r.left + pad * 1.8f, base,
                    textPaint(size, true, col, Paint.Align.LEFT)
                )
            }
        }
    }
}
