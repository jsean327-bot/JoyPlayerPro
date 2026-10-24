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
    private const val MAX = 12

    private class Guard(val p: ExoPlayer) : Player.Listener {
        val h = Handler(Looper.getMainLooper())
        var stallN = 0
        var errN = 0

        fun restart() {
            runCatching {
                val item = p.currentMediaItem ?: return
                p.stop()
                p.setMediaItem(item)
                p.prepare()
                p.playWhenReady = true
            }
        }

        val stallRun = Runnable {
            if (p.playbackState == Player.STATE_BUFFERING && p.playWhenReady && stallN < MAX) {
                stallN++
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
                    h.postDelayed(stallRun, STALL[minOf(stallN, STALL.size - 1)])
                }
                Player.STATE_READY -> {
                    cancel(); stallN = 0; errN = 0; PlayerFactory.reconnecting = 0
                }
                else -> h.removeCallbacks(stallRun)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (errN >= MAX) return
            h.removeCallbacks(errRun)
            h.postDelayed(errRun, RETRY[minOf(errN, RETRY.size - 1)])
            errN++
            PlayerFactory.reconnecting = errN
        }
    }

    /** Starts watching the player; call the returned function to stop. */
    fun attach(player: ExoPlayer): () -> Unit {
        val g = Guard(player)
        player.addListener(g)
        return {
            g.cancel()
            runCatching { player.removeListener(g) }
            PlayerFactory.reconnecting = 0
        }
    }
}
