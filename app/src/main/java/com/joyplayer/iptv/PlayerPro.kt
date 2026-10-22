package com.joyplayer.iptv

import android.net.Uri
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
    val player = shared ?: remember(ch.url) {
        ExoPlayer.Builder(ctx).build().apply { setMediaItem(baseItem); prepare(); playWhenReady = true }
    }
    PipSupport(player)
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    var overlay by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(settings.key) }
    var auto by remember { mutableStateOf(settings.auto) }
    var lang by remember { mutableStateOf(settings.lang) }
    var hits by remember { mutableStateOf<List<SubHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val boxFr = remember { FocusRequester() }
    val firstBtnFr = remember { FocusRequester() }
    var boxFocused by remember { mutableStateOf(false) }
    var pv by remember { mutableStateOf<PlayerView?>(null) }

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
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.widget.Toast.makeText(
                    ctx, "Playback error: ${error.errorCodeName}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        player.addListener(l)
        onDispose {
            player.removeListener(l)
            if (shared == null) player.release() else SharedLive.restorePreview()
        }
    }

    suspend fun applyHit(h: SubHit) {
        val (file, ext) = withContext(Dispatchers.IO) { SubDl.fetch(h.url, meta, ctx.cacheDir) }
        val mime = when (ext) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val sub = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
            .setMimeType(mime)
            .setLanguage(lang.lowercase())
            .setLabel("SubDL")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val pos = player.currentPosition
        player.setMediaItem(baseItem.buildUpon().setSubtitleConfigurations(listOf(sub)).build(), pos)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        player.prepare()
        player.playWhenReady = true
        msg = "Subtitle loaded: ${h.name}"
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
                if (!boxFocused || dialog.isNotEmpty() || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val repeat = e.nativeKeyEvent.repeatCount
                val step = if (repeat > 6) 30_000L else 10_000L
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play()
                        pv?.showController(); true
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
                    Key.DirectionUp, Key.Menu -> {
                        if (overlay) runCatching { firstBtnFr.requestFocus() } else pv?.showController()
                        true
                    }
                    Key.DirectionDown -> { pv?.showController(); true }
                    else -> false
                }
            }
            .focusable()
    ) {
        AndroidView(
            factory = { c ->
                (android.view.LayoutInflater.from(c).inflate(R.layout.player_view, null) as PlayerView).apply {
                    this.player = player
                    pv = this
                    AppPrefs.styleSubtitles(this)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v -> overlay = v == View.VISIBLE }
                    )
                    val gd = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            back()
                            return true
                        }
                    })
                    setOnTouchListener { _, e -> gd.onTouchEvent(e); false }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if ((overlay || dialog.isNotEmpty()) && !PipState.inPip) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                            runCatching { boxFr.requestFocus() }; true
                        } else false
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayBtn("🔊 Audio", Modifier.focusRequester(firstBtnFr)) { dialog = "audio" }
                OverlayBtn("💬 Subtitles") { dialog = "subs" }
                OverlayBtn("⚙ Quality") { dialog = "quality" }
                OverlayBtn("🖼 PiP") { PipState.request(ctx) }
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
                    Opt("🌐  Search online (SubDL)", false) {
                        dialog = "online"
                        if (key.isNotBlank()) search(false)
                    }
                } else {
                    Text("Online search works for movies and series.", color = C.Text, fontSize = 13.sp)
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
