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
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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

    /** > 0 while a live stream is being reconnected automatically (number of the attempt). */
    var reconnecting by mutableStateOf(0)

    // Same HTTP stack (OkHttp) the app already uses for the playlist / account calls.
    private val okClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun create(ctx: Context): ExoPlayer {
        val dataFactory = DataSource.Factory {
            OkHttpDataSource.Factory(okClient)
                .setUserAgent(USER_AGENTS[uaIndex.coerceIn(0, USER_AGENTS.lastIndex)])
                .createDataSource()
        }
        // starts playing sooner (1.5 s of picture is enough) and keeps a comfortable buffer afterwards
        // TV boxes / Fire TV sticks have a weaker Wi-Fi and little memory: longer runway, but a capped buffer size
        val tv = isTvDevice(ctx)
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(if (tv) 30_000 else 15_000, if (tv) 90_000 else 50_000, if (tv) 2_500 else 1_500, if (tv) 6_000 else 4_000)
            .setTargetBufferBytes(if (tv) 48 * 1024 * 1024 else -1)
            .setPrioritizeTimeOverSizeThresholds(!tv)
            .build()
        val renderers = androidx.media3.exoplayer.DefaultRenderersFactory(ctx)
            .setEnableDecoderFallback(true)
        val player = ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, dataFactory)))
            .setLoadControl(loadControl)
            .build()
        // preferred audio / subtitle language (Settings > Automatic)
        val pa = AppPrefs.prefAudio
        val ps = AppPrefs.prefSub
        if (pa.isNotBlank() || ps.isNotBlank()) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                if (pa.isNotBlank()) setPreferredAudioLanguage(pa)
                if (ps.isNotBlank()) setPreferredTextLanguage(ps)
            }.build()
        }
        return player
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

    /** The deepest cause, e.g. "ConnectException: Connection refused", to see why a connection died. */
    fun rootCause(e: Throwable): String {
        var t: Throwable = e
        while (t.cause != null && t.cause !== t) t = t.cause!!
        return "${t.javaClass.simpleName}: ${(t.message ?: "").take(90)}"
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
        if (reconnecting == 0) Toast.makeText(ctx, "Playback failed: ${describe(error)}$extra", Toast.LENGTH_LONG).show()
    }
}

/** One failing stream: walks through (user agent x URL form) combinations, logging each answer. */
class StreamRetry(cur: String) {
    val urls: List<String> = PlayerFactory.variants(cur)
    private val combos: List<Pair<String, Int>>
    private val log = mutableListOf<String>()
    private var i = 0
    private var lastCause = ""
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
        lastCause = PlayerFactory.rootCause(error)
        i++
        // A dead connection has nothing to do with the user agent: only try the other URL forms.
        if (error.errorCode == 2001 || error.errorCode == 2002) {
            while (i < combos.size && combos[i].second != c.second) i++
        }
        val n = combos.getOrNull(i)
        if (n == null) {
            exhaustedAt = System.currentTimeMillis()
            PlayerFactory.uaIndex = combos[0].second
            PlayerFactory.lastDiag = "Could not play this stream (${log.size} tries):\n" +
                    log.takeLast(9).joinToString("\n") + "\nCause: $lastCause"
            return false
        }
        PlayerFactory.uaIndex = n.second
        player.setMediaItem(MediaItem.fromUri(n.first))
        player.prepare()
        player.playWhenReady = true
        return true
    }
}
