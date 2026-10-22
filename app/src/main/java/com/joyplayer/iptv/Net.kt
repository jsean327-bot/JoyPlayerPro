package com.joyplayer.iptv

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Every player in the app is created here so all streams are requested like a normal IPTV
 * player does (VLC user agent, redirects allowed, longer timeouts). Many IPTV panels reject
 * unknown user agents such as the default "ExoPlayerLib" one.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlayerFactory {
    const val USER_AGENT = "VLC/3.0.18 LibVLC/3.0.18"

    fun create(ctx: Context): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val data = DefaultDataSource.Factory(ctx, http)
        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(data))
            .build()
    }

    /** Same live channel with the other container (.m3u8 <-> .ts), or null when not applicable. */
    fun altUrl(u: String): String? {
        if (!u.contains("/live/")) return null
        return when {
            u.endsWith(".m3u8") -> u.removeSuffix(".m3u8") + ".ts"
            u.endsWith(".ts") -> u.removeSuffix(".ts") + ".m3u8"
            else -> null
        }
    }

    /** Error name plus the HTTP status code when the server refused the request. */
    fun describe(e: PlaybackException): String {
        var t: Throwable? = e.cause
        while (t != null) {
            if (t is HttpDataSource.InvalidResponseCodeException) return "${e.errorCodeName} (HTTP ${t.responseCode})"
            t = t.cause
        }
        return e.errorCodeName
    }
}
