package com.personal.aftermathtv

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.random.Random

/** Channel-change static and (optional) faint scanlines. No vignette. */
class CrtOverlayView(ctx: Context) : View(ctx) {
    var crtOn = true
        set(v) { field = v; invalidate() }
    var staticOn = false
        set(v) { field = v; invalidate() }
    /** True while the guide is open: the guide stays clean. */
    var suppress = false
        set(v) { field = v; invalidate() }

    private val line = Paint().apply { color = Color.argb(34, 0, 0, 0) }
    private val noise = Paint()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val u = h / 1080f
        if (staticOn) {
            c.drawColor(Color.BLACK)
            val s = 8f * u
            var y = 0f
            while (y < h) {
                var x = 0f
                while (x < w) {
                    val g = Random.nextInt(255)
                    noise.color = Color.rgb(g, g, g)
                    c.drawRect(x, y, x + s, y + s, noise)
                    x += s
                }
                y += s
            }
            postInvalidateDelayed(40)
        }
        if (crtOn && !suppress && !staticOn) {
            var y = 0f
            while (y < h) { c.drawRect(0f, y, w, y + 1.2f * u, line); y += 4f * u }
        }
    }
}
