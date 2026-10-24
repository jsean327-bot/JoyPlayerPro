package com.joyplayer.iptv

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

object SharedLive {
    var player: ExoPlayer? = null
    var previewView: PlayerView? = null

    // Returns the live preview player if it is already playing this exact channel
    fun takeFor(url: String): ExoPlayer? {
        val p = player ?: return null
        val cur = p.currentMediaItem?.localConfiguration?.uri?.toString()
        return if (cur == AppPrefs.fmtUrl(url)) p else null
    }

    // Gives the video surface back to the small preview after fullscreen closes
    fun restorePreview() {
        val pv = previewView ?: return
        val p = player ?: return
        pv.player = null
        pv.player = p
    }
}

@Composable
fun rememberLivePlayer(sel: Channel?, paused: Boolean): ExoPlayer {
    val ctx = LocalContext.current
    val player = remember { PlayerFactory.create(ctx) }
    DisposableEffect(Unit) {
        SharedLive.player = player
        val retries = HashMap<String, StreamRetry>()
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) { retries.clear(); PlayerFactory.lastDiag = "" }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val cur = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
                PlayerFactory.handleError(ctx, player, error, cur, retries)
            }
        }
        player.addListener(l)
        val detach = LiveRecover.attach(player)
        onDispose {
            detach()
            player.removeListener(l)
            if (SharedLive.player === player) {
                SharedLive.player = null
                SharedLive.previewView = null
            }
            player.release()
        }
    }
    LaunchedEffect(sel?.url, paused) {
        if (sel == null) {
            player.stop()
        } else if (!paused) {
            val want = AppPrefs.fmtUrl(sel.url)
            val cur = player.currentMediaItem?.localConfiguration?.uri?.toString()
            if (cur != want || player.playbackState == Player.STATE_IDLE) {
                player.setMediaItem(MediaItem.fromUri(want))
                player.prepare()
            }
            player.playWhenReady = true
        }
    }
    return player
}

@Composable
fun LivePreviewSurface(player: ExoPlayer, sel: Channel?, onFull: (Channel) -> Unit) {
    val current by rememberUpdatedState(sel)
    val full by rememberUpdatedState(onFull)
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                this.player = player
                SharedLive.previewView = this
                val gd = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        current?.let { full(it) }
                        return true
                    }
                })
                setOnTouchListener { _, e -> gd.onTouchEvent(e); true }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
