package com.joyplayer.iptv

import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Keeps a live channel alive: if the picture freezes (buffering for too long) or the stream dies,
 * it reconnects by itself, waiting a little longer after each failure (5, 10, 20, 30 s ...).
 */
object LiveRecover {
    private val STALL = longArrayOf(10_000L, 10_000L, 15_000L, 20_000L)
    private val RETRY = longArrayOf(5_000L, 10_000L, 20_000L, 30_000L)
    private val VOD_STALL = longArrayOf(12_000L, 12_000L, 20_000L, 30_000L)
    private const val MAX = 12

    private class Guard(val p: ExoPlayer, val vod: Boolean = false) : Player.Listener {
        val h = Handler(Looper.getMainLooper())
        var stallN = 0
        var errN = 0

        fun restart() {
            runCatching {
                val item = p.currentMediaItem ?: return
                // a movie / episode reconnects at the same minute, a live channel just restarts
                val pos = if (vod && p.duration != androidx.media3.common.C.TIME_UNSET) p.currentPosition else 0L
                p.stop()
                if (vod && pos > 0L) p.setMediaItem(item, pos) else p.setMediaItem(item)
                p.prepare()
                p.playWhenReady = true
            }
        }

        val stallRun = Runnable {
            if (p.playbackState == Player.STATE_BUFFERING && p.playWhenReady && stallN < MAX) {
                stallN++
                AppLog.add(if (vod) "vod" else "live", "stalled, reconnect #$stallN")
                PlayerFactory.reconnecting = stallN
                restart()
            }
        }
        val errRun = Runnable {
            if (p.playbackState == Player.STATE_IDLE && errN < MAX) {
                restart()
            }
        }

        fun cancel() { h.removeCallbacks(stallRun); h.removeCallbacks(errRun) }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    h.removeCallbacks(stallRun)
                    h.postDelayed(stallRun, if (vod) VOD_STALL[minOf(stallN, VOD_STALL.size - 1)] else STALL[minOf(stallN, STALL.size - 1)])
                }
                Player.STATE_READY -> {
                    cancel(); stallN = 0; errN = 0; PlayerFactory.reconnecting = 0
                }
                else -> h.removeCallbacks(stallRun)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // the server refused (401 / 403 / 404 ...): asking again and again changes nothing and can get the device blocked
            if (PlayerFactory.httpStatus(error) in 400..499) return
            if (errN >= MAX) return
            h.removeCallbacks(errRun)
            h.postDelayed(errRun, RETRY[minOf(errN, RETRY.size - 1)])
            errN++
            PlayerFactory.reconnecting = errN
        }
    }

    /** Starts watching the player; call the returned function to stop. */
    fun attach(player: ExoPlayer, vod: Boolean = false): () -> Unit {
        val g = Guard(player, vod)
        player.addListener(g)
        return {
            g.cancel()
            runCatching { player.removeListener(g) }
            PlayerFactory.reconnecting = 0
        }
    }
}
