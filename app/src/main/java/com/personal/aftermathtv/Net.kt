package com.personal.aftermathtv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL

object Net {
    /** GET that follows redirects, including http -> https. */
    fun getBytes(start: String): ByteArray {
        var u = start
        repeat(6) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 Chrome/120 Safari/537.36")
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw Exception("redirect with no Location")
                    u = URL(URL(u), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) throw Exception("HTTP $code from $u")
                return c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        }
        throw Exception("too many redirects: $start")
    }

    fun get(u: String): String = String(getBytes(u), Charsets.UTF_8)

    fun bitmap(u: String): Bitmap? = try {
        val b = getBytes(u)
        BitmapFactory.decodeByteArray(b, 0, b.size)
    } catch (e: Exception) { null }
}
