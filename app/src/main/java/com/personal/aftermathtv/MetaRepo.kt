package com.personal.aftermathtv

import android.text.Html
import org.json.JSONObject
import java.net.URLEncoder

object MetaRepo {
    private val cache = HashMap<String, Meta>()

    /** OMDb key: set at startup from the saved setting, falling back to Config.OMDB_KEY. */
    @Volatile var apiKey: String = Config.OMDB_KEY

    fun clearCache() { synchronized(cache) { cache.clear() } }

    fun lookup(title: String, type: String = ""): Meta? {
        val key = title + "|" + type
        synchronized(cache) { cache[key]?.let { return it } }
        var m: Meta? = null
        if (apiKey.isNotEmpty()) {
            m = try { omdb(title, type) } catch (e: Exception) { null }
            if (m == null && type.isNotEmpty()) m = try { omdb(title, "") } catch (e: Exception) { null }
        }
        if (m == null) m = try { tvmaze(title) } catch (e: Exception) { null }
        if (m != null) synchronized(cache) { cache[key] = m }
        return m
    }

    private fun JSONObject.str(k: String): String {
        if (isNull(k)) return ""
        val s = optString(k, "")
        return if (s == "N/A") "" else s
    }

    private fun tvmaze(t: String): Meta? {
        val o = JSONObject(Net.get("https://api.tvmaze.com/singlesearch/shows?q=" + URLEncoder.encode(t, "UTF-8")))
        val plot = Html.fromHtml(o.str("summary"), Html.FROM_HTML_MODE_COMPACT).toString().trim()
        val img = o.optJSONObject("image")?.str("medium") ?: ""
        val avg = o.optJSONObject("rating")?.optDouble("average", 0.0) ?: 0.0
        val g = o.optJSONArray("genres")
        val genres = if (g == null) "" else (0 until g.length()).joinToString(" / ") { g.optString(it) }
        return Meta(
            o.str("name"), plot, o.str("premiered").take(4),
            if (avg > 0.0) "TVmaze " + String.format("%.1f", avg) else "",
            genres, img
        )
    }

    private fun omdb(t: String, type: String): Meta? {
        val o = JSONObject(Net.get(
            "https://www.omdbapi.com/?t=" + URLEncoder.encode(t, "UTF-8") +
                "&plot=short" + (if (type.equals("series", true)) "&type=series" else "") + "&apikey=" + apiKey
        ))
        if (o.optString("Response") != "True") return null
        val r = o.str("imdbRating")
        return Meta(
            o.str("Title"), o.str("Plot"), o.str("Year"),
            if (r.isNotEmpty()) "IMDb $r" else "",
            o.str("Genre").replace(", ", " / "), o.str("Poster")
        )
    }
}
