package com.personal.aftermathtv

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.view.View

/** Channel info banner shown when you change channel or press Info. */
class BannerView(ctx: Context) : View(ctx) {
    private var num = ""
    private var name = ""
    private var title = ""
    private var sub = ""
    private var time = ""
    private var progress = -1f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)

    fun update(num: String, name: String, title: String, sub: String, time: String, progress: Float) {
        this.num = num; this.name = name; this.title = title
        this.sub = sub; this.time = time; this.progress = progress
        invalidate()
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
                    align: Paint.Align, maxW: Float, shadow: Boolean = true, bold: Boolean = true) {
        tp.textSize = size
        tp.color = color
        tp.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tp.textAlign = align
        if (maxW > 0f) {
            val m = tp.measureText(s)
            if (m > maxW) tp.textSize = size * maxW / m
        }
        if (shadow) tp.setShadowLayer(3f, 0f, 2f, Color.argb(170, 0, 20, 90)) else tp.clearShadowLayer()
        c.drawText(s, x, y, tp)
        tp.clearShadowLayer()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val u = h / 1080f
        val left = 0.05f * w
        val right = 0.95f * w
        val top = 0.795f * h
        val bottom = 0.955f * h
        val bh = bottom - top

        // bar
        paint.shader = LinearGradient(0f, top, 0f, bottom, Color.rgb(44, 100, 208), Color.rgb(14, 40, 140), Shader.TileMode.CLAMP)
        c.drawRect(left, top, right, bottom, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * u
        paint.color = Color.rgb(160, 200, 248)
        c.drawRect(left, top, right, bottom, paint)
        paint.style = Paint.Style.FILL

        // channel block (sky blue, like the guide's channel column)
        val cb = 0.165f * w
        paint.shader = LinearGradient(0f, top, 0f, bottom, Color.rgb(104, 196, 252), Color.rgb(62, 160, 238), Shader.TileMode.CLAMP)
        c.drawRect(left + 3f * u, top + 3f * u, left + cb, bottom - 3f * u, paint)
        paint.shader = null
        txt(c, "HD", left + 16f * u, top + bh * 0.52f, bh * 0.15f, Color.WHITE, Paint.Align.LEFT, 0f)
        txt(c, num, left + cb - 18f * u, top + bh * 0.52f, bh * 0.42f, Color.WHITE, Paint.Align.RIGHT, cb * 0.55f)
        txt(c, name, left + cb - 18f * u, top + bh * 0.84f, bh * 0.17f, Color.WHITE, Paint.Align.RIGHT, cb - 40f * u)

        // title / subtitle
        val tx = left + cb + 0.02f * w
        val timeW = if (time.isEmpty()) 0f else 0.17f * w
        txt(c, title, tx, top + bh * 0.42f, bh * 0.27f, Color.WHITE, Paint.Align.LEFT, right - tx - timeW - 0.02f * w)
        if (sub.isNotEmpty()) {
            txt(c, sub, tx, top + bh * 0.68f, bh * 0.15f, Color.rgb(205, 228, 255), Paint.Align.LEFT, right - tx - 0.03f * w, false, false)
        }
        if (time.isNotEmpty()) {
            txt(c, time, right - 0.018f * w, top + bh * 0.42f, bh * 0.17f, Color.WHITE, Paint.Align.RIGHT, timeW)
        }

        // progress bar
        if (progress >= 0f) {
            val bx0 = tx
            val bx1 = right - 0.02f * w
            val by0 = top + bh * 0.80f
            val by1 = top + bh * 0.88f
            paint.color = Color.rgb(8, 26, 96)
            c.drawRect(bx0, by0, bx1, by1, paint)
            paint.color = Color.rgb(230, 206, 72)
            c.drawRect(bx0, by0, bx0 + (bx1 - bx0) * progress.coerceIn(0f, 1f), by1, paint)
        }
    }
}
