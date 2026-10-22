package com.joyplayer.iptv

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppPrefs {
    private var p: SharedPreferences? = null

    fun init(c: Context) {
        if (p == null) p = c.applicationContext.getSharedPreferences("joyplayer_settings", Context.MODE_PRIVATE)
    }

    private fun gi(k: String, d: Int): Int = p?.getInt(k, d) ?: d
    private fun pi(k: String, v: Int) { p?.edit()?.putInt(k, v)?.apply() }
    private fun gs(k: String): Set<String> = p?.getStringSet(k, emptySet())?.toSet() ?: emptySet()
    private fun ps(k: String, v: Set<String>) { p?.edit()?.putStringSet(k, v)?.apply() }

    var clock24: Boolean
        get() = gi("clock24", 1) == 1
        set(v) { pi("clock24", if (v) 1 else 0) }
    var autoConnect: Boolean
        get() = gi("autoconnect", 1) == 1
        set(v) { pi("autoconnect", if (v) 1 else 0) }
    var subBox: Boolean
        get() = gi("subbox", 0) == 1
        set(v) { pi("subbox", if (v) 1 else 0) }
    var posterSize: Int
        get() = gi("poster", 1)
        set(v) { pi("poster", v) }
    /** 0 = update every time, 1 = every day, 2 = after 2 days */
    var autoUpdate: Int
        get() = gi("autoupdate", 1)
        set(v) { pi("autoupdate", v) }
    var subEnabled: Boolean
        get() = gi("subenabled", 1) == 1
        set(v) { pi("subenabled", if (v) 1 else 0) }
    /** Subtitle size in px (40 = normal). Falls back to the old 4-step setting. */
    var subPx: Int
        get() {
            val v = gi("subpx", -1)
            return if (v > 0) v else listOf(30, 40, 52, 68)[subSize.coerceIn(0, 3)]
        }
        set(v) { pi("subpx", v) }
    var subFg: Int
        get() = if (p?.contains("subfg") == true) gi("subfg", 0) else when (subColor) {
            1 -> android.graphics.Color.YELLOW
            2 -> android.graphics.Color.CYAN
            else -> android.graphics.Color.WHITE
        }
        set(v) { pi("subfg", v) }
    var subBg: Int
        get() = if (p?.contains("subbg") == true) gi("subbg", 0)
        else if (subBox) android.graphics.Color.argb(170, 0, 0, 0) else android.graphics.Color.TRANSPARENT
        set(v) { pi("subbg", v) }
    var listLayout: Boolean
        get() = gi("listlayout", 0) == 1
        set(v) { pi("listlayout", if (v) 1 else 0) }
    var liveSort: Int
        get() = gi("livesort", 0)
        set(v) { pi("livesort", v) }
    var liveFormat: Int
        get() = gi("livefmt", 0)
        set(v) { pi("livefmt", v) }
    var subSize: Int
        get() = gi("subsize", 1)
        set(v) { pi("subsize", v) }
    var subColor: Int
        get() = gi("subcolor", 0)
        set(v) { pi("subcolor", v) }

    val posterDp: Int
        get() = listOf(100, 128, 170)[posterSize.coerceIn(0, 2)]

    val hasPin: Boolean
        get() = (p?.getString("pin", "") ?: "").isNotEmpty()

    private fun hashPin(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("joyplayer:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun setPin(pin: String) { p?.edit()?.putString("pin", hashPin(pin))?.apply() }
    fun clearPin() { p?.edit()?.remove("pin")?.apply() }
    fun checkPin(pin: String): Boolean = hashPin(pin) == (p?.getString("pin", "") ?: "")

    fun hidden(kind: String, listId: String?): Set<String> =
        if (listId == null) emptySet() else gs("hid_${kind}_$listId")

    fun setHidden(kind: String, listId: String, v: Set<String>) { ps("hid_${kind}_$listId", v) }

    fun locked(kind: String, listId: String?): Set<String> =
        if (listId == null || !hasPin) emptySet() else gs("lock_${kind}_$listId")

    fun setLocked(kind: String, listId: String, v: Set<String>) { ps("lock_${kind}_$listId", v) }

    var recentLive: List<String>
        get() = (p?.getString("recent_live", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) { p?.edit()?.putString("recent_live", v.joinToString("\n"))?.apply() }

    fun fmtUrl(u: String): String {
        if (!u.contains("/live/")) return u
        // liveFormat: 0 = Default (as provided), 1 = MPEG-TS, 2 = HLS
        if (liveFormat == 1 && u.endsWith(".m3u8")) return u.removeSuffix(".m3u8") + ".ts"
        if (liveFormat == 2 && u.endsWith(".ts")) return u.removeSuffix(".ts") + ".m3u8"
        return u
    }

    fun styleSubtitles(v: PlayerView) {
        val sv = v.subtitleView ?: return
        sv.visibility = if (subEnabled) android.view.View.VISIBLE else android.view.View.GONE
        sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * (subPx / 40f))
        val fg = subFg
        val bg = subBg
        sv.setStyle(
            CaptionStyleCompat(
                fg, bg, android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null
            )
        )
    }
}

object Parental {
    var unlocked by mutableStateOf(false)

    /** Categories opened with the PIN during this visit ("kind|listId|category"). */
    val opened = mutableStateListOf<String>()
}

object LiveHistory {
    var version by mutableStateOf(0)
    fun list(): List<String> = AppPrefs.recentLive
    fun push(url: String) {
        AppPrefs.recentLive = (listOf(url) + AppPrefs.recentLive.filter { it != url }).take(40)
        version++
    }
    fun clear() {
        AppPrefs.recentLive = emptyList()
        version++
    }
}

@Composable
fun ClockText() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(15000) }
    }
    val fmt = if (AppPrefs.clock24) "HH:mm" else "hh:mm a"
    Text(SimpleDateFormat(fmt, Locale.getDefault()).format(Date(now)), color = C.Text, fontSize = 14.sp)
}

