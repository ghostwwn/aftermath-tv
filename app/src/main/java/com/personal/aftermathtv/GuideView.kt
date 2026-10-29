package com.personal.aftermathtv

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Early-2000s satellite-box guide look. Live video sits in the PiP rect (drawn by the PlayerView above). */
class GuideView(ctx: Context) : View(ctx) {
    companion object {
        const val WINDOW_MIN = 180
        const val VISIBLE_ROWS = 7
        fun pip(w: Int, h: Int): Rect {
            val l = (0.60f * w).toInt()
            val t = (0.035f * h).toInt()
            val pw = (0.37f * w).toInt()
            val ph = pw * 9 / 16
            return Rect(l, t, l + pw, t + ph)
        }
    }

    var rows: List<GuideRow> = emptyList()
    var rowIdx = 0
    private var firstRow = 0
    var tunedIdx = 0
    var focusTime = System.currentTimeMillis()
    var meta: Meta? = null
    var poster: Bitmap? = null
    var loadingMeta = false
    var scheduleError = ""
    var onSelect: ((Program?) -> Unit)? = null

    private var windowStart = floor30(System.currentTimeMillis())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Runnable { invalidate() }
    private var u = 1f   // scale factor: 1.0 at 1080p, 2.0 at 4K
    private var th = GuideTheme.current()

    private val tFmtAp = SimpleDateFormat("h:mm a", Locale.US)
    private val tFmt = SimpleDateFormat("h:mm", Locale.US)
    private val colFmt = SimpleDateFormat("h:mma", Locale.US)
    private val clockFmt = SimpleDateFormat("h:mma EEE MM/dd", Locale.US)
    private val dateFmt = SimpleDateFormat("EEE MM/dd", Locale.US)

    private fun floor30(t: Long) = t - (t % (30 * 60000L))

    fun resetFocus(idx: Int) {
        rowIdx = idx.coerceIn(0, maxOf(0, rows.size - 1))
        firstRow = if (rowIdx < VISIBLE_ROWS) 0 else rowIdx - VISIBLE_ROWS + 1
        focusTime = System.currentTimeMillis()
        windowStart = floor30(focusTime)
        invalidate()
    }

    fun selected(): Program? {
        val r = rows.getOrNull(rowIdx) ?: return null
        return r.programs.firstOrNull { focusTime >= it.start && focusTime < it.end }
            ?: r.programs.minByOrNull { Math.abs(it.start - focusTime) }
    }

    fun move(dx: Int, dy: Int) {
        if (dy != 0) {
            rowIdx = (rowIdx + dy).coerceIn(0, maxOf(0, rows.size - 1))
            if (rowIdx < firstRow) firstRow = rowIdx
            else if (rowIdx >= firstRow + VISIBLE_ROWS) firstRow = rowIdx - VISIBLE_ROWS + 1
        }
        if (dx != 0) {
            val r = rows.getOrNull(rowIdx)
            val s = selected()
            if (r != null && s != null) {
                if (dx > 0) r.programs.firstOrNull { it.start >= s.end }?.let { focusTime = it.start }
                else r.programs.lastOrNull { it.end <= s.start }?.let { focusTime = it.start }
            }
        }
        if (focusTime < windowStart) windowStart = floor30(focusTime)
        else if (focusTime >= windowStart + (WINDOW_MIN - 30) * 60000L) windowStart = floor30(focusTime - 60 * 60000L)
        onSelect?.invoke(selected())
        invalidate()
    }

