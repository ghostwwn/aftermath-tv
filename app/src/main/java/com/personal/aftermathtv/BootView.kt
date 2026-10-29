package com.personal.aftermathtv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View

/** Old-CRT power-on: black, a white line stretches out, grows into a full white flash, then fades away. */
class BootView(ctx: Context) : View(ctx) {
    private val paint = Paint()
    private var t0 = 0L
    private var revealed = false
    private var onReveal: (() -> Unit)? = null
    private val total = 1750L
    private val white = Color.rgb(240, 248, 255)

    fun play(onReveal: () -> Unit) {
        this.onReveal = onReveal
        revealed = false
        t0 = SystemClock.uptimeMillis()
        visibility = VISIBLE
        invalidate()
    }

    private fun ease(x: Float): Float {
        val p = x.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val u = h / 1080f
        val t = SystemClock.uptimeMillis() - t0
        val cy = h / 2f
        when {
            t < 350 -> c.drawColor(Color.BLACK)
            t < 750 -> {
                c.drawColor(Color.BLACK)
                val half = (w / 2f) * ease((t - 350) / 400f)
                paint.color = Color.argb(70, 200, 225, 255)
                c.drawRect(w / 2f - half, cy - 10f * u, w / 2f + half, cy + 10f * u, paint)
                paint.color = white
                c.drawRect(w / 2f - half, cy - 2.5f * u, w / 2f + half, cy + 2.5f * u, paint)
            }
            t < 1050 -> {
                c.drawColor(Color.BLACK)
                val half = 2.5f * u + (h / 2f - 2.5f * u) * ease((t - 750) / 300f)
                paint.color = white
                c.drawRect(0f, cy - half, w, cy + half, paint)
            }
            t < 1250 -> c.drawColor(white)
            else -> {
                if (!revealed) {
                    revealed = true
                    post { onReveal?.invoke() }
                }
                val a = (255 * (1f - ((t - 1250) / (total - 1250f)).coerceIn(0f, 1f))).toInt()
                if (a <= 0) {
                    post { visibility = GONE }
                    return
                }
                c.drawColor(Color.argb(a, 240, 248, 255))
            }
        }
        postInvalidateOnAnimation()
    }
}
