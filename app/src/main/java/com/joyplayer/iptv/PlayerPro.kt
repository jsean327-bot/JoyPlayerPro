package com.joyplayer.iptv

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val subLangs = listOf(
    "AR" to "العربية", "EN" to "English", "FR" to "Français", "TR" to "Türkçe",
    "ES" to "Español", "DE" to "Deutsch", "IT" to "Italiano"
)

private fun langName(code: String?): String? {
    if (code.isNullOrBlank() || code == "und") return null
    val n = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH)
    return if (n.isBlank() || n.equals(code, true)) code else n
}

private fun trackLabel(f: Format, n: Int): String {
    val l = langName(f.language)
    val name = f.label
    val extra = if (f.channelCount > 0) "  ${f.channelCount}ch" else ""
    val tag = if (name != null && l != null && !name.contains(l, true)) " ($l)" else ""
    return (name ?: l ?: "Track $n") + tag + extra
}

private fun qLabel(h: Int) = when {
    h >= 4320 -> "8K (${h}p)"
    h >= 2160 -> "4K (${h}p)"
    h >= 1440 -> "2K (${h}p)"
    else -> "${h}p"
}

private fun selectTrack(player: ExoPlayer, g: Tracks.Group, i: Int) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(g.type, false)
        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
        .build()
}

@Composable
private fun OverlayBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label, color = Color.White, fontSize = 14.sp,
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x99000000))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun Opt(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(if (selected) C.Burgundy else C.Row)
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = Color.White)
    }
}

@Composable
private fun PlayerDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF14102E)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Column(
                Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) { content() }
        }
    }
}