@Composable
fun SOpt(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(if (selected) C.Burgundy else C.Row)
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = Color.White)
    }
}

private val subFgColors = listOf(
    0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF00E5FF.toInt(), 0xFF69F0AE.toInt(),
    0xFFFFA726.toInt(), 0xFFFF80AB.toInt(), 0xFFEF5350.toInt(), 0xFF64B5F6.toInt()
)
private val subBgColors = listOf(
    0x00000000, 0xFF000000.toInt(), 0xAA000000.toInt(), 0xFF303030.toInt(),
    0xFF0D1B4A.toInt(), 0xFF4A0E22.toInt()
)

@Composable
private fun ColorChoices(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { c ->
            val none = (c ushr 24) == 0
            val sel = c == selected
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (none) Color(0xFF555555) else Color(c))
                    .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else Color(0x66FFFFFF), RoundedCornerShape(6.dp))
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center
            ) { if (none) Text("∅", color = Color.White) }
        }
    }
}

@Composable
private fun SubBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.Row).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
fun LayoutOpt(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(if (checked) C.Burgundy else C.Row)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun SDialog(
    title: String, onDismiss: () -> Unit, maxHeight: Dp = 240.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF14102E)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Column(
                Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { content() }
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String, options: List<String>, selected: Int,
    onPick: (Int) -> Unit, onDismiss: () -> Unit
) {
    SDialog(title, onDismiss) {
        options.forEachIndexed { i, o -> SOpt(o, i == selected) { onPick(i) } }
    }
}

private fun clearRecents(ctx: Context, kind: String) {
    val p = ctx.getSharedPreferences("joyplayer_lib", Context.MODE_PRIVATE)
    val e = p.edit()
    p.all.keys.filter { it.startsWith("rec_") && it.endsWith("_$kind") }.forEach { e.remove(it) }
    e.apply()
}

private val setLangs = listOf(
    "AR" to "العربية", "EN" to "English", "FR" to "Français", "TR" to "Türkçe",
    "ES" to "Español", "DE" to "Deutsch", "IT" to "Italiano"
)

@Composable
fun SettingsScreen(
    active: SavedList?, channels: List<Channel>,
    onBack: () -> Unit, onPlaylists: () -> Unit
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val subs = remember { SubSettings(ctx) }
    var dialog by remember { mutableStateOf("") }
    val tiles = listOf(
        "parental" to "🔒  Parental Control",
        "playlist" to "📋  Change Playlist",
        "lang" to "🌐  Change Language",
        "layout" to "🔳  Change Layout",
        "hlive" to "🙈  Hide Live Categories",
        "hvod" to "🙈  Hide Vod Categories",
        "hseries" to "🙈  Hide Series Categories",
        "hchan" to "🧹  Clear History Channels",
        "hmovies" to "🧹  Clear History Movies",
        "hseriesh" to "🧹  Clear History Series",
        "sort" to "↕️  Live Channel Sort",
        "format" to "📡  Live Stream Format",
        "auto" to "⚙️  Automatic",
        "clock" to "🕒  Time Format",
        "sub" to "💬  Subtitle Settings",
        "pip" to "🖼️  PIP Settings",
        "backup" to "💾  Backup & Restore",
        "update" to "⬆️  Update Now"
    )

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    fun tap(id: String) {
        when (id) {
            "playlist" -> onPlaylists()
            else -> dialog = id
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E1A4A), Color(0xFF2A1040), Color(0xFF4A0E22))))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("↩", fontSize = 30.sp, color = Color.White,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 16.dp))
            Text("JoyPlayer", color = C.Blue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("  |  Settings", color = Color.White, fontSize = 22.sp)
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            GridCells.Adaptive(210.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            gridItems(tiles) { (id, label) ->
                Box(
                    Modifier.height(64.dp).clip(RoundedCornerShape(8.dp))
                        .background(Brush.verticalGradient(listOf(C.Burgundy, C.BurgundyDark)))
                        .clickable { tap(id) }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) { Text(label, color = Color.White, fontSize = 15.sp, maxLines = 2) }
            }
        }
    }

    when (dialog) {
        "lang" -> SDialog("Choose language", { dialog = "" }) {
            Lang.codes.forEachIndexed { i, c ->
                SOpt(Lang.names[i], Lang.code == c) { Lang.set(c); dialog = "" }
            }
        }
        "layout" -> SDialog("Change Layout", { dialog = "" }) {
            var asList by remember { mutableStateOf(AppPrefs.listLayout) }
            var size by remember { mutableStateOf(AppPrefs.posterSize) }
            LayoutOpt("List Layout", asList) { asList = true }
            LayoutOpt("Grid Layout", !asList) { asList = false }
            if (!asList) {
                Text("Poster size (Movies & Series)", color = C.Text, fontSize = 13.sp)
                listOf("Small", "Medium", "Large").forEachIndexed { i, n -> SOpt(n, size == i) { size = i } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { AppPrefs.listLayout = asList; AppPrefs.posterSize = size; dialog = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy),
                    modifier = Modifier.weight(1f)
                ) { Text("OK") }
                Button(
                    onClick = { dialog = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = C.BurgundyDark),
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
            }
        }
        "sort" -> ChoiceDialog(
            "Live channel sort", listOf("Default (as provided)", "A → Z", "Z → A"), AppPrefs.liveSort,
            { AppPrefs.liveSort = it; dialog = "" }, { dialog = "" }
        )
        "format" -> SDialog("Live Stream Format", { dialog = "" }) {
            var fmt by remember { mutableStateOf(AppPrefs.liveFormat.coerceIn(0, 2)) }
            LayoutOpt("Default", fmt == 0) { fmt = 0 }
            LayoutOpt("MPEGTS(.ts)", fmt == 1) { fmt = 1 }
            LayoutOpt("HLS(.m3u8)", fmt == 2) { fmt = 2 }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { AppPrefs.liveFormat = fmt; dialog = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy),
                    modifier = Modifier.weight(1f)
                ) { Text("OK") }
                Button(
                    onClick = { dialog = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = C.BurgundyDark),
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
            }
        }
        "clock" -> ChoiceDialog(
            "Time format", listOf("24-hour", "12-hour (AM/PM)"), if (AppPrefs.clock24) 0 else 1,
            { AppPrefs.clock24 = it == 0; dialog = "" }, { dialog = "" }
        )
        "auto" -> SDialog("Automatic", { dialog = "" }) {
            var ac by remember { mutableStateOf(AppPrefs.autoConnect) }
            var asub by remember { mutableStateOf(subs.auto) }
            var upd by remember { mutableStateOf(AppPrefs.autoUpdate.coerceIn(0, 2)) }
            LayoutOpt("Auto-Update Live, Movie & Series everytime", upd == 0) { upd = 0 }
            LayoutOpt("Auto-Update Live, Movie & Series everyday", upd == 1) { upd = 1 }
            LayoutOpt("Auto-Update Live, Movie & Series after 2 days", upd == 2) { upd = 2 }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(ac, { ac = it; AppPrefs.autoConnect = it })
                Text("  Auto-connect to the last playlist on launch", color = Color.White, fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(asub, { asub = it; subs.auto = it })
                Text("  Auto-load subtitles (movies & series)", color = Color.White, fontSize = 14.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { AppPrefs.autoUpdate = upd; dialog = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy),
                    modifier = Modifier.weight(1f)
                ) { Text("OK") }
                Button(
                    onClick = { dialog = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = C.BurgundyDark),
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
            }
        }
        "sub" -> SDialog("Subtitle Settings", { dialog = "" }, maxHeight = 460.dp) {
            var enabled by remember { mutableStateOf(AppPrefs.subEnabled) }
            var px by remember { mutableStateOf(AppPrefs.subPx.toFloat()) }
            var fg by remember { mutableStateOf(AppPrefs.subFg) }
            var bg by remember { mutableStateOf(AppPrefs.subBg) }
            var lang by remember { mutableStateOf(subs.lang) }
            var key by remember { mutableStateOf(subs.key) }
            SubBlock {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Subtitle", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(enabled, { enabled = it })
                }
            }
            SubBlock {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Subtitle Size", color = Color.White, fontSize = 15.sp)
                    Slider(
                        px, { px = it }, valueRange = 16f..80f, steps = 15,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                    )
                    Text("${px.toInt()}px", color = Color.White, fontSize = 13.sp)
                }
            }
            SubBlock {
                Text("Subtitle Color", color = Color.White, fontSize = 15.sp)
                ColorChoices(subFgColors, fg) { fg = it }
            }
            SubBlock {
                Text("Subtitle Background", color = Color.White, fontSize = 15.sp)
                ColorChoices(subBgColors, bg) { bg = it }
            }
            Box(
                Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Subtitle Test", color = Color(fg), fontSize = (px * 0.6f).sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(bg)).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text("Preferred online language", color = C.Text, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                setLangs.forEach { (code, name) ->
                    FilterChip(lang == code, { lang = code }, { Text(name) })
                }
            }
            OutlinedTextField(key, { key = it }, singleLine = true, label = { Text("SubDL API key") })
            Text("Get a free key from subdl.com", color = C.Text, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        AppPrefs.subEnabled = enabled
                        AppPrefs.subPx = px.toInt()
                        AppPrefs.subFg = fg
                        AppPrefs.subBg = bg
                        subs.lang = lang
                        if (key.trim() != subs.key) subs.key = key
                        dialog = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy),
                    modifier = Modifier.weight(1f)
                ) { Text("OK") }
                Button(
                    onClick = { dialog = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = C.BurgundyDark),
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
            }
        }
        "pip" -> PipDialog { dialog = "" }
        "backup" -> BackupDialog { dialog = "" }
        "update" -> UpdateDialog { dialog = "" }
        "parental" -> ParentalDialog(active, channels) { dialog = "" }
        "hlive" -> CategoryPickerLoader("live", false, active, channels) { dialog = "" }
        "hvod" -> CategoryPickerLoader("movies", false, active, channels) { dialog = "" }
        "hseries" -> CategoryPickerLoader("series", false, active, channels) { dialog = "" }
        "hchan" -> AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text("Clear history?") },
            text = { Text("This clears the recently watched channels list.") },
            confirmButton = {
                TextButton(onClick = { LiveHistory.clear(); dialog = ""; toast("Done") }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } }
        )
        "hmovies", "hseriesh" -> {
            val kind = if (dialog == "hmovies") "movies" else "series"
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("Clear history?") },
                text = { Text("This clears the 'Resume to Watch' list for $kind.") },
                confirmButton = {
                    TextButton(onClick = { clearRecents(ctx, kind); dialog = ""; toast("Done") }) { Text("Clear") }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } }
            )
        }
    }
}
