package com.example.fajrbell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Date
import kotlin.math.max
import kotlin.math.min

/**
 * بيرسم الودجت كله كصورة:
 * فوق (أزرق): التاريخ + مستطيل فيه (الساعة الآن، الصلاة القادمة ووقتها، المتبقي).
 * تحت (رصاصي): مواعيد الصلوات الخمس.
 */
object WidgetRenderer {

    // ===== الألوان (تقدر تغيّرها) =====
    private val SKY_TOP = Color.parseColor("#1F6599")       // الخلفية الزرقا فوق
    private val SKY_BOTTOM = Color.parseColor("#18527F")
    private val PANEL_TOP = Color.parseColor("#6C757D")     // الشريط الرصاصي تحت
    private val PANEL_BOTTOM = Color.parseColor("#535B62")
    private val NAVY = Color.parseColor("#0B3A5B")          // النصوص جوه المستطيل
    private val NAVY_SOFT = Color.parseColor("#4A6A85")
    private val FRAME_GRAY = Color.parseColor("#7B848C")    // الوقت المتبقي (رصاصي)
    private val FRAME_BLUE = Color.parseColor("#9AD3F3")    // الوقت اللي عدى (لبني سادة)
    private val RED = Color.parseColor("#E53935")           // تنبيه قرب الصلاة
    private val YELLOW = Color.parseColor("#FFD60A")
    private val DIVIDER = Color.parseColor("#D3DEE9")
    const val WARN_MS = 10 * 60_000L                        // أحمر قبل الصلاة بـ 10 دقايق

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

        val showPanel = hDp >= 130f      // لو أقصر من كده: المستطيل بس
        val useColumns = wDp >= 200f     // عريض: الصلوات في صف، ضيق: في عمود
        val panelH = when {
            !showPanel -> 0f
            useColumns -> (hf * 0.27f).coerceIn(36f * density, 80f * density)
            else -> hf * 0.40f
        }
        val topH = hf - panelH

        // ----- المنطقة الزرقا (فوق) -----
        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        topPaint.shader = LinearGradient(0f, 0f, 0f, topH, SKY_TOP, SKY_BOTTOM, Shader.TileMode.CLAMP)
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
        val headerH = (hf * 0.105f).coerceIn(13f * density, 22f * density)
        val dateSize = headerH * 0.62f
        val half = (wf - 3f * pad) / 2f
        val hijri = Fmt.hijri(Prefs.hijriAdjust(context))
        val greg = Fmt.gregorian()
        val dateBase = pad * 0.6f + headerH * 0.72f
        c.drawText(hijri, pad * 1.6f, dateBase, fit(hijri, dateSize, half, true, Color.WHITE, Paint.Align.LEFT))
        c.drawText(greg, wf - pad * 1.6f, dateBase, fit(greg, dateSize, half, false, Color.WHITE, Paint.Align.RIGHT))

        // ----- المستطيل -----
        val cardTop = pad * 0.6f + headerH + pad * 0.4f
        val cardBottom = topH - pad
        val card = RectF(pad, cardTop, wf - pad, max(cardBottom, cardTop + 40f))
        drawCard(c, card, progress, remaining, next, now, density)