@Composable
fun ProPlayer(ch: Channel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val back by rememberUpdatedState(onBack)
    val settings = remember { SubSettings(ctx) }
    val hasMeta = remember(ch.url) { PlayMeta.map.containsKey(ch.url) }
    val meta = remember(ch.url) {
        PlayMeta.map[ch.url] ?: SubMeta(PlayMeta.cleanTitle(ch.name), PlayMeta.year(ch.name), null, null)
    }
    val baseItem = remember(ch.url) { MediaItem.fromUri(AppPrefs.fmtUrl(ch.url)) }
    val shared = remember(ch.url) { SharedLive.takeFor(ch.url) }
    val retries = remember(ch.url) { HashMap<String, StreamRetry>() }
    ResumeStore.init(ctx)
    val resumeAt = remember(ch.url) { if (hasMeta && shared == null) ResumeStore.get(ch.url) else 0L }
    val player = shared ?: remember(ch.url) {
        PlayerFactory.create(ctx).apply {
            if (resumeAt > 0L) setMediaItem(baseItem, resumeAt) else setMediaItem(baseItem)
            prepare(); playWhenReady = true
        }
    }
    LaunchedEffect(ch.url) {
        if (resumeAt > 0L) {
            android.widget.Toast.makeText(ctx, "Resuming from ${ResumeStore.fmt(resumeAt)}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            if (hasMeta && shared == null && player.isPlaying) ResumeStore.save(ch.url, player.currentPosition, player.duration, ch.name, ch.logo)
        }
    }
    PipSupport(player)
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    var overlay by remember { mutableStateOf(true) }
    var uiTick by remember { mutableStateOf(0) }   // bumped on every key press: restarts the auto-hide timer
    // touch-seek HUD (double-tap skip / hold-and-slide scrub)
    var seekHud by remember { mutableStateOf<String?>(null) }
    var skipAcc by remember { mutableStateOf(0L) }
    var scrubUi by remember { mutableStateOf(false) }
    var hudTick by remember { mutableStateOf(0) }
    LaunchedEffect(hudTick) {
        if (!scrubUi) {
            delay(900)
            seekHud = null
            skipAcc = 0L
        }
    }
    var dialog by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(settings.key) }
    var auto by remember { mutableStateOf(settings.auto) }
    var lang by remember { mutableStateOf(settings.lang) }
    var hits by remember { mutableStateOf<List<SubHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    // downloaded subtitle kept in memory so its timing can be shifted (Subtitle delay)
    var subText by remember(ch.url) { mutableStateOf<String?>(null) }
    var subExt by remember(ch.url) { mutableStateOf("srt") }
    var subLabel by remember(ch.url) { mutableStateOf("") }
    var subOffset by remember(ch.url) { mutableStateOf(0L) }
    var delayPending by remember(ch.url) { mutableStateOf(0L) }
    var speed by remember(ch.url) { mutableStateOf(1f) }
    // Google Cast (phones with Google Play services only)
    val castCtx = remember { if (isTvDevice(ctx)) null else CastHelper.context(ctx) }
    var castName by remember { mutableStateOf<String?>(null) }
    var castPos by remember { mutableStateOf(0L) }
    DisposableEffect(castCtx) {
        val sm = castCtx?.sessionManager
        if (sm == null) {
            onDispose { }
        } else {
            val l = object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(s: CastSession, id: String) {
                    castName = s.castDevice?.friendlyName ?: "TV"
                    val c = s.remoteMediaClient
                    if (c != null) {
                        c.load(
                            CastHelper.request(
                                AppPrefs.fmtUrl(ch.url), ch.name, ch.logo, !hasMeta,
                                if (hasMeta) player.currentPosition else 0L
                            )
                        )
                    }
                    player.pause()
                }
                override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) {
                    castName = s.castDevice?.friendlyName ?: "TV"
                }
                override fun onSessionEnding(s: CastSession) {
                    castPos = s.remoteMediaClient?.approximateStreamPosition ?: 0L
                }
                override fun onSessionEnded(s: CastSession, error: Int) {
                    val was = castName != null
                    castName = null
                    if (was) {
                        if (hasMeta && castPos > 0L) player.seekTo(castPos)
                        player.play()
                    }
                }
                override fun onSessionStarting(s: CastSession) {}
                override fun onSessionStartFailed(s: CastSession, error: Int) { castName = null }
                override fun onSessionResuming(s: CastSession, id: String) {}
                override fun onSessionResumeFailed(s: CastSession, error: Int) {}
                override fun onSessionSuspended(s: CastSession, reason: Int) {}
            }
            sm.addSessionManagerListener(l, CastSession::class.java)
            onDispose {
                sm.removeSessionManagerListener(l, CastSession::class.java)
                if (castName != null) sm.endCurrentSession(true)
            }
        }
    }
    var sleepEnd by remember { mutableStateOf(0L) }
    var sleepLeft by remember { mutableStateOf(0) }
    LaunchedEffect(sleepEnd) {
        if (sleepEnd <= 0L) {
            sleepLeft = 0
            return@LaunchedEffect
        }
        while (true) {
            val left = sleepEnd - System.currentTimeMillis()
            if (left <= 0L) break
            sleepLeft = ((left + 59_999L) / 60_000L).toInt()
            delay(minOf(left, 30_000L))
        }
        sleepEnd = 0L
        player.pause()
        back()
    }
    // Picture size: 0 = Fit (black bars), 4 = Zoom (fills the screen, crops edges), 3 = Stretch
    val playerPrefs = remember { ctx.getSharedPreferences("joyplayer_player", android.content.Context.MODE_PRIVATE) }
    var resizeMode by remember { mutableStateOf(playerPrefs.getInt("resize", 0)) }

    // Fullscreen: hide the system bars, use the notch area, and keep the screen awake while playing
    DisposableEffect(Unit) {
        val win = (ctx as? Activity)?.window
        if (win != null) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val c = WindowCompat.getInsetsController(win, win.decorView)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= 28) {
                val lp = win.attributes
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                win.attributes = lp
            }
        }
        onDispose {
            if (win != null) {
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(win, win.decorView).show(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= 28) {
                    val lp = win.attributes
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    win.attributes = lp
                }
            }
        }
    }

    val boxFr = remember { FocusRequester() }
    val firstBtnFr = remember { FocusRequester() }
    var boxFocused by remember { mutableStateOf(false) }
    var pv by remember { mutableStateOf<PlayerView?>(null) }

    // Live channel zapping (inside the list the user was browsing)
    val canZap = !hasMeta && LiveZap.list.size > 1
    fun goTo(n: Channel, num: Int) {
        // keep using the one live connection: switch the shared player to the new channel
        SharedLive.player?.let { sp ->
            if (shared != null) {
                sp.setMediaItem(MediaItem.fromUri(AppPrefs.fmtUrl(n.url)))
                sp.prepare()
                sp.playWhenReady = true
            }
        }
        LiveZap.onSelect?.invoke(n)
        android.widget.Toast.makeText(ctx, "$num. ${n.name}", android.widget.Toast.LENGTH_SHORT).show()
        pv?.showController()
        LiveZap.play?.invoke(n)
    }

    fun zap(d: Int) {
        val list = LiveZap.list
        val i = list.indexOfFirst { it.url == ch.url }
        if (i < 0 || list.size < 2) return
        val idx = ((i + d) % list.size + list.size) % list.size
        goTo(list[idx], idx + 1)
    }

    // Number keys on the remote: type a channel number (its position in the list), 0 = previous channel
    var numBuf by remember { mutableStateOf("") }

    fun lastChannel() {
        val prevUrl = LiveHistory.list().firstOrNull { it != ch.url }
        val pool = if (LiveZap.all.isNotEmpty()) LiveZap.all else LiveZap.list
        val n = pool.firstOrNull { it.url == prevUrl }
        if (n == null) {
            android.widget.Toast.makeText(ctx, "No previous channel", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val pos = LiveZap.list.indexOfFirst { it.url == n.url }
            goTo(n, if (pos >= 0) pos + 1 else 0)
        }
    }

    fun jumpNumber() {
        val n = numBuf.toIntOrNull()
        numBuf = ""
        val list = LiveZap.list
        if (n != null && n in 1..list.size) {
            goTo(list[n - 1], n)
        } else if (n != null) {
            android.widget.Toast.makeText(ctx, "No channel $n", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(numBuf) {
        if (numBuf.isNotEmpty()) {
            delay(1600)
            jumpNumber()
        }
    }

    // Hide the controls + top buttons by themselves while watching (5 s without any key / touch)
    LaunchedEffect(overlay, dialog, uiTick) {
        if (overlay && dialog.isEmpty()) {
            delay(5000)
            pv?.hideController()
        }
    }

    // Remote: show the top buttons (Audio, Subtitles, Quality ...) and move the focus onto them
    fun focusButtons() {
        pv?.showController()
        scope.launch {
            delay(160)
            runCatching { firstBtnFr.requestFocus() }
        }
    }

    // Remote control: keep focus on the video so OK / left / right work, return to it after dialogs.
    LaunchedEffect(dialog, overlay) {
        if (dialog.isEmpty() && (overlay || !boxFocused)) {
            delay(80)
            runCatching { if (!overlay || !boxFocused) boxFr.requestFocus() }
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) { retries.clear(); PlayerFactory.lastDiag = "" }
                if (state == Player.STATE_ENDED && hasMeta && shared == null) {
                    val nx = PlayQueue.nextAfter(ch.url)
                    val go = PlayQueue.play
                    if (nx != null && go != null) {
                        ResumeStore.save(ch.url, player.duration, player.duration, ch.name, ch.logo)
                        PlayMeta.map[nx.first.url] = nx.second
                        android.widget.Toast.makeText(ctx, "Next episode: ${nx.first.name}", android.widget.Toast.LENGTH_SHORT).show()
                        go(nx.first)
                    }
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (shared != null) return   // the live preview player reports its own errors
                val cur = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
                PlayerFactory.handleError(ctx, player, error, cur, retries)
            }
        }
        player.addListener(l)
        onDispose {
            player.removeListener(l)
            if (hasMeta && shared == null) ResumeStore.save(ch.url, player.currentPosition, player.duration, ch.name, ch.logo)
            if (shared == null) player.release() else SharedLive.restorePreview()
        }
    }

    suspend fun loadSubFile(file: java.io.File, ext: String, label: String) {
        val mime = when (ext) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val sub = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
            .setMimeType(mime)
            .setLanguage(lang.lowercase())
            .setLabel(label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val pos = player.currentPosition
        player.setMediaItem(baseItem.buildUpon().setSubtitleConfigurations(listOf(sub)).build(), pos)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        player.prepare()
        player.playWhenReady = true
    }

    suspend fun applyHit(h: SubHit) {
        val (file, ext) = withContext(Dispatchers.IO) { SubDl.fetch(h.url, meta, ctx.cacheDir) }
        subText = withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8) }
        subExt = ext
        subLabel = h.name
        subOffset = 0L
        delayPending = 0L
        loadSubFile(file, ext, "SubDL")
        msg = "Subtitle loaded: ${h.name}"
    }

    fun applyDelay(off: Long) {
        val txt = subText ?: return
        val ext = subExt
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                val out = java.io.File(ctx.cacheDir, "sub_shift_${System.currentTimeMillis()}.$ext")
                out.writeText(SubShift.shift(txt, ext, off), Charsets.UTF_8)
                out
            }
            loadSubFile(f, ext, "SubDL")
            subOffset = off
        }
    }

    fun search(applyFirst: Boolean) {
        if (key.isBlank()) { msg = "Enter your SubDL API key first"; return }
        scope.launch {
            busy = true; msg = null
            try {
                val res = withContext(Dispatchers.IO) { SubDl.search(key, meta, lang) }
                hits = res
                if (res.isEmpty()) msg = "No subtitles found for this language"
                else if (applyFirst) applyHit(res.first())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                msg = e.message ?: "Error"
            }
            busy = false
        }
    }

    LaunchedEffect(ch.url) {
        if (hasMeta && auto && key.isNotBlank()) {
            delay(3000)
            val hasAr = tracks.groups.any { g ->
                g.type == C.TRACK_TYPE_TEXT &&
                        (0 until g.length).any { i -> g.getTrackFormat(i).language?.startsWith("ar", true) == true }
            }
            if (!hasAr) search(true)
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(boxFr)
            .onFocusChanged { boxFocused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) uiTick++
                if (!boxFocused || dialog.isNotEmpty() || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val repeat = e.nativeKeyEvent.repeatCount
                val step = if (repeat > 6) 30_000L else 10_000L
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        if (numBuf.isNotEmpty() && e.key != Key.MediaPlayPause) {
                            jumpNumber()
                        } else if (!hasMeta && e.key != Key.MediaPlayPause) {
                            // live TV: OK opens the buttons row (Audio / Subtitles / Quality ...)
                            focusButtons()
                        } else {
                            if (player.isPlaying) player.pause() else player.play()
                            pv?.showController()
                        }
                        true
                    }
                    Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
                    Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
                    Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9 -> {
                        if (!canZap) {
                            false
                        } else {
                            val d = e.nativeKeyEvent.keyCode.let { kc ->
                                if (kc >= android.view.KeyEvent.KEYCODE_NUMPAD_0) kc - android.view.KeyEvent.KEYCODE_NUMPAD_0
                                else kc - android.view.KeyEvent.KEYCODE_0
                            }
                            if (d == 0 && numBuf.isEmpty()) lastChannel()
                            else if (numBuf.length < 4) numBuf += d.toString()
                            true
                        }
                    }
                    Key.MediaPlay -> { player.play(); pv?.showController(); true }
                    Key.MediaPause -> { player.pause(); pv?.showController(); true }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        if (player.isCurrentMediaItemSeekable) player.seekTo((player.currentPosition - step).coerceAtLeast(0L))
                        pv?.showController(); true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        if (player.isCurrentMediaItemSeekable) player.seekTo(player.currentPosition + step)
                        pv?.showController(); true
                    }
                    Key.ChannelUp -> { if (canZap) zap(1) else pv?.showController(); true }
                    Key.ChannelDown -> { if (canZap) zap(-1) else pv?.showController(); true }
                    Key.DirectionUp -> {
                        if (canZap) dialog = "chans" else pv?.showController()
                        true
                    }
                    Key.Menu -> { focusButtons(); true }
                    Key.DirectionDown -> { if (canZap) dialog = "chans" else focusButtons(); true }
                    else -> false
                }
            }
            .focusable()
    ) {
        AndroidView(
            factory = { c ->
                val exo = player
                (android.view.LayoutInflater.from(c).inflate(R.layout.player_view, null) as PlayerView).apply {
                    this.player = player
                    pv = this
                    AppPrefs.styleSubtitles(this)
                    // lift the player's own bar (time, seek bar, settings gear) so our buttons row sits right under it
                    val lift = (48 * c.resources.displayMetrics.density).toInt()
                    for (name in listOf("exo_bottom_bar", "exo_progress")) {
                        val id = c.resources.getIdentifier(name, "id", c.packageName)
                        val v = if (id != 0) findViewById<View>(id) else null
                        val lp = v?.layoutParams as? ViewGroup.MarginLayoutParams
                        if (v != null && lp != null) {
                            lp.bottomMargin += lift
                            v.layoutParams = lp
                        }
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v -> overlay = v == View.VISIBLE }
                    )
                    val view = this
                    val skipMs = 10_000L
                    var scrubbing = false
                    var startX = 0f
                    var startPos = 0L
                    var target = 0L

                    var vMode = 0          // 0 = undecided, 1 = vertical swipe, 2 = other
                    var vLeft = false
                    var level = 0f
                    val audio = c.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager

                    fun canSeek(): Boolean = hasMeta && exo.isCurrentMediaItemSeekable

                    fun skipBy(ms: Long) {
                        val d = exo.duration
                        var t = exo.currentPosition + ms
                        if (t < 0L) t = 0L
                        if (d > 0L && t > d - 1000L) t = (d - 1000L).coerceAtLeast(0L)
                        exo.seekTo(t)
                        skipAcc = if (seekHud != null && skipAcc != 0L && (skipAcc > 0L) == (ms > 0L)) skipAcc + ms else ms
                        seekHud = if (skipAcc > 0L) "+${skipAcc / 1000}s  ⏩" else "⏪  -${-skipAcc / 1000}s"
                        hudTick++
                    }

                    val gd = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDown(e: MotionEvent): Boolean = true

                        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                            if (scrubbing || e1 == null) return false
                            val win = (c as? Activity)?.window
                            if (vMode == 0) {
                                vMode = if (kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f) 1 else 2
                                vLeft = e1.x < view.width / 2f
                                if (vMode == 1) {
                                    level = if (vLeft) {
                                        val cur = win?.attributes?.screenBrightness ?: -1f
                                        if (cur >= 0f) cur else runCatching {
                                            android.provider.Settings.System.getInt(
                                                c.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS
                                            ) / 255f
                                        }.getOrDefault(0.5f)
                                    } else {
                                        audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() /
                                            audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    }
                                }
                            }
                            if (vMode != 1) return false
                            val h = view.height.toFloat().coerceAtLeast(1f)
                            level = (level + dy / h * 1.3f).coerceIn(if (vLeft) 0.02f else 0f, 1f)
                            val pct = (level * 100).toInt()
                            if (vLeft) {
                                if (win != null) {
                                    val lp = win.attributes
                                    lp.screenBrightness = level
                                    win.attributes = lp
                                }
                                seekHud = "🔆  $pct%"
                            } else {
                                val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (level * max).toInt(), 0)
                                seekHud = (if (pct == 0) "🔇  " else "🔊  ") + "$pct%"
                            }
                            hudTick++
                            return true
                        }

                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            if (!canSeek()) { back(); return true }
                            val w = view.width.toFloat().coerceAtLeast(1f)
                            when {
                                e.x < w * 0.4f -> skipBy(-skipMs)
                                e.x > w * 0.6f -> skipBy(skipMs)
                                else -> if (exo.isPlaying) exo.pause() else exo.play()
                            }
                            return true
                        }

                        override fun onLongPress(e: MotionEvent) {
                            if (!canSeek()) return
                            scrubbing = true
                            startX = e.x
                            startPos = exo.currentPosition
                            target = startPos
                            scrubUi = true
                            seekHud = ResumeStore.fmt(startPos)
                            hudTick++
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    })
                    setOnTouchListener { _, e ->
                        if (e.actionMasked == MotionEvent.ACTION_DOWN) vMode = 0
                        gd.onTouchEvent(e)
                        if (vMode == 1 && !scrubbing) {
                            true
                        } else if (scrubbing) {
                            when (e.actionMasked) {
                                MotionEvent.ACTION_MOVE -> {
                                    val w = view.width.toFloat().coerceAtLeast(1f)
                                    val d = exo.duration
                                    var t = startPos + ((e.x - startX) / w * 120_000f).toLong()
                                    if (t < 0L) t = 0L
                                    if (d > 0L && t > d - 1000L) t = (d - 1000L).coerceAtLeast(0L)
                                    target = t
                                    val diff = t - startPos
                                    val sign = if (diff >= 0L) "+" else "-"
                                    seekHud = ResumeStore.fmt(t) + "   (" + sign + ResumeStore.fmt(kotlin.math.abs(diff)) + ")"
                                    hudTick++
                                }
                                MotionEvent.ACTION_UP -> {
                                    exo.seekTo(target)
                                    scrubbing = false
                                    scrubUi = false
                                    hudTick++
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    scrubbing = false
                                    scrubUi = false
                                    hudTick++
                                }
                            }
                            true
                        } else false
                    }
                }
            },
            update = { v -> v.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize()
        )
        seekHud?.let { t ->
            if (!PipState.inPip) {
                Text(
                    t, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                        .clip(RoundedCornerShape(14.dp)).background(Color(0xAA000000))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
        if (numBuf.isNotEmpty() && !PipState.inPip) {
            Text(
                numBuf, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 36.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Color(0xAA000000))
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
        castName?.let { n ->
            if (!PipState.inPip) {
                CastBar(
                    n, castCtx?.sessionManager?.currentCastSession?.remoteMediaClient, hasMeta,
                    { castCtx?.sessionManager?.endCurrentSession(true) },
                    Modifier.align(Alignment.Center)
                )
            }
        }
        val diag = PlayerFactory.lastDiag
        if (diag.isNotEmpty() && !PipState.inPip) {
            Text(
                diag, color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 90.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(8.dp)
            )
        }
        if (!hasMeta && overlay && dialog.isEmpty() && !PipState.inPip) {
            val narrow = rememberIsCompact()
            EpgBadge(
                ch,
                Modifier.align(Alignment.TopStart)
                    .padding(start = 12.dp, end = 12.dp, top = if (narrow) 64.dp else 12.dp)
                    .fillMaxWidth(if (narrow) 1f else 0.42f)
            )
        }
        if ((overlay || dialog.isNotEmpty()) && !PipState.inPip) {
            Row(
                // bottom of the screen, directly under the player's own bar with the settings gear
                Modifier.align(Alignment.BottomEnd).padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                    .horizontalScroll(rememberScrollState())
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown) uiTick++
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                            runCatching { boxFr.requestFocus() }; true
                        } else false
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canZap) {
                    OverlayBtn("⏮ CH") { zap(-1) }
                    OverlayBtn("CH ⏭") { zap(1) }
                    OverlayBtn("↩ Last") { lastChannel() }
                    OverlayBtn("☰ Channels") { dialog = "chans" }
                }
                OverlayBtn("🔊 Audio", Modifier.focusRequester(firstBtnFr)) { dialog = "audio" }
                OverlayBtn("💬 Subtitles") { dialog = "subs" }
                OverlayBtn("⚙ Quality") { dialog = "quality" }
                if (hasMeta) OverlayBtn("⏩ " + (if (speed == 1f) "1x" else "${speed}x")) { dialog = "speed" }
                OverlayBtn(if (sleepEnd > 0L) "😴 ${sleepLeft}m" else "😴 Sleep") { dialog = "sleep" }
                if (castCtx != null) OverlayBtn("📡 Cast") { dialog = "cast" }
                OverlayBtn(
                    when (resizeMode) { 4 -> "⤢ Zoom"; 3 -> "⤢ Stretch"; else -> "⤢ Fit" }
                ) {
                    resizeMode = when (resizeMode) { 0 -> 4; 4 -> 3; else -> 0 }
                    playerPrefs.edit().putInt("resize", resizeMode).apply()
                }
                OverlayBtn("🖼 PiP") { PipState.request(ctx) }
                PlayQueue.nextAfter(ch.url)?.let { nx ->
                    if (hasMeta) OverlayBtn("⏭ Next") {
                        PlayMeta.map[nx.first.url] = nx.second
                        PlayQueue.play?.invoke(nx.first)
                    }
                }
            }
        }

        when (dialog) {
            "audio" -> PlayerDialog("Audio / Dubbing", { dialog = "" }) {
                val items = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                    .flatMap { g -> (0 until g.length).map { g to it } }
                items.forEachIndexed { idx, (g, i) ->
                    Opt(trackLabel(g.getTrackFormat(i), idx + 1), g.isTrackSelected(i)) {
                        selectTrack(player, g, i); dialog = ""
                    }
                }
                if (items.size <= 1) Text("This stream has only one audio track.", color = C.Text, fontSize = 13.sp)
            }
            "subs" -> PlayerDialog("Subtitles", { dialog = "" }) {
                val items = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                    .flatMap { g -> (0 until g.length).map { g to it } }
                val off = items.none { (g, i) -> g.isTrackSelected(i) }
                Opt("Off", off) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    dialog = ""
                }
                items.forEachIndexed { idx, (g, i) ->
                    Opt(trackLabel(g.getTrackFormat(i), idx + 1), g.isTrackSelected(i)) {
                        selectTrack(player, g, i); dialog = ""
                    }
                }
                if (hasMeta) {
                    Opt("⏱  Subtitle delay", false) { delayPending = subOffset; dialog = "subdelay" }
                    Opt("🌐  Search online (SubDL)", false) {
                        dialog = "online"
                        if (key.isNotBlank()) search(false)
                    }
                } else {
                    Text("Online search works for movies and series.", color = C.Text, fontSize = 13.sp)
                }
            }
            "subdelay" -> PlayerDialog("Subtitle delay", { dialog = "" }) {
                if (subText == null) {
                    Text(
                        "Delay works with subtitles downloaded from SubDL. Load one first.",
                        color = C.Text, fontSize = 13.sp
                    )
                } else {
                    Text(
                        String.format(Locale.US, "%+.1fs", delayPending / 1000.0),
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
                    )
                    Text("Positive values show the subtitle later, negative earlier.", color = C.Text, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverlayBtn("-0.5s") { delayPending -= 500L }
                        OverlayBtn("-0.1s") { delayPending -= 100L }
                        OverlayBtn("+0.1s") { delayPending += 100L }
                        OverlayBtn("+0.5s") { delayPending += 500L }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { delayPending = 0L }) { Text("Reset") }
                        Button(
                            onClick = { applyDelay(delayPending); dialog = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy)
                        ) { Text("OK") }
                    }
                }
            }
            "cast" -> castCtx?.let { cc ->
                CastDialog(cc, castName, { cc.sessionManager.endCurrentSession(true) }, { dialog = "" })
            }
            "speed" -> PlayerDialog("Playback speed", { dialog = "" }) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { sp ->
                    Opt(if (sp == 1f) "Normal (1x)" else "${sp}x", speed == sp) {
                        speed = sp
                        player.setPlaybackParameters(PlaybackParameters(sp))
                        dialog = ""
                    }
                }
            }
            "chans" -> androidx.compose.ui.window.Dialog(
                onDismissRequest = { dialog = "" },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                val chList = LiveZap.list
                val curIdx = chList.indexOfFirst { it.url == ch.url }.coerceAtLeast(0)
                val ls = androidx.compose.foundation.lazy.rememberLazyListState(
                    initialFirstVisibleItemIndex = (curIdx - 3).coerceAtLeast(0)
                )
                val curFr = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    delay(120)
                    runCatching { curFr.requestFocus() }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Column(
                        Modifier.fillMaxHeight().width(340.dp)
                            .background(Color(0xE60E0B24)).padding(vertical = 12.dp)
                    ) {
                        Text(
                            "Channels", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = ls,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(chList.size) { i ->
                                val c = chList[i]
                                val isCur = i == curIdx
                                Row(
                                    Modifier.fillMaxWidth()
                                        .then(if (isCur) Modifier.focusRequester(curFr) else Modifier)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCur) C.Burgundy else Color(0x33FFFFFF))
                                        .clickable {
                                            dialog = ""
                                            if (!isCur) goTo(c, i + 1)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${i + 1}", color = Color(0xFFBFB6E8), fontSize = 13.sp, modifier = Modifier.width(40.dp))
                                    Text(
                                        c.name, color = Color.White, fontSize = 15.sp, maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isCur) Text("▶", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            "sleep" -> PlayerDialog("Sleep timer", { dialog = "" }) {
                Opt("Off", sleepEnd == 0L) { sleepEnd = 0L; dialog = "" }
                listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                    Opt("$m min", false) {
                        sleepEnd = System.currentTimeMillis() + m * 60_000L
                        dialog = ""
                    }
                }
            }
            "online" -> PlayerDialog("SubDL subtitles", { dialog = "" }) {
                if (key.isBlank()) {
                    var k by remember { mutableStateOf("") }
                    Text("Paste your SubDL API key (saved on this device only):", color = C.Text, fontSize = 13.sp)
                    OutlinedTextField(k, { k = it }, singleLine = true, label = { Text("API key") })
                    Button(onClick = {
                        settings.key = k; key = settings.key
                        if (key.isNotBlank()) search(false)
                    }) { Text("Save") }
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        subLangs.forEach { (code, name) ->
                            FilterChip(lang == code, { lang = code; settings.lang = code; search(false) }, { Text(name) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(auto, { auto = it; settings.auto = it })
                        Text("  Auto-load subtitles", color = Color.White, fontSize = 14.sp)
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    msg?.let { Text(it, color = Color(0xFFFFD54F), fontSize = 13.sp) }
                    hits.forEach { h ->
                        Opt(h.name + if (h.author.isNotBlank()) "  •  ${h.author}" else "", false) {
                            scope.launch {
                                busy = true
                                try {
                                    applyHit(h)
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    msg = e.message ?: "Error"
                                }
                                busy = false
                            }
                        }
                    }
                    TextButton(onClick = { key = ""; settings.key = "" }) { Text("Change API key") }
                }
            }
            "quality" -> PlayerDialog("Video quality", { dialog = "" }) {
                val vids = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                    .flatMap { g ->
                        (0 until g.length).filter { g.isTrackSupported(it) }
                            .map { Triple(g, it, g.getTrackFormat(it).height) }
                    }
                    .filter { it.third > 0 }
                    .distinctBy { it.third }
                    .sortedBy { it.third }
                val isAuto = player.trackSelectionParameters.overrides.values
                    .none { it.type == C.TRACK_TYPE_VIDEO }
                Opt("Auto", isAuto) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                    dialog = ""
                }
                vids.forEach { (g, i, h) ->
                    Opt(qLabel(h), g.isTrackSelected(i) && !isAuto) {
                        selectTrack(player, g, i); dialog = ""
                    }
                }
                if (vids.size <= 1) Text("This stream offers only one quality.", color = C.Text, fontSize = 13.sp)
            }
        }
    }
}
