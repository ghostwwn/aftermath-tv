package com.personal.aftermathtv

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.KeyEvent
import android.view.View

/** First-run screen: enter an OMDb key with a retro on-screen keyboard, or skip. D-pad driven. */
class SetupView(ctx: Context) : View(ctx) {
    var onSave: ((String) -> Unit)? = null
    var onSkip: (() -> Unit)? = null

    private var text = ""
    private var row = 0
    private var col = 0
    private var message = ""
    private var messageOk = false
    private var busy = false
    private var cancelMode = false

    private val keys = arrayOf("1234567890ab", "cdefghijklmn", "opqrstuvwxyz")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)

    fun open(current: String, cancelMode: Boolean) {
        this.cancelMode = cancelMode
        text = ""
        row = 0; col = 0
        busy = false
        messageOk = false
        message = if (current.isNotEmpty()) "Current key: " + "*".repeat(maxOf(0, current.length - 4)) + current.takeLast(4) else ""
        visibility = VISIBLE
        invalidate()
    }

    fun setStatus(msg: String, ok: Boolean, busy: Boolean) {
        message = msg
        messageOk = ok
        this.busy = busy
        invalidate()
    }

    private fun add(ch: Char) {
        if (text.length < 24) text += ch
    }

    private fun delete() {
        if (text.isNotEmpty()) text = text.dropLast(1)
    }

    private fun press() {
        if (row < 3) {
            add(keys[row][col])
        } else when (col) {
            0 -> delete()
            1 -> text = ""
            2 -> {
                if (text.length < 4) {
                    message = "Type your OMDb key first, or press ${if (cancelMode) "CANCEL" else "SKIP"}."
                    messageOk = false
                } else onSave?.invoke(text)
            }
            else -> onSkip?.invoke()
        }
    }

    fun handleKey(code: Int, ev: KeyEvent?): Boolean {
        if (busy) return true
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> col = (col - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> col = (col + 1).coerceAtMost(if (row == 3) 3 else 11)
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (row == 3) { row = 2; col = col * 3 + 1 } else if (row > 0) row--
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (row == 2) { row = 3; col = col / 3 } else if (row < 2) row++
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> press()
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> onSkip?.invoke()
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> delete()
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> add('0' + (code - KeyEvent.KEYCODE_0))
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> add('a' + (code - KeyEvent.KEYCODE_A))
            else -> return false
        }
        invalidate()
        return true
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
                    align: Paint.Align = Paint.Align.CENTER, mono: Boolean = false, maxW: Float = 0f) {
        tp.textSize = size
        tp.color = color
        tp.typeface = if (mono) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.DEFAULT_BOLD
        tp.isFakeBoldText = false
        tp.textAlign = align
        if (maxW > 0f) {
            val m = tp.measureText(s)
            if (m > maxW) tp.textSize = size * maxW / m
        }
        c.drawText(s, x, y, tp)
    }

    private fun cell(c: Canvas, r: RectF, top: Int, bottom: Int, border: Int, u: Float) {
        paint.shader = LinearGradient(0f, r.top, 0f, r.bottom, top, bottom, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 5f * u, 5f * u, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * u
        paint.color = border
        c.drawRoundRect(r, 5f * u, 5f * u, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val u = h / 1080f
        val th = GuideTheme.current()

        paint.shader = LinearGradient(0f, 0f, 0f, h, th.bgTop, th.bgBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        tp.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        tp.textSize = 0.052f * h
        tp.color = Color.WHITE
        tp.textAlign = Paint.Align.LEFT
        c.drawText(Config.WORDMARK, 0.03f * w, 0.082f * h, tp)

        txt(c, "SHOW INFO SETUP", w / 2f, 0.165f * h, 0.052f * h, Color.WHITE)

        // explanation
        tp.textSize = 0.0265f * h
        tp.color = Color.WHITE
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.textAlign = Paint.Align.LEFT
        val body = "For IMDb ratings and posters in the guide, enter a free OMDb key from omdbapi.com. " +
            if (cancelMode) "Or choose CANCEL to keep things as they are."
            else "Or choose SKIP. The app works fine without one and uses TVmaze instead."
        val bw = (0.72f * w).toInt()
        val layout = StaticLayout.Builder.obtain(body, 0, body.length, tp, bw)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3).build()
        c.save()
        c.translate((w - bw) / 2f, 0.195f * h)
        layout.draw(c)
        c.restore()

        // key field
        val fw = 0.5f * w
        val fr = RectF((w - fw) / 2f, 0.335f * h, (w + fw) / 2f, 0.415f * h)
        paint.color = Color.rgb(6, 22, 70)
        c.drawRoundRect(fr, 6f * u, 6f * u, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * u
        paint.color = Color.rgb(170, 205, 245)
        c.drawRoundRect(fr, 6f * u, 6f * u, paint)
        paint.style = Paint.Style.FILL
        if (text.isEmpty()) {
            txt(c, "OMDb key", w / 2f, fr.centerY() + 0.015f * h, 0.042f * h, Color.argb(110, 255, 255, 255), mono = false)
        } else {
            val cursor = if ((System.currentTimeMillis() / 500L) % 2L == 0L && !busy) "_" else " "
            txt(c, text + cursor, w / 2f, fr.centerY() + 0.016f * h, 0.046f * h, Color.WHITE, mono = true, maxW = fw - 30f * u)
        }
        if (message.isNotEmpty()) {
            txt(c, message, w / 2f, 0.462f * h, 0.026f * h,
                if (messageOk) Color.rgb(190, 255, 190) else Color.rgb(255, 232, 130), maxW = 0.9f * w)
        }

        // on-screen keyboard
        val cw = 0.056f * w
        val ch = 0.07f * h
        val gap = 6f * u
        val x0 = (w - (12 * cw + 11 * gap)) / 2f
        val y0 = 0.50f * h
        for (r in 0 until 3) {
            for (k in 0 until 12) {
                val rect = RectF(x0 + k * (cw + gap), y0 + r * (ch + gap), x0 + k * (cw + gap) + cw, y0 + r * (ch + gap) + ch)
                val sel = row == r && col == k
                if (sel) cell(c, rect, th.selTop, th.selBottom, Color.WHITE, u)
                else cell(c, rect, th.cellTop, th.cellBottom, th.cellBorder.takeIf { it != 0 } ?: Color.rgb(42, 66, 162), u)
                txt(c, keys[r][k].toString().uppercase(), rect.centerX(), rect.centerY() + 0.011f * h, 0.034f * h,
                    if (sel) th.selText else th.cellText)
            }
        }
        val labels = arrayOf("DELETE", "CLEAR", "SAVE", if (cancelMode) "CANCEL" else "SKIP")
        val by = y0 + 3 * (ch + gap)
        val bw3 = 3 * cw + 2 * gap
        for (i in 0 until 4) {
            val rect = RectF(x0 + i * (bw3 + gap), by, x0 + i * (bw3 + gap) + bw3, by + ch)
            val sel = row == 3 && col == i
            val top: Int
            val bottom: Int
            val fg: Int
            if (sel) { top = th.selTop; bottom = th.selBottom; fg = th.selText }
            else if (i == 2) { top = Color.rgb(70, 170, 80); bottom = Color.rgb(44, 140, 58); fg = Color.WHITE }
            else { top = th.chanTop; bottom = th.chanBottom; fg = Color.WHITE }
            cell(c, rect, top, bottom, if (sel) Color.WHITE else Color.rgb(42, 66, 162), u)
            txt(c, labels[i], rect.centerX(), rect.centerY() + 0.011f * h, 0.03f * h, fg)
        }

        txt(c, "D-PAD: MOVE     OK: SELECT     BACK: " + (if (cancelMode) "CANCEL" else "SKIP"), w / 2f, 0.965f * h, 0.022f * h,
            Color.argb(190, 255, 255, 255))

        if (visibility == VISIBLE) postInvalidateDelayed(500)
    }
}