        // ----- مواعيد الصلوات تحت المستطيل -----
        if (showPanel) {
            drawPanel(c, RectF(0f, topH, wf, hf), pad, useColumns, next, now)
        }
        return bmp
    }

    /** رقم الساعة كبير + ص/م صغيرة، في النص */
    private fun drawTime(
        c: Canvas, num: String, ap: String, cx: Float, base: Float,
        numSize: Float, color: Int, maxW: Float
    ) {
        var ns = numSize
        var aps = ns * 0.45f
        val numP = textPaint(ns, true, color, Paint.Align.LEFT)
        val apP = textPaint(aps, true, color, Paint.Align.LEFT)
        var gap = ns * 0.12f
        var total = numP.measureText(num) + gap + apP.measureText(ap)
        if (maxW > 0f && total > maxW) {
            val f = maxW / total
            ns *= f
            aps *= f
            gap *= f
            numP.textSize = ns
            apP.textSize = aps
            total = numP.measureText(num) + gap + apP.measureText(ap)
        }
        val x0 = cx - total / 2f
        c.drawText(num, x0, base, numP)
        c.drawText(ap, x0 + numP.measureText(num) + gap, base, apP)
    }

    private fun drawCard(
        c: Canvas, r: RectF, progress: Float, remaining: Long,
        next: FajrScheduler.Slot, nowMs: Long, density: Float
    ) {
        val warn = remaining <= WARN_MS
        val grayC = if (warn) RED else FRAME_GRAY
        val blueC = if (warn) RED else FRAME_BLUE

        val t = max(4f, r.height() * 0.07f)                       // سمك الإطار
        val rOuter = min(r.width(), r.height()) * 0.14f

        // الخلفية البيضا جوه الإطار
        val inner = RectF(r.left + t, r.top + t, r.right - t, r.bottom - t)
        val rin = max(2f, rOuter - t)
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        whitePaint.color = Color.WHITE
        c.drawRoundRect(inner, rin, rin, whitePaint)

        // مسار الإطار: يبدأ من منتصف الحافة العلوية مع عقارب الساعة
        val h2 = t / 2f
        val r2 = RectF(r.left + h2, r.top + h2, r.right - h2, r.bottom - h2)
        val rr = max(2f, rOuter - h2)
        val path = Path()
        path.moveTo(r2.centerX(), r2.top)
        path.lineTo(r2.right - rr, r2.top)
        path.arcTo(RectF(r2.right - 2f * rr, r2.top, r2.right, r2.top + 2f * rr), -90f, 90f)
        path.lineTo(r2.right, r2.bottom - rr)
        path.arcTo(RectF(r2.right - 2f * rr, r2.bottom - 2f * rr, r2.right, r2.bottom), 0f, 90f)
        path.lineTo(r2.left + rr, r2.bottom)
        path.arcTo(RectF(r2.left, r2.bottom - 2f * rr, r2.left + 2f * rr, r2.bottom), 90f, 90f)
        path.lineTo(r2.left, r2.top + rr)
        path.arcTo(RectF(r2.left, r2.top, r2.left + 2f * rr, r2.top + 2f * rr), 180f, 90f)
        path.lineTo(r2.centerX(), r2.top)

        val sp = Paint(Paint.ANTI_ALIAS_FLAG)
        sp.style = Paint.Style.STROKE
        sp.strokeWidth = t
        sp.strokeCap = Paint.Cap.BUTT
        val pm = PathMeasure(path, false)
        val len = pm.length
        val el = len * progress
        if (progress >= 0.999f) {
            // الوقت كله عدى: الإطار كله لبني
            sp.color = blueC
            c.drawPath(path, sp)
        } else {
            if (el > 0.5f) {
                val seg = Path()
                pm.getSegment(0f, el, seg, true)
                sp.color = blueC
                c.drawPath(seg, sp)
            }
            val rest = Path()
            pm.getSegment(el, len, rest, true)
            sp.color = grayC
            c.drawPath(rest, sp)
        }

        // ----- المحتوى جوه المستطيل -----
        val iw = inner.width()
        val ih = inner.height()
        val cx = inner.centerX()
        val top = inner.top

        // 1) الساعة الآن
        if (ih / density >= 95f) {
            c.drawText(
                "الساعة الآن", cx, top + ih * 0.085f,
                textPaint(ih * 0.075f, true, NAVY_SOFT, Paint.Align.CENTER)
            )
        }
        val nowDate = Date(nowMs)
        drawTime(
            c, Fmt.clock(nowDate), Fmt.ampm(nowDate), cx, top + ih * 0.33f,
            ih * 0.26f, NAVY, iw * 0.9f
        )

        // فاصل رفيع
        val line = Paint(Paint.ANTI_ALIAS_FLAG)
        line.color = DIVIDER
        line.strokeWidth = max(1f, density)
        c.drawLine(cx - iw * 0.4f, top + ih * 0.385f, cx + iw * 0.4f, top + ih * 0.385f, line)

        // 2) الصلاة القادمة ووقت أذانها: الاسم على اليمين والوقت على الشمال
        val nameText = "صلاة " + next.prayer.ar
        val num = Fmt.clock(next.time)
        val ap = Fmt.ampm(next.time)
        var tSize = ih * 0.22f
        var nSize = tSize * 0.78f
        var aSize = tSize * 0.45f
        val nameP = textPaint(nSize, true, NAVY, Paint.Align.LEFT)
        val numP = textPaint(tSize, true, NAVY, Paint.Align.LEFT)
        val apP = textPaint(aSize, true, NAVY, Paint.Align.LEFT)
        var gapName = tSize * 0.35f
        var gapAp = tSize * 0.12f
        var total = numP.measureText(num) + gapAp + apP.measureText(ap) + gapName + nameP.measureText(nameText)
        val maxW = iw * 0.94f
        if (total > maxW) {
            val f = maxW / total
            tSize *= f; nSize *= f; aSize *= f; gapName *= f; gapAp *= f
            nameP.textSize = nSize
            numP.textSize = tSize
            apP.textSize = aSize
            total = numP.measureText(num) + gapAp + apP.measureText(ap) + gapName + nameP.measureText(nameText)
        }
        val base2 = top + ih * 0.62f
        val xNum = cx - total / 2f
        val xAp = xNum + numP.measureText(num) + gapAp
        val xName = xAp + apP.measureText(ap) + gapName
        c.drawText(num, xNum, base2, numP)
        c.drawText(ap, xAp, base2, apP)
        c.drawText(nameText, xName, base2, nameP)

        // 3) المتبقي بالأصفر داخل كبسولة كحلي: "باقي 2:07"
        val timeStr = Fmt.hm(remaining)
        val label = "باقي"
        var pillH = ih * 0.29f
        var timeSize = pillH * 0.62f
        var labelSize = timeSize * 0.55f
        val timeP = textPaint(timeSize, true, YELLOW, Paint.Align.LEFT)
        val labelP = textPaint(labelSize, true, YELLOW, Paint.Align.LEFT)
        var gapR = timeSize * 0.3f
        var padX = pillH * 0.55f
        var contentW = timeP.measureText(timeStr) + gapR + labelP.measureText(label)
        val maxPill = iw * 0.92f
        if (contentW + 2f * padX > maxPill) {
            val f = maxPill / (contentW + 2f * padX)
            timeSize *= f; labelSize *= f; gapR *= f; padX *= f; pillH *= f
            timeP.textSize = timeSize
            labelP.textSize = labelSize
            contentW = timeP.measureText(timeStr) + gapR + labelP.measureText(label)
        }
        val pillTop = top + ih * 0.68f
        val pill = RectF(cx - contentW / 2f - padX, pillTop, cx + contentW / 2f + padX, pillTop + pillH)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        pillPaint.color = NAVY
        c.drawRoundRect(pill, pillH / 2f, pillH / 2f, pillPaint)
        val pillBase = pillTop + pillH / 2f + timeSize * 0.36f
        val tx = cx - contentW / 2f
        c.drawText(timeStr, tx, pillBase, timeP)
        c.drawText(label, tx + timeP.measureText(timeStr) + gapR, pillBase, labelP)
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
                val alpha = if (t.time < now) 170 else 255
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
                val alpha = if (t.time < now) 170 else 255
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