    // ---------- drawing helpers ----------

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
                    bold: Boolean = true, align: Paint.Align = Paint.Align.LEFT,
                    maxW: Float = 0f, shadow: Boolean = false, italic: Boolean = false) {
        tp.textSize = size
        tp.color = color
        tp.typeface = when {
            bold && italic -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
            italic -> Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            bold -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
        tp.isFakeBoldText = false
        tp.textAlign = align
        if (maxW > 0f) {
            val m = tp.measureText(s)
            if (m > maxW) tp.textSize = size * maxW / m
        }
        if (shadow) tp.setShadowLayer(2.5f * u, 0f, 1.5f * u, Color.argb(170, 0, 30, 100)) else tp.clearShadowLayer()
        c.drawText(s, x, y, tp)
        tp.clearShadowLayer()
    }

    private fun gradRect(c: Canvas, r: RectF, top: Int, bottom: Int) {
        paint.shader = LinearGradient(0f, r.top, 0f, r.bottom, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(r, paint)
        paint.shader = null
    }

    private fun borderRect(c: Canvas, r: RectF) {
        if (th.cellBorder == 0) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * u
        paint.color = th.cellBorder
        c.drawRect(r, paint)
        paint.style = Paint.Style.FILL
    }

    private fun cellText(c: Canvas, s: String, r: RectF, size: Float, color: Int, bold: Boolean, shadow: Boolean) {
        val wd = (r.width() - 14f * u).toInt()
        if (wd < 20) return
        tp.textSize = size
        tp.color = color
        tp.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tp.isFakeBoldText = false
        tp.textAlign = Paint.Align.LEFT
        if (shadow) tp.setShadowLayer(2.5f * u, 0f, 1.5f * u, Color.argb(190, 0, 50, 10)) else tp.clearShadowLayer()
        val sl = StaticLayout.Builder.obtain(s, 0, s.length, tp, wd)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build()
        c.save()
        c.clipRect(r)
        c.translate(r.left + 7f * u, r.centerY() - sl.height / 2f)
        sl.draw(c)
        c.restore()
        tp.clearShadowLayer()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        u = h / 1080f
        th = GuideTheme.current()
        val now = System.currentTimeMillis()

        paint.shader = LinearGradient(0f, 0f, 0f, h, th.bgTop, th.bgBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        if (th.infoPanel != 0) {
            paint.color = th.infoPanel
            c.drawRect(0f, 0.135f * h, w, 0.432f * h, paint)
        }

        // wordmark, list pill and clock (top-left, like the real guide's logo block)
        tp.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        tp.textSize = 0.052f * h
        tp.color = Color.WHITE
        tp.textAlign = Paint.Align.LEFT
        tp.isFakeBoldText = false
        val mark = Config.WORDMARK
        c.drawText(mark, 0.03f * w, 0.082f * h, tp)
        val markW = tp.measureText(mark)
        val lx = 0.03f * w + markW + 0.025f * w
        txt(c, "List:", lx, 0.055f * h, 0.028f * h, Color.WHITE, false, italic = true)
        val pillText = "All Channels"
        tp.textSize = 0.028f * h
        tp.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        val pillW = tp.measureText(pillText) + 0.02f * w
        val px0 = lx + 0.055f * w
        paint.color = th.pill
        c.drawRoundRect(RectF(px0, 0.027f * h, px0 + pillW, 0.067f * h), 6f * u, 6f * u, paint)
        txt(c, pillText, px0 + 0.01f * w, 0.0565f * h, 0.028f * h, Color.WHITE, true, shadow = true, italic = true)
        txt(c, clockFmt.format(Date(now)).lowercase(Locale.US), lx, 0.098f * h, 0.03f * h, Color.WHITE, true)

        // PiP frame
        val pip = pip(width, height)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * u
        paint.color = Color.rgb(175, 205, 245)
        c.drawRect(RectF(pip).apply { inset(-2f * u, -2f * u) }, paint)
        paint.style = Paint.Style.FILL

        drawInfo(c, w, h)
        drawGrid(c, w, h)

        removeCallbacks(tick)
        postDelayed(tick, 30000)
    }

    private fun drawInfo(c: Canvas, w: Float, h: Float) {
        // poster (small, left)
        val px = 0.03f * w
        val py = 0.15f * h
        val pw = 0.10f * w
        val ph = 0.265f * h
        val bm = poster
        if (bm != null && bm.width > 0 && bm.height > 0) {
            val sc = minOf(pw / bm.width, ph / bm.height)
            val dw = bm.width * sc
            val dh = bm.height * sc
            val dx = px + (pw - dw) / 2f
            c.drawBitmap(bm, null, RectF(dx, py, dx + dw, py + dh), paint)
        } else {
            paint.color = Color.argb(70, 0, 0, 30)
            c.drawRect(px, py, px + pw, py + ph, paint)
            txt(c, if (loadingMeta) "..." else "NO IMAGE", px + pw / 2f, py + ph / 2f, 0.02f * h,
                Color.argb(150, 255, 255, 255), true, Paint.Align.CENTER)
        }

        // centered title + info lines, like the real guide
        val tx = 0.145f * w
        val tw = 0.585f * w - tx
        val cx = tx + tw / 2f
        val sel = selected()
        val row = rows.getOrNull(rowIdx)

        if (sel == null) {
            txt(c, row?.label ?: "", cx, 0.185f * h, 0.042f * h, Color.WHITE, true, Paint.Align.CENTER, tw)
            val msg = if (scheduleError.isNotEmpty() && (row?.id == "est" || row?.id == "pst")) scheduleError else (row?.note ?: "")
            drawParagraph(c, msg, tx, 0.205f * h, tw, 0.42f * h, h)
            return
        }

        val shown = if (sel.lookup.isNotEmpty() && sel.lookup != "-") sel.lookup else sel.title
        txt(c, shown, cx, 0.185f * h, 0.042f * h, Color.WHITE, true, Paint.Align.CENTER, tw)

        val m = meta
        val yr = if (sel.year > 0) sel.year.toString() else (m?.year ?: "")
        val l2 = ArrayList<String>()
        if (sel.block.isNotEmpty()) l2.add(sel.block.uppercase(Locale.US) + ".")
        l2.add(tFmt.format(Date(sel.start)) + " - " + tFmtAp.format(Date(sel.end)) + ".")
        if (yr.isNotEmpty()) l2.add("($yr)")
        if (m != null && m.rating.isNotEmpty()) l2.add(m.rating + ".")
        if (m != null && m.genres.isNotEmpty()) l2.add(m.genres + ".")
        val l3 = "Episode: " + (if (sel.episode.isNotEmpty()) sel.episode else "N/A")
        val plot = m?.plot ?: ""
        val tail = when {
            plot.isNotEmpty() || sel.note.isNotEmpty() ->
                listOf(sel.note, plot).filter { it.isNotEmpty() }.joinToString("  ")
            loadingMeta -> "Loading info..."
            else -> "No description found for this title."
        }
        drawParagraph(c, l2.joinToString(" ") + "\n" + l3 + "\n" + tail, tx, 0.205f * h, tw, 0.42f * h, h)
    }

    private fun drawParagraph(c: Canvas, text: String, x: Float, y: Float, wd: Float, bottom: Float, h: Float) {
        tp.textSize = 0.0255f * h
        tp.color = Color.WHITE
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.isFakeBoldText = false
        tp.textAlign = Paint.Align.LEFT
        tp.clearShadowLayer()
        val lines = ((bottom - y) / (tp.fontSpacing)).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, wd.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
    }

    private fun drawGrid(c: Canvas, w: Float, h: Float) {
        val labelX = 0.03f * w
        val labelW = 0.13f * w
        val gx0 = labelX + labelW
        val gx1 = 0.97f * w
        val cols = WINDOW_MIN / 30
        val colW = (gx1 - gx0) / cols
        val ws = windowStart
        val we = ws + WINDOW_MIN * 60000L
        val ppm = (gx1 - gx0) / (WINDOW_MIN * 60000f)
        val headerTop = 0.44f * h
        val headerH = 0.043f * h
        val top0 = headerTop + headerH
        val step = 0.0705f * h
        val g = th.cellInset * u
        val gridBottom = top0 + VISIBLE_ROWS * step

        paint.color = th.gridBg
        c.drawRect(labelX, headerTop, gx1, gridBottom, paint)

        // header row
        val dateR = RectF(labelX + g, headerTop + g, gx0 - g, top0 - g)
        gradRect(c, dateR, th.dateTop, th.dateBottom)
        borderRect(c, dateR)
        txt(c, dateFmt.format(Date(ws)), labelX + 12f * u, headerTop + headerH * 0.70f, 0.026f * h, Color.WHITE, true, maxW = labelW - 20f * u, italic = true)
        for (k in 0 until cols) {
            val x0 = gx0 + k * colW
            val hr = RectF(x0 + g, headerTop + g, x0 + colW - g, top0 - g)
            gradRect(c, hr, th.headTop, th.headBottom)
            borderRect(c, hr)
            txt(c, colFmt.format(Date(ws + k * 30 * 60000L)).lowercase(Locale.US), x0 + 12f * u, headerTop + headerH * 0.70f, 0.025f * h, Color.WHITE, true, italic = true)
        }

        for ((i, r) in rows.withIndex()) {
            if (i < firstRow || i >= firstRow + VISIBLE_ROWS) continue
            val top = top0 + (i - firstRow) * step
            val isSel = i == rowIdx

            // channel cell (sky blue)
            val lr = RectF(labelX + g, top + g, gx0 - g, top + step - g)
            gradRect(c, lr, th.chanTop, th.chanBottom)
            borderRect(c, lr)
            txt(c, "HD", lr.left + 8f * u, lr.centerY() + 0.006f * h, 0.017f * h, Color.WHITE, true, shadow = true, italic = true)
            val num = r.label.substringBefore(' ')
            val nm = r.label.substringAfter(' ')
            txt(c, num, lr.right - 10f * u, top + step * 0.47f, 0.029f * h, Color.WHITE, true, Paint.Align.RIGHT, labelW * 0.5f, true)
            txt(c, nm, lr.right - 10f * u, top + step * 0.86f, 0.0195f * h, Color.WHITE, true, Paint.Align.RIGHT, labelW - 52f * u, true)

            if (r.programs.isEmpty()) {
                val rect = RectF(gx0 + g, top + g, gx1 - g, top + step - g)
                if (isSel) gradRect(c, rect, th.selTop, th.selBottom) else gradRect(c, rect, th.cellTop, th.cellBottom)
                borderRect(c, rect)
                val msg = if (scheduleError.isNotEmpty() && (r.id == "est" || r.id == "pst")) "SCHEDULE UNAVAILABLE" else r.note
                cellText(c, msg, rect, 0.0245f * h, if (isSel) th.selText else th.cellText, isSel || th.cellBold, isSel)
                continue
            }

            val selP = if (isSel) selected() else null
            for (p in r.programs) {
                if (p.end <= ws || p.start >= we) continue
                val x0 = gx0 + (maxOf(p.start, ws) - ws) * ppm
                val x1 = gx0 + (minOf(p.end, we) - ws) * ppm
                if (x1 - x0 < 8f * u) continue
                val rect = RectF(x0 + g, top + g, x1 - g, top + step - g)
                val chosen = selP != null && selP.start == p.start
                if (chosen) gradRect(c, rect, th.selTop, th.selBottom) else gradRect(c, rect, th.cellTop, th.cellBottom)
                borderRect(c, rect)

                // shows that began before the window get a "(HH:MM)" time-left prefix, like the real guide
                var label = p.title
                if (p.start < ws) {
                    val rem = ((p.end - ws) / 60000L).toInt().coerceAtLeast(0)
                    label = String.format(Locale.US, "(%02d:%02d) %s", rem / 60, rem % 60, p.title)
                }
                cellText(c, label, rect, 0.0245f * h, if (chosen) th.selText else th.cellText, chosen || th.cellBold, chosen)

                paint.color = if (chosen) th.selText else th.arrow
                val ay = rect.centerY()
                val aw = 7f * u
                val ah = 9f * u
                if (p.start < ws) {
                    val pl = Path()
                    pl.moveTo(rect.left + 5f * u + aw, ay - ah); pl.lineTo(rect.left + 5f * u, ay); pl.lineTo(rect.left + 5f * u + aw, ay + ah); pl.close()
                    c.drawPath(pl, paint)
                }
                if (p.end > we) {
                    val pr = Path()
                    pr.moveTo(rect.right - 5f * u - aw, ay - ah); pr.lineTo(rect.right - 5f * u, ay); pr.lineTo(rect.right - 5f * u - aw, ay + ah); pr.close()
                    c.drawPath(pr, paint)
                }
            }
        }

        // scroll arrows (more channels above/below)
        paint.color = Color.argb(230, 255, 255, 255)
        val ax = 0.985f * w
        if (firstRow > 0) {
            val y0 = top0 + 6f * u
            val pa = Path()
            pa.moveTo(ax, y0); pa.lineTo(ax - 10f * u, y0 + 16f * u); pa.lineTo(ax + 10f * u, y0 + 16f * u); pa.close()
            c.drawPath(pa, paint)
        }
        if (firstRow + VISIBLE_ROWS < rows.size) {
            val y1 = gridBottom - 6f * u
            val pb = Path()
            pb.moveTo(ax, y1); pb.lineTo(ax - 10f * u, y1 - 16f * u); pb.lineTo(ax + 10f * u, y1 - 16f * u); pb.close()
            c.drawPath(pb, paint)
        }
    }
}
