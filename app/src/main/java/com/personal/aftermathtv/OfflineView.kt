package com.personal.aftermathtv

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * "This stream is offline" card: a bright 90s-cartoon style scene with chunky outlined
 * 3D shapes tumbling over a scrolling grid floor. Entirely original artwork, and it
 * loops seamlessly every LOOP_MS.
 */
class OfflineView(ctx: Context) : View(ctx) {
    private class Mesh(val v: Array<FloatArray>, val f: Array<IntArray>)
    private class Obj(
        val mesh: Mesh, val color: Int, val size: Float,
        val cx: Float, val cy: Float, val ax: Float, val ay: Float,
        val kx: Int, val ky: Int, val ph: Float,
        val rx: Int, val ry: Int, val z: Float
    )

    private val cube = Mesh(
        arrayOf(
            floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f), floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
            floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f)
        ),
        arrayOf(
            intArrayOf(0, 1, 2, 3), intArrayOf(5, 4, 7, 6), intArrayOf(4, 0, 3, 7),
            intArrayOf(1, 5, 6, 2), intArrayOf(3, 2, 6, 7), intArrayOf(4, 5, 1, 0)
        )
    )
    private val octa = Mesh(
        arrayOf(
            floatArrayOf(1.3f, 0f, 0f), floatArrayOf(-1.3f, 0f, 0f), floatArrayOf(0f, 1.3f, 0f),
            floatArrayOf(0f, -1.3f, 0f), floatArrayOf(0f, 0f, 1.3f), floatArrayOf(0f, 0f, -1.3f)
        ),
        arrayOf(
            intArrayOf(0, 2, 4), intArrayOf(2, 1, 4), intArrayOf(1, 3, 4), intArrayOf(3, 0, 4),
            intArrayOf(2, 0, 5), intArrayOf(1, 2, 5), intArrayOf(3, 1, 5), intArrayOf(0, 3, 5)
        )
    )
    private val tetra = Mesh(
        arrayOf(
            floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, -1f, 1f), floatArrayOf(-1f, 1f, -1f), floatArrayOf(1f, -1f, -1f)
        ),
        arrayOf(intArrayOf(0, 1, 2), intArrayOf(0, 3, 1), intArrayOf(0, 2, 3), intArrayOf(1, 3, 2))
    )

    private val objs = listOf(
        Obj(cube, Color.rgb(255, 138, 0), 0.85f, -4.3f, 1.5f, 0.4f, 0.5f, 1, 2, 0.0f, 1, 2, 6f),
        Obj(octa, Color.rgb(0, 200, 230), 0.8f, 4.4f, 1.3f, 0.4f, 0.6f, 2, 1, 1.1f, 2, 1, 6.5f),
        Obj(tetra, Color.rgb(240, 60, 170), 1.0f, -3.2f, -0.9f, 0.5f, 0.4f, 1, 1, 2.2f, 1, 3, 5.5f),
        Obj(cube, Color.rgb(150, 220, 40), 0.7f, 3.5f, -0.8f, 0.4f, 0.5f, 2, 2, 3.3f, 3, 1, 5.5f),
        Obj(octa, Color.rgb(255, 214, 0), 0.7f, -1.4f, 2.5f, 0.5f, 0.2f, 1, 3, 4.4f, 1, 1, 7f),
        Obj(tetra, Color.rgb(140, 90, 240), 0.9f, 1.8f, 2.6f, 0.4f, 0.3f, 3, 1, 5.5f, 2, 2, 7f),
        Obj(cube, Color.rgb(240, 70, 60), 0.55f, -5.0f, -0.2f, 0.3f, 0.6f, 1, 2, 0.7f, 2, 3, 8f),
        Obj(octa, Color.rgb(255, 138, 0), 0.6f, 5.0f, -0.1f, 0.3f, 0.6f, 2, 3, 1.9f, 3, 2, 8f)
    )

    private val palette = intArrayOf(
        Color.rgb(255, 214, 0), Color.rgb(0, 200, 230), Color.rgb(240, 60, 170),
        Color.rgb(150, 220, 40), Color.rgb(255, 138, 0)
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val fillT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL); textAlign = Paint.Align.CENTER
    }
    private val strokeT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL); textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = Color.rgb(28, 8, 56)
    }

    private val loopMs = 12000L
    private val shapeScale = 0.6f
    private val t0 = System.currentTimeMillis()
    private var channel = ""
    private var detail = ""

    fun show(channelLabel: String, detail: String) {
        this.channel = channelLabel
        this.detail = detail
        if (visibility != VISIBLE) visibility = VISIBLE
        invalidate()
    }

    fun hide() {
        visibility = GONE
    }

    private fun outlined(c: Canvas, s: String, x: Float, y: Float, size: Float, fill: Int, maxW: Float, u: Float) {
        fillT.textSize = size
        var sz = size
        val m = fillT.measureText(s)
        if (m > maxW) sz = size * maxW / m
        fillT.textSize = sz
        strokeT.textSize = sz
        strokeT.strokeWidth = maxOf(sz * 0.16f, 3f * u)
        fillT.color = fill
        c.drawText(s, x, y, strokeT)
        c.drawText(s, x, y, fillT)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 2f || h < 2f) return
        val u = h / 1080f
        val elapsed = (System.currentTimeMillis() - t0) % loopMs
        val tt = (elapsed.toFloat() / loopMs) * (2f * PI.toFloat())
        val horizon = 0.68f * h

        // sky
        paint.shader = LinearGradient(0f, 0f, 0f, horizon, Color.rgb(70, 40, 170), Color.rgb(30, 160, 230), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, horizon + 1f, paint)
        // floor
        paint.shader = LinearGradient(0f, horizon, 0f, h, Color.rgb(20, 140, 160), Color.rgb(8, 80, 110), Shader.TileMode.CLAMP)
        c.drawRect(0f, horizon, w, h, paint)
        paint.shader = null

        // scrolling grid floor (loops every 3s)
        val phase = (elapsed % 3000L) / 3000f
        line.strokeWidth = 3f * u
        line.color = Color.argb(190, 255, 230, 80)
        val n = 10
        for (i in 0..n) {
            val p = (i + phase) / n
            if (p > 1f) continue
            val y = horizon + (h - horizon) * p * p
            c.drawLine(0f, y, w, y, line)
        }
        for (j in -8..8) {
            c.drawLine(w / 2f + j * w * 0.012f, horizon, w / 2f + j * w * 0.2f, h, line)
        }

        // confetti
        for (i in 0 until 16) {
            val k = 1 + (i % 2)
            val yf = ((i * 0.0625f) + k * (tt / (2f * PI.toFloat()))) % 1f
            val x = (((i * 37) % 100) / 100f) * w + 20f * u * sin(tt * k + i)
            val y = yf * horizon
            val r = (6 + (i % 4) * 3) * u
            paint.color = palette[i % palette.size]
            c.drawCircle(x, y, r, paint)
            line.strokeWidth = 2.5f * u
            line.color = Color.rgb(28, 8, 56)
            c.drawCircle(x, y, r, line)
        }

        // 3D shapes (painter's algorithm, back to front)
        val f = h
        val lx = -0.35f; val ly = 0.6f; val lz = -0.7f
        val ll = sqrt(lx * lx + ly * ly + lz * lz)
        line.strokeWidth = 5f * u
        line.color = Color.rgb(28, 8, 56)
        for (o in objs.sortedByDescending { it.z }) {
            val wx = o.cx + o.ax * sin(o.kx * tt + o.ph)
            val wy = o.cy + o.ay * sin(o.ky * tt + o.ph * 1.7f)
            val wz = o.z + 0.5f * sin(tt + o.ph)
            val a = o.rx * tt
            val b = o.ry * tt
            val ca = cos(a); val sa = sin(a); val cb = cos(b); val sb = sin(b)
            val rot = Array(o.mesh.v.size) { idx ->
                val v = o.mesh.v[idx]
                val x = v[0] * o.size * shapeScale; val y = v[1] * o.size * shapeScale; val z = v[2] * o.size * shapeScale
                val y1 = y * ca - z * sa
                val z1 = y * sa + z * ca
                val x2 = x * cb + z1 * sb
                val z2 = -x * sb + z1 * cb
                floatArrayOf(x2, y1, z2)
            }
            // draw faces far to near
            val faces = o.mesh.f.sortedByDescending { fc -> fc.map { rot[it][2] }.average() }
            for (fc in faces) {
                val p0 = rot[fc[0]]; val p1 = rot[fc[1]]; val p2 = rot[fc[2]]
                var nx = (p1[1] - p0[1]) * (p2[2] - p0[2]) - (p1[2] - p0[2]) * (p2[1] - p0[1])
                var ny = (p1[2] - p0[2]) * (p2[0] - p0[0]) - (p1[0] - p0[0]) * (p2[2] - p0[2])
                var nz = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p1[1] - p0[1]) * (p2[0] - p0[0])
                var cxf = 0f; var cyf = 0f; var czf = 0f
                for (vi in fc) { cxf += rot[vi][0]; cyf += rot[vi][1]; czf += rot[vi][2] }
                cxf /= fc.size; cyf /= fc.size; czf /= fc.size
                if (nx * cxf + ny * cyf + nz * czf < 0f) { nx = -nx; ny = -ny; nz = -nz }   // make normal point outward
                val nl = sqrt(nx * nx + ny * ny + nz * nz)
                if (nl < 1e-5f) continue
                nx /= nl; ny /= nl; nz /= nl
                // back-face culling: visible if the normal points toward the camera
                if (nx * (cxf + wx) + ny * (cyf + wy) + nz * (czf + wz) >= 0f) continue
                val shade = 0.5f + 0.5f * maxOf(0f, (nx * lx + ny * ly + nz * lz) / ll)
                val col = Color.rgb(
                    (Color.red(o.color) * shade).toInt().coerceIn(0, 255),
                    (Color.green(o.color) * shade).toInt().coerceIn(0, 255),
                    (Color.blue(o.color) * shade).toInt().coerceIn(0, 255)
                )
                val path = Path()
                var ok = true
                for ((n2, vi) in fc.withIndex()) {
                    val zz = rot[vi][2] + wz
                    if (zz < 0.5f) { ok = false; break }
                    val sx = w / 2f + (rot[vi][0] + wx) * f / zz
                    val sy = h * 0.40f - (rot[vi][1] + wy) * f / zz
                    if (n2 == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                if (!ok) continue
                path.close()
                paint.color = col
                c.drawPath(path, paint)
                c.drawPath(path, line)
            }
        }

        // text
        val bob = 0.006f * h * sin(tt)
        if (channel.isNotEmpty()) {
            outlined(c, channel, w / 2f, 0.20f * h + bob, 0.05f * h, Color.rgb(0, 230, 255), w * 0.7f, u)
        }
        outlined(c, "THIS STREAM", w / 2f, 0.37f * h + bob, 0.115f * h, Color.rgb(255, 228, 70), w * 0.9f, u)
        outlined(c, "IS OFFLINE", w / 2f, 0.51f * h + bob, 0.115f * h, Color.rgb(255, 228, 70), w * 0.9f, u)
        outlined(c, "Please check back later!", w / 2f, 0.62f * h, 0.055f * h, Color.WHITE, w * 0.85f, u)
        val dots = ".".repeat(1 + ((elapsed / 500L) % 3).toInt())
        outlined(c, "Trying to reconnect$dots", w / 2f, 0.82f * h, 0.042f * h, Color.WHITE, w * 0.8f, u)
        if (detail.isNotEmpty()) {
            fillT.textSize = 0.022f * h
            fillT.color = Color.argb(170, 255, 255, 255)
            c.drawText(detail.take(90), w / 2f, 0.97f * h, fillT)
        }

        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }
}
