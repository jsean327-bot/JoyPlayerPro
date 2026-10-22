package com.joyplayer.iptv

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Every player in the app is created here so all streams are requested like a normal IPTV
 * player does (VLC user agent, redirects allowed, longer timeouts). If a server refuses a
 * stream, StreamRetry tries other user agents and the other URL forms before giving up, and
 * records what the server answered so it can be shown on screen.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlayerFactory {
    val USER_AGENTS = listOf(
        "VLC/3.0.18 LibVLC/3.0.18",
        "Dalvik/2.1.0 (Linux; U; Android 13; SM-S918B Build/TP1A.220624.014)",
        "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    )

    @Volatile var uaIndex = 0

    /** Text describing the last failed stream (shown over the fullscreen player). */
    var lastDiag by mutableStateOf("")

    fun create(ctx: Context): ExoPlayer {
        val dataFactory = DataSource.Factory {
            DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENTS[uaIndex.coerceIn(0, USER_AGENTS.lastIndex)])
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .createDataSource()
        }
        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, dataFactory)))
            .build()
    }

    private val liveRx = Regex("""^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\d+)(?:\.\w+)?$""")
    private val maskRx = Regex("""^(https?://[^/]+)/(?:(live|movie|series)/)?([^/]+)/([^/]+)/([^/?]+)""")

    /** The URL itself first, then the other forms a live channel can have on an Xtream server. */
    fun variants(u: String): List<String> {
        val m = liveRx.find(u) ?: return listOf(u)
        val (b, user, pass, id) = m.destructured
        val all = listOf(
            "$b/live/$user/$pass/$id.m3u8",
            "$b/live/$user/$pass/$id.ts",
            "$b/$user/$pass/$id"
        )
        return listOf(u) + all.filter { it != u }
    }

    /** Hides username and password so a screenshot of the error can be shared safely. */
    fun mask(u: String): String {
        val m = maskRx.find(u) ?: return u.substringAfter("://").take(60)
        val host = m.groupValues[1].substringAfter("://")
        val kind = m.groupValues[2].ifEmpty { "" }
        val tail = m.groupValues[5]
        return host + (if (kind.isNotEmpty()) "/$kind" else "") + "/•••/•••/" + tail
    }

    /** "HTTP 403 (IO_BAD_HTTP_STATUS)" when the server refused, otherwise just the error name. */
    fun describe(e: PlaybackException): String {
        val name = e.errorCodeName.removePrefix("ERROR_CODE_")
        var t: Throwable? = e.cause
        while (t != null) {
            if (t is HttpDataSource.InvalidResponseCodeException) return "HTTP ${t.responseCode} ($name)"
            t = t.cause
        }
        return name
    }

    fun handleError(
        ctx: Context, player: ExoPlayer, error: PlaybackException, cur: String,
        retries: HashMap<String, StreamRetry>
    ) {
        val io = error.errorCode in 2000..2999
        var r = retries[cur]
        if (r != null && r.exhausted && System.currentTimeMillis() - r.exhaustedAt > 30_000L) r = null
        if (r == null && io) {
            val fresh = StreamRetry(cur)
            fresh.urls.forEach { retries[it] = fresh }
            r = fresh
        }
        if (io && r != null && r.next(player, error)) return
        val extra = if (lastDiag.isNotEmpty()) "  (details on the fullscreen player)" else ""
        Toast.makeText(ctx, "Playback failed: ${describe(error)}$extra", Toast.LENGTH_LONG).show()
    }
}

/** One failing stream: walks through (user agent x URL form) combinations, logging each answer. */
class StreamRetry(cur: String) {
    val urls: List<String> = PlayerFactory.variants(cur)
    private val combos: List<Pair<String, Int>>
    private val log = mutableListOf<String>()
    private var i = 0
    var exhaustedAt = 0L
        private set
    val exhausted: Boolean get() = i >= combos.size

    init {
        val n = PlayerFactory.USER_AGENTS.size
        val start = PlayerFactory.uaIndex
        combos = (0 until n).flatMap { k ->
            val ua = (start + k) % n
            urls.map { it to ua }
        }
    }

    /** True when another attempt was started; false when everything has been tried. */
    fun next(player: ExoPlayer, error: PlaybackException): Boolean {
        if (exhausted) return false
        val c = combos[i]
        log += "${PlayerFactory.describe(error)}  [UA ${c.second + 1}]  ${PlayerFactory.mask(c.first)}"
        i++
        val n = combos.getOrNull(i)
        if (n == null) {
            exhaustedAt = System.currentTimeMillis()
            PlayerFactory.uaIndex = combos[0].second
            PlayerFactory.lastDiag = "The server refused this stream (${log.size} tries):\n" +
                    log.takeLast(9).joinToString("\n")
            return false
        }
        PlayerFactory.uaIndex = n.second
        player.setMediaItem(MediaItem.fromUri(n.first))
        player.prepare()
        player.playWhenReady = true
        return true
    }
}
