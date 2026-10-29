package com.personal.aftermathtv

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Schedule {
    @Volatile var error: String = ""

    private fun JSONObject.str(k: String): String {
        val s = optString(k, "")
        return if (s == "null") "" else s
    }

    private fun parse(s: String): Long {
        if (s.length < 19) return 0L
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return try { f.parse(s.substring(0, 19))?.time ?: 0L } catch (e: Exception) { 0L }
    }


    /** Builds upcoming programs from a bundled weekly JSON grid in the channel's own timezone. */
    fun loadWeekly(am: AssetManager, name: String): List<Program> {
        val root = JSONObject(am.open(name).bufferedReader().use { it.readText() })
        val tz = TimeZone.getTimeZone(root.optString("tz", "America/New_York"))
        val weekly = root.getJSONObject("weekly")
        val dated = root.optJSONObject("dated")
        val keys = arrayOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
        val day = Calendar.getInstance(tz)
        day.timeInMillis = System.currentTimeMillis()
        day.set(Calendar.HOUR_OF_DAY, 0); day.set(Calendar.MINUTE, 0)
        day.set(Calendar.SECOND, 0); day.set(Calendar.MILLISECOND, 0)
        day.add(Calendar.DAY_OF_YEAR, -1)
        val out = ArrayList<Program>()
        for (d in 0 until 6) {
            val arr = weekly.optJSONArray(keys[day.get(Calendar.DAY_OF_WEEK) - 1])
            val date = String.format(
                Locale.US, "%04d-%02d-%02d",
                day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)
            )
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val hm = o.getString("t").split(":")
                    val c = day.clone() as Calendar
                    c.set(Calendar.HOUR_OF_DAY, hm[0].toInt())
                    c.set(Calendar.MINUTE, hm[1].toInt())
                    val title = o.getString("title")
                    var note = o.optString("note", "")
                    val extra = datedNote(dated, date, title)
                    if (extra.isNotEmpty()) note = if (note.isEmpty()) extra else extra + "  |  " + note
                    out.add(
                        Program(
                            title, c.timeInMillis, c.timeInMillis + 25 * 60000L,
                            o.optString("ep", ""), o.optString("block", ""), 0, "series", "",
                            o.optString("lookup", ""), note
                        )
                    )
                }
            }
            day.add(Calendar.DAY_OF_YEAR, 1)
        }
        out.sortBy { it.start }
        return out.mapIndexed { i, p -> if (i + 1 < out.size) p.copy(end = out[i + 1].start) else p }
    }

    private fun datedNote(dated: JSONObject?, date: String, title: String): String {
        if (dated == null) return ""
        val cat = when {
            title == "Retro Blast Halloween" || title == "Fridays Halloween" -> "halloween"
            title == "Horror Movie" -> "horror"
            title == "Cartoon Theater" -> "theater"
            else -> return ""
        }
        val t = dated.optJSONObject(cat)?.optString(date, "") ?: ""
        if (t.isEmpty()) return ""
        return when (cat) {
            "halloween" -> "Tonight: $t"
            "horror" -> "Tonight's feature: $t"
            else -> "Today: $t"
        }
    }

    /** Returns channel id ("est"/"pst") -> sorted programs. */
    fun fetch(): Map<String, List<Program>> {
        val reqFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        reqFmt.timeZone = TimeZone.getTimeZone("UTC")
        val now = System.currentTimeMillis()
        val acc = HashMap<String, HashMap<Long, Program>>()
        var firstErr = ""
        var okCount = 0

        for (offMin in intArrayOf(-120, 60, 240, 420, 600, 780)) {
            try {
                val t = reqFmt.format(Date(now + offMin * 60000L))
                val body = Net.get(Config.SCHEDULE_URL + "?startDate=" + URLEncoder.encode(t, "UTF-8")).trim()
                val arr: JSONArray = if (body.startsWith("[")) JSONArray(body) else {
                    val o = JSONObject(body)
                    o.optJSONArray("channels") ?: o.optJSONArray("data") ?: JSONArray()
                }
                okCount++
                for (i in 0 until arr.length()) {
                    val ch = arr.optJSONObject(i) ?: continue
                    val name = ch.str("name").lowercase(Locale.US)
                    if (!name.contains("toonami")) continue
                    val key = if (name.contains("east")) "est" else if (name.contains("west")) "pst" else continue
                    val media = ch.optJSONArray("media") ?: continue
                    for (j in 0 until media.length()) {
                        val m = media.optJSONObject(j) ?: continue
                        val start = parse(m.str("startDate"))
                        if (start == 0L) continue
                        val info = m.optJSONObject("info")
                        val name2 = m.str("name")
                        val full = info?.str("fullname")?.ifEmpty { name2 } ?: name2
                        val epNum = m.optInt("episodeNumber", 0)
                        val epTitle = info?.str("episode") ?: ""
                        val episode = listOf(if (epNum > 0) "Ep. $epNum" else "", epTitle)
                            .filter { it.isNotEmpty() }.joinToString(" - ")
                        var img = ""
                        if (info != null) {
                            img = info.str("image")
                            if (img.isEmpty()) img = info.str("poster")
                            if (img.isEmpty()) img = info.str("artwork")
                        }
                        val p = Program(
                            full, start, start + 25 * 60000L, episode,
                            m.str("blockName"), info?.optInt("year", 0) ?: 0,
                            m.str("mediaType"), img
                        )
                        acc.getOrPut(key) { HashMap() }[start] = p
                    }
                }
            } catch (e: Exception) {
                if (firstErr.isEmpty()) firstErr = e.message ?: e.javaClass.simpleName
            }
        }

        error = if (okCount == 0) "schedule: $firstErr" else ""
        val out = HashMap<String, List<Program>>()
        for ((k, v) in acc) {
            val sorted = v.values.sortedBy { it.start }
            out[k] = sorted.mapIndexed { i, p ->
                if (i + 1 < sorted.size) p.copy(end = sorted[i + 1].start) else p
            }
        }
        return out
    }
}
