package com.personal.aftermathtv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private data class Channel(
        val id: String,
        val title: String,
        val direct: String = "",
        val name: String = "",
        val note: String = "",
        val sched: String = ""
    )

    private var player: ExoPlayer? = null
    private lateinit var root: FrameLayout
    private lateinit var pv: PlayerView
    private lateinit var guide: GuideView
    private lateinit var badge: TextView
    private lateinit var offline: OfflineView
    private lateinit var banner: BannerView
    private lateinit var crt: CrtOverlayView
    private lateinit var setup: SetupView
    private lateinit var boot: BootView
    private val prefs by lazy { getSharedPreferences("tv", MODE_PRIVATE) }
    private var booting = false
    private var setupOpen = false
    private var appStarted = false
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val metaIo = Executors.newFixedThreadPool(2)
    private val timeFmt = SimpleDateFormat("h:mm", Locale.US)
    private val timeFmtAp = SimpleDateFormat("h:mm a", Locale.US)

    private var channels: List<Channel> = emptyList()
    private var index = 0
    private var tuneToken = 0
    private var lastError = ""
    private var retryCount = 0
    private var retryReload = false

    private var schedule: Map<String, List<Program>> = emptyMap()
    private var scheduleAt = 0L
    private var guideOpen = false
    private var metaToken = 0

    private val hideBanner = Runnable { banner.visibility = View.GONE }
    private val hideStatic = Runnable { crt.staticOn = false }
    private val retry = Runnable { if (retryReload) loadChannelsThenTune() else tune(true) }
    private val stall = Runnable { lastError = "stream stalled"; scheduleRetry() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        guide = GuideView(this).apply {
            visibility = View.GONE
            onSelect = { p -> selectionChanged(p) }
        }
        pv = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
        }
        offline = OfflineView(this).apply { visibility = View.GONE }
        badge = TextView(this).apply {
            val th = GuideTheme.current()
            setTextColor(th.stripText)
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(th.stripBg)
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        banner = BannerView(this).apply { visibility = View.GONE }
        crt = CrtOverlayView(this)
        setup = SetupView(this).apply {
            visibility = View.GONE
            onSave = { k -> saveKey(k) }
            onSkip = { skipSetup() }
        }
        boot = BootView(this).apply { visibility = View.GONE }

        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        root.addView(guide, FrameLayout.LayoutParams(mp, mp))
        root.addView(pv, FrameLayout.LayoutParams(mp, mp))
        root.addView(offline, FrameLayout.LayoutParams(mp, mp))
        root.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        root.addView(banner, FrameLayout.LayoutParams(mp, mp))
        root.addView(crt, FrameLayout.LayoutParams(mp, mp))
        root.addView(setup, FrameLayout.LayoutParams(mp, mp))
        root.addView(boot, FrameLayout.LayoutParams(mp, mp))
        setContentView(root)

        player = ExoPlayer.Builder(this)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(20000, 60000, 2500, 5000)
                    .build()
            )
            .build().also {
                pv.player = it
                it.addListener(object : Player.Listener {
                    override fun onPlayerError(e: PlaybackException) {
                        lastError = "player: " + e.errorCodeName
                        scheduleRetry()
                    }
                    override fun onPlaybackStateChanged(s: Int) {
                        ui.removeCallbacks(stall)
                        if (s == Player.STATE_READY) {
                            lastError = ""
                            retryCount = 0
                            offline.hide()
                            ui.removeCallbacks(hideStatic)
                            ui.postDelayed(hideStatic, 600)
                        } else if (s == Player.STATE_BUFFERING) {
                            ui.postDelayed(stall, 15000)   // give up on a stuck stream and retry
                        }
                    }
                })
            }
        index = prefs.getInt("ch", 0)
        MetaRepo.apiKey = (prefs.getString("omdb_key", "") ?: "").ifEmpty { Config.OMDB_KEY }
        if (Config.BOOT_ANIMATION) {
            booting = true
            boot.play { booting = false; afterBoot() }
        } else {
            afterBoot()
        }
    }

    // ---------- startup + OMDb key setup ----------

    private fun afterBoot() {
        val prompted = prefs.getBoolean("setup_done", false)
        if (MetaRepo.apiKey.isEmpty() && !prompted) openSetup(false) else startApp()
    }

    private fun startApp() {
        if (appStarted) return
        appStarted = true
        crt.staticOn = true
        loadChannelsThenTune()
    }

    private fun openSetup(fromSettings: Boolean) {
        if (guideOpen) closeGuide()
        setupOpen = true
        player?.volume = 0f
        setup.open(prefs.getString("omdb_key", "") ?: "", fromSettings)
    }

    private fun closeSetup() {
        setupOpen = false
        setup.visibility = View.GONE
        player?.volume = 1f
        startApp()
    }

    private fun skipSetup() {
        prefs.edit().putBoolean("setup_done", true).apply()
        closeSetup()
    }

    private fun saveKey(k: String) {
        setup.setStatus("Checking key...", false, true)
        io.execute {
            val result = try {
                val o = JSONObject(Net.get("https://www.omdbapi.com/?i=tt0096895&apikey=" + URLEncoder.encode(k, "UTF-8")))
                if (o.optString("Response") == "True") "ok" else o.optString("Error", "rejected")
            } catch (e: Exception) {
                if ((e.message ?: "").contains("HTTP 401")) "Invalid API key!" else "offline"
            }
            ui.post {
                if (result == "ok" || result == "offline") {
                    prefs.edit().putString("omdb_key", k).putBoolean("setup_done", true).apply()
                    MetaRepo.apiKey = k
                    MetaRepo.clearCache()
                    val msg = if (result == "ok") "Key accepted!" else "Couldn't check the key (offline?). Saved anyway."
                    setup.setStatus(msg, true, true)
                    ui.postDelayed({ closeSetup() }, 1100)
                } else {
                    setup.setStatus("OMDb says: $result  (new keys must be activated from the email link first)", false, false)
                }
            }
        }
    }

    private fun loadChannelsThenTune() {
        io.execute {
            val main: List<Channel> = try {
                val body = Net.get(Config.CHANNELS_URL).trim()
                val arr = if (body.startsWith("[")) JSONArray(body) else JSONObject(body).getJSONArray("channels")
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    val id = o.optString("id", o.optString("shortName", o.optString("name", "")))
                    val title = o.optString("title", o.optString("name", id))
                    Channel(id, title)
                }.filter { it.id.isNotEmpty() }
            } catch (e: Exception) {
                lastError = "channel list: " + (e.message ?: e.javaClass.simpleName)
                emptyList()
            }
            channels = main + Config.EXTRA_CHANNELS.map {
                Channel(it.id, it.title, it.hlsUrl, it.shortName, it.note, it.scheduleAsset)
            }
            ui.post {
                if (channels.isEmpty() && Config.STREAM_URL_OVERRIDE.isEmpty()) scheduleRetry(true)
                else {
                    if (index !in channels.indices) index = 0
                    tune()
                    refreshSchedule()
                }
            }
        }
    }

    private fun refreshSchedule() {
        io.execute {
            val s = HashMap<String, List<Program>>(Schedule.fetch())
            for (ch in channels) {
                if (ch.sched.isNotEmpty()) {
                    try {
                        s[ch.id] = Schedule.loadWeekly(assets, ch.sched)
                    } catch (e: Exception) {
                        lastError = "schedule: " + (e.message ?: e.javaClass.simpleName)
                    }
                }
            }
            ui.post {
                if (s.isNotEmpty()) { schedule = s; scheduleAt = System.currentTimeMillis() }
                if (guideOpen) {
                    guide.rows = buildRows()
                    guide.scheduleError = Schedule.error
                    selectionChanged(guide.selected())
                    guide.invalidate()
                }
            }
        }
    }

    private fun channelLabel(): String {
        val ch = channels.getOrNull(index) ?: return "AFTERMATH TV"
        return "%02d %s".format(index + 1, shortName(ch))
    }

    /** Stream failed or stalled: show the offline card and retry with a growing delay. */
    private fun scheduleRetry(reloadList: Boolean = false) {
        crt.staticOn = false
        ui.removeCallbacks(hideStatic)
        ui.removeCallbacks(stall)
        ui.removeCallbacks(retry)
        offline.show(channelLabel(), lastError)
        retryReload = reloadList
        val delays = longArrayOf(3000, 5000, 8000, 12000, 20000, 30000)
        val d = delays[minOf(retryCount, delays.size - 1)]
        retryCount++
        ui.postDelayed(retry, d)
    }

    private fun parseStreamResponse(raw: String): String {
        val t = raw.trim()
        if (t.startsWith("{")) {
            val o = JSONObject(t)
            val u = o.optString("url", o.optString("streamUrl", ""))
            if (u.isNotEmpty()) return u
        }
        return t.trim('"')
    }

    /** auto = true when this is a background retry (keeps the offline card up instead of flashing static). */
    private fun tune(auto: Boolean = false) {
        val token = ++tuneToken
        val ch = channels.getOrNull(index)
        if (!auto) {
            ui.removeCallbacks(retry)
            offline.hide()
            retryCount = 0
            crt.staticOn = true
            showBanner()
        }
        io.execute {
            try {
                val url = if (ch != null && ch.direct.isNotEmpty()) ch.direct
                else if (Config.STREAM_URL_OVERRIDE.isNotEmpty()) Config.STREAM_URL_OVERRIDE
                else parseStreamResponse(Net.get(Config.STREAM_API + "?channelName=" + URLEncoder.encode(ch!!.id, "UTF-8")))
                if (url.isEmpty()) throw Exception("empty stream URL")
                ui.post { if (token == tuneToken) play(url, ch != null && ch.direct.isNotEmpty()) }
            } catch (e: Exception) {
                lastError = "stream: " + (e.message ?: e.javaClass.simpleName)
                ui.post { if (token == tuneToken) scheduleRetry() }
            }
        }
    }

    /** direct = third-party HLS channel: stay further behind live so slow servers stall less. */
    private fun play(url: String, direct: Boolean) {
        val p = player ?: return
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setDefaultRequestProperties(Config.HEADERS)
        val isHls = url.contains(".m3u8", ignoreCase = true) || direct
        val item = MediaItem.Builder().setUri(url).apply {
            if (direct) {
                setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(20000)
                        .setMinOffsetMs(10000)
                        .setMaxOffsetMs(45000)
                        .build()
                )
            }
        }.build()
        val policy = DefaultLoadErrorHandlingPolicy(6)
        val src = if (isHls) {
            HlsMediaSource.Factory(http)
                .setLoadErrorHandlingPolicy(policy)
                .createMediaSource(item)
        } else {
            DefaultMediaSourceFactory(this)
                .setDataSourceFactory(http)
                .setLoadErrorHandlingPolicy(policy)
                .createMediaSource(item)
        }
        p.setMediaSource(src)
        p.prepare()
        p.playWhenReady = true
    }

    private fun changeChannel(delta: Int) {
        if (channels.size < 2) return
        index = (index + delta + channels.size) % channels.size
        prefs.edit().putInt("ch", index).apply()
        tune()
    }

    // ---------- guide ----------

    private fun shortName(ch: Channel): String = if (ch.name.isNotEmpty()) ch.name else when (ch.id) {
        "est" -> "AFTERMATH EAST"
        "pst" -> "AFTERMATH WEST"
        "movies" -> "MOVIES"
        "radio" -> "RADIO"
        else -> ch.title.uppercase(Locale.US)
    }

    private fun noteFor(ch: Channel): String = if (ch.note.isNotEmpty()) ch.note else when (ch.id) {
        "movies" -> "24/7 MOVIES  -  NO LISTINGS PUBLISHED"
        "radio" -> "24/7 TOONAMI RADIO"
        else -> "NO SCHEDULE DATA YET"
    }

    private fun buildRows(): List<GuideRow> = channels.mapIndexed { i, ch ->
        GuideRow(ch.id, "%02d %s".format(i + 1, shortName(ch)), schedule[ch.id] ?: emptyList(), noteFor(ch))
    }

    private fun layoutPlayer(pip: Boolean) {
        if (pip && (root.width == 0 || root.height == 0)) {
            root.post { if (guideOpen) layoutPlayer(true) }
            return
        }
        val lp = pv.layoutParams as FrameLayout.LayoutParams
        val olp = offline.layoutParams as FrameLayout.LayoutParams
        if (pip) {
            val r = GuideView.pip(root.width, root.height)
            for (p in arrayOf(lp, olp)) {
                p.width = r.width(); p.height = r.height()
                p.leftMargin = r.left; p.topMargin = r.top
                p.gravity = Gravity.TOP or Gravity.START
            }
            val bl = badge.layoutParams as FrameLayout.LayoutParams
            val bh = (0.045f * root.height).toInt()
            bl.width = r.width(); bl.height = bh
            bl.leftMargin = r.left; bl.topMargin = r.bottom - bh
            badge.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.027f * root.height)
            badge.setPadding((0.012f * root.width).toInt(), 0, 0, 0)
            badge.layoutParams = bl
            badge.visibility = View.VISIBLE
        } else {
            for (p in arrayOf(lp, olp)) {
                p.width = ViewGroup.LayoutParams.MATCH_PARENT
                p.height = ViewGroup.LayoutParams.MATCH_PARENT
                p.leftMargin = 0; p.topMargin = 0
            }
            badge.visibility = View.GONE
        }
        pv.layoutParams = lp
        offline.layoutParams = olp
        crt.suppress = pip
    }

    private fun openGuide() {
        if (channels.isEmpty()) return
        guideOpen = true
        ui.removeCallbacks(hideBanner)
        banner.visibility = View.GONE
        guide.rows = buildRows()
        guide.tunedIdx = index
        badge.text = channelLabel()
        guide.scheduleError = Schedule.error
        guide.resetFocus(index)
        guide.visibility = View.VISIBLE
        layoutPlayer(true)
        selectionChanged(guide.selected())
        if (System.currentTimeMillis() - scheduleAt > 20 * 60000L) refreshSchedule()
    }

    private fun closeGuide() {
        guideOpen = false
        guide.visibility = View.GONE
        layoutPlayer(false)
    }

    private fun selectionChanged(p: Program?) {
        val token = ++metaToken
        guide.meta = null
        guide.poster = null
        if (p == null) { guide.loadingMeta = false; guide.invalidate(); return }
        guide.loadingMeta = true
        guide.invalidate()
        metaIo.execute {
            val q = if (p.lookup.isNotEmpty()) p.lookup else p.title
            val m = if (q == "-") null else MetaRepo.lookup(q, p.type)
            val url = if (p.imageUrl.isNotEmpty()) p.imageUrl else (m?.poster ?: "")
            val bmp = if (url.isNotEmpty()) Net.bitmap(url) else null
            ui.post {
                if (token == metaToken) {
                    guide.meta = m
                    guide.poster = bmp
                    guide.loadingMeta = false
                    guide.invalidate()
                }
            }
        }
    }

    // ---------- info banner ----------

    private fun nowPlaying(ch: Channel?): Program? {
        if (ch == null) return null
        val list = schedule[ch.id] ?: return null
        val now = System.currentTimeMillis()
        return list.firstOrNull { now >= it.start && now < it.end }
    }

    private fun showBanner() {
        if (guideOpen) return
        val ch = channels.getOrNull(index)
        val np = nowPlaying(ch)
        val num = "%02d".format(index + 1)
        val name = if (ch != null) shortName(ch) else "AFTERMATH TV"
        if (np != null) {
            val title = if (np.lookup.isNotEmpty() && np.lookup != "-") np.lookup else np.title
            val sub = listOf(np.block.uppercase(Locale.US), np.episode).filter { it.isNotEmpty() }.joinToString("  -  ")
            val range = timeFmt.format(Date(np.start)) + " - " + timeFmtAp.format(Date(np.end))
            val prog = ((System.currentTimeMillis() - np.start).toFloat() / (np.end - np.start).toFloat()).coerceIn(0f, 1f)
            banner.update(num, name, title, sub, range, prog)
        } else {
            banner.update(num, name, if (ch != null) noteFor(ch) else "", "", "", -1f)
        }
        banner.visibility = View.VISIBLE
        ui.removeCallbacks(hideBanner)
        ui.postDelayed(hideBanner, 5000)
    }

    // ---------- remote ----------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (booting) return true
        if (setupOpen) return setup.handleKey(keyCode, event)
        if (guideOpen) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> guide.move(-1, 0)
                KeyEvent.KEYCODE_DPAD_RIGHT -> guide.move(1, 0)
                KeyEvent.KEYCODE_DPAD_UP -> guide.move(0, -1)
                KeyEvent.KEYCODE_DPAD_DOWN -> guide.move(0, 1)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val r = guide.rowIdx
                    closeGuide()
                    if (r != index && r in channels.indices) {
                        index = r
                        prefs.edit().putInt("ch", index).apply()
                        tune()
                    } else showBanner()
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_GUIDE -> closeGuide()
                else -> return super.onKeyDown(keyCode, event)
            }
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_GUIDE -> { openGuide(); return true }
            KeyEvent.KEYCODE_INFO -> { showBanner(); return true }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> { openSetup(true); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_CHANNEL_UP -> { changeChannel(1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_CHANNEL_DOWN -> { changeChannel(-1); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { crt.crtOn = !crt.crtOn; return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { tune(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() { super.onStart(); player?.playWhenReady = true }
    override fun onStop() { super.onStop(); player?.playWhenReady = false }
    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        io.shutdownNow()
        metaIo.shutdownNow()
        player?.release()
        super.onDestroy()
    }
}
