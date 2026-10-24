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
import kotlinx.coroutines.launch
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
    /** Subtitle font id (see SubFonts): "default", "fam:<family>:<style>" or "file:<path>". */
    var subFont: String
        get() = p?.getString("subfont", "default") ?: "default"
        set(v) { p?.edit()?.putString("subfont", v)?.apply() }
    var subBg: Int
        get() = if (p?.contains("subbg") == true) gi("subbg", 0)
        else if (subBox) android.graphics.Color.argb(170, 0, 0, 0) else android.graphics.Color.TRANSPARENT
        set(v) { pi("subbg", v) }
    /** Program-guide link of a playlist: the one typed in Settings, else the one found in the M3U header. */
    fun epgUrl(listId: String): String = epgManual(listId).ifBlank { p?.getString("epg_auto_$listId", "") ?: "" }
    fun epgManual(listId: String): String = p?.getString("epg_$listId", "") ?: ""
    fun setEpgManual(listId: String, v: String) { p?.edit()?.putString("epg_$listId", v.trim())?.apply() }
    fun setEpgAuto(listId: String, v: String) { p?.edit()?.putString("epg_auto_$listId", v.trim())?.apply() }

    var wallpaper: Int
        get() = gi("wallpaper", 0)
        set(v) { pi("wallpaper", v) }
    var tileBox: Int
        get() = gi("tile_box", 0)
        set(v) { pi("tile_box", v) }
    var tileBorder: Int
        get() = gi("tile_border", 0)
        set(v) { pi("tile_border", v) }
    var tileText: Int
        get() = gi("tile_text", 0)
        set(v) { pi("tile_text", v) }
    var tileFont: Int
        get() = gi("tile_font", 0)
        set(v) { pi("tile_font", v) }
    var tileTextSize: Int
        get() = gi("tile_tsize", 1)
        set(v) { pi("tile_tsize", v) }
    var tileIconSize: Int
        get() = gi("tile_isize", 1)
        set(v) { pi("tile_isize", v) }
    var listLayout: Boolean
        get() = gi("listlayout", 0) == 1
        set(v) { pi("listlayout", if (v) 1 else 0) }
    var liveSort: Int
        get() = gi("livesort", 0)
        set(v) { pi("livesort", v) }
    /** Preferred audio language of movies / channels ("" = the file's own default). */
    var prefAudio: String
        get() = p?.getString("prefaudio", "ar") ?: "ar"
        set(v) { p?.edit()?.putString("prefaudio", v)?.apply() }
    /** Preferred subtitle language ("" = none unless the file says so). */
    var prefSub: String
        get() = p?.getString("prefsub", "") ?: ""
        set(v) { p?.edit()?.putString("prefsub", v)?.apply() }
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

    /** Favorite categories (pinned to the top of the category list, shown with a star). */
    fun favCats(kind: String, listId: String?): Set<String> =
        if (listId == null) emptySet() else gs("favcat_${kind}_$listId")

    fun toggleFavCat(kind: String, listId: String, cat: String): Set<String> {
        val cur = gs("favcat_${kind}_$listId")
        val next = if (cat in cur) cur - cat else cur + cat
        ps("favcat_${kind}_$listId", next)
        return next
    }

    /** Own order of the categories, saved as the list of names. */
    fun catOrder(kind: String, listId: String?): List<String> =
        if (listId == null) emptyList()
        else (p?.getString("catorder_${kind}_$listId", "") ?: "").split("\n").filter { it.isNotEmpty() }

    fun setCatOrder(kind: String, listId: String, v: List<String>) {
        p?.edit()?.putString("catorder_${kind}_$listId", v.joinToString("\n"))?.apply()
    }

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
                CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, SubFonts.typeface(subFont)
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
        "wall" to "🖼️  Main Screen Background",
        "tiles" to "🎨  Main Buttons Style",
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
        "epg" to "📅  EPG (XMLTV)",
        "backup" to "💾  Backup & Restore",
        "cache" to "🗑️  Clear Cache",
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
            .screenBg()
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
                val tbc = TileStyle.borderColor
                Box(
                    Modifier.height(64.dp).clip(RoundedCornerShape(8.dp))
                        .background(Brush.verticalGradient(TileStyle.gradient()))
                        .then(if (tbc != 0) Modifier.border(2.dp, Color(tbc), RoundedCornerShape(8.dp)) else Modifier)
                        .clickable { tap(id) }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        label,
                        color = if (TileStyle.textColor == 0) Color.White else Color(TileStyle.textColor),
                        fontSize = (15f * TileStyle.scales[TileStyle.textSize]).sp,
                        fontFamily = TileStyle.fontFamilies[TileStyle.font], maxLines = 2
                    )
                }
            }
        }
    }

    when (dialog) {
        "lang" -> SDialog("Choose language", { dialog = "" }) {
            Lang.codes.forEachIndexed { i, c ->
                SOpt(Lang.names[i], Lang.code == c) { Lang.set(c); dialog = "" }
            }
        }
        "tiles" -> SDialog("Main Buttons Style", { dialog = "" }, maxHeight = 420.dp) {
            Tile("Live", "📺", Modifier.fillMaxWidth().height(96.dp)) {}
            SubBlock {
                Text("Button Color", color = Color.White, fontSize = 15.sp)
                ColorChoices(TileStyle.boxColors, TileStyle.boxColor) { TileStyle.boxColor = it }
            }
            SubBlock {
                Text("Border Color", color = Color.White, fontSize = 15.sp)
                ColorChoices(TileStyle.borderColors, TileStyle.borderColor) { TileStyle.borderColor = it }
            }
            SubBlock {
                Text("Text Color", color = Color.White, fontSize = 15.sp)
                ColorChoices(TileStyle.textColors, TileStyle.textColor) { TileStyle.textColor = it }
            }
            Text("Font", color = C.Text, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TileStyle.fontNames.forEachIndexed { i, n ->
                    Text(
                        n, color = Color.White, fontSize = 14.sp, fontFamily = TileStyle.fontFamilies[i],
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (TileStyle.font == i) C.Burgundy else C.Row)
                            .clickable { TileStyle.font = i }.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            Text("Text size", color = C.Text, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TileStyle.sizeNames.forEachIndexed { i, n ->
                    Text(
                        n, color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (TileStyle.textSize == i) C.Burgundy else C.Row)
                            .clickable { TileStyle.textSize = i }.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            Text("Icon size", color = C.Text, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TileStyle.sizeNames.forEachIndexed { i, n ->
                    Text(
                        n, color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (TileStyle.iconSize == i) C.Burgundy else C.Row)
                            .clickable { TileStyle.iconSize = i }.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            SOpt("Reset to default", false) { TileStyle.reset() }
        }
        "wall" -> SDialog("Choose Main Screen Background", { dialog = "" }, maxHeight = 360.dp) {
            val sel = Wallpapers.current()
            Wallpapers.list.indices.toList().chunked(3).forEach { rowIdx ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowIdx.forEach { i ->
                        Box(
                            Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(8.dp))
                                .wallpaper(i)
                                .then(if (i == sel) Modifier.border(3.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { Wallpapers.set(i) },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(Wallpapers.list[i].name, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                }
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
            var pAudio by remember { mutableStateOf(AppPrefs.prefAudio) }
            var pSub by remember { mutableStateOf(AppPrefs.prefSub) }
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
            Text("Preferred audio language", color = C.Text, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("" to "Default") + setLangs).forEach { (code, name) ->
                    FilterChip(pAudio.equals(code, true), { pAudio = code.lowercase() }, { Text(name) })
                }
            }
            Text("Preferred subtitle language", color = C.Text, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("" to "Off") + setLangs).forEach { (code, name) ->
                    FilterChip(pSub.equals(code, true), { pSub = code.lowercase() }, { Text(name) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { AppPrefs.autoUpdate = upd; AppPrefs.prefAudio = pAudio; AppPrefs.prefSub = pSub; dialog = "" },
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
        "sub" -> SDialog("Subtitle Settings", { dialog = "" }, maxHeight = 560.dp) {
            var enabled by remember { mutableStateOf(AppPrefs.subEnabled) }
            var px by remember { mutableStateOf(AppPrefs.subPx.toFloat()) }
            var fg by remember { mutableStateOf(AppPrefs.subFg) }
            var bg by remember { mutableStateOf(AppPrefs.subBg) }
            var lang by remember { mutableStateOf(subs.lang) }
            var key by remember { mutableStateOf(subs.key) }
            var font by remember { mutableStateOf(AppPrefs.subFont) }
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
            SubBlock {
                Text("Subtitle Font:  " + SubFonts.label(font), color = Color.White, fontSize = 15.sp)
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxWidth().height(190.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SubFonts.list().forEach { e ->
                        item(key = e.id) {
                            val fam = remember(e.id) { SubFonts.typeface(e.id)?.let { androidx.compose.ui.text.font.FontFamily(it) } }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                    .background(if (font == e.id) C.Burgundy else C.BurgundyDark)
                                    .clickable { font = e.id }.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Text(
                                    e.label, color = Color.White, fontSize = 16.sp,
                                    fontFamily = fam, modifier = Modifier.weight(1f)
                                )
                                if (font == e.id) Text("✓", color = Color.White)
                            }
                        }
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val prevFam = remember(font) { SubFonts.typeface(font)?.let { androidx.compose.ui.text.font.FontFamily(it) } }
                androidx.compose.material3.Text(
                    "Subtitle Test  —  اختبار الترجمة", color = Color(fg), fontSize = (px * 0.6f).sp,
                    fontWeight = if (font == "default") FontWeight.Bold else null, fontFamily = prevFam,
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
                        AppPrefs.subFont = font
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
        "cache" -> CacheDialog { dialog = "" }
        "epg" -> SDialog("EPG (XMLTV)", { dialog = "" }, maxHeight = 380.dp) {
            if (active == null) {
                Text("Connect to a playlist first", color = Color(0xFFFF8A8A), fontSize = 13.sp)
            } else {
                var url by remember { mutableStateOf(AppPrefs.epgManual(active.id)) }
                val auto = AppPrefs.epgUrl(active.id).takeIf { AppPrefs.epgManual(active.id).isBlank() }.orEmpty()
                Text(
                    "Program guide for M3U playlists. Leave empty to use the link found inside the playlist.",
                    color = C.Text, fontSize = 13.sp
                )
                if (auto.isNotBlank()) Text(auto, color = Color(0xFF6EE7A0), fontSize = 12.sp)
                OutlinedTextField(
                    url, { url = it }, singleLine = true, label = { Text("XMLTV link (.xml or .xml.gz)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            AppPrefs.setEpgManual(active.id, url)
                            XmlTv.activeUrl = AppPrefs.epgUrl(active.id)
                            XmlTv.reset()
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
        }
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


private fun dirSize(f: java.io.File?): Long =
    if (f == null || !f.exists()) 0L else if (f.isFile) f.length() else (f.listFiles()?.sumOf { dirSize(it) } ?: 0L)

private fun fmtSize(b: Long): String =
    if (b < 1024L * 1024L) "${b / 1024} KB" else "%.1f MB".format(b / 1048576.0)

/** Empties the files the app can rebuild by itself; shows how much space each part uses. */
@Composable
private fun CacheDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var imgs by remember { mutableStateOf(-1L) }
    var temp by remember { mutableStateOf(0L) }
    var lists by remember { mutableStateOf(0L) }
    var tick by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val i = dirSize(java.io.File(ctx.cacheDir, "img"))
            imgs = i
            temp = (dirSize(ctx.cacheDir) - i).coerceAtLeast(0L)
            lists = dirSize(java.io.File(ctx.filesDir, "content_cache"))
        }
    }
    fun clear(withLists: Boolean) {
        busy = true
        scope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val il = coil.Coil.imageLoader(ctx)
                    il.memoryCache?.clear()
                    il.diskCache?.clear()
                }
                // empty the folders but keep them, other parts of the app expect them to exist
                ctx.cacheDir.listFiles()?.forEach { f ->
                    if (f.name == "img") return@forEach
                    if (f.isDirectory) f.listFiles()?.forEach { it.deleteRecursively() } else f.delete()
                }
                if (withLists) {
                    java.io.File(ctx.filesDir, "content_cache").listFiles()?.forEach { it.delete() }
                    Vod.invalidateAll()
                }
            }
            busy = false
            tick++
            Toast.makeText(ctx, Lang.tr(if (withLists) "Cache and saved lists cleared" else "Cache cleared"), Toast.LENGTH_SHORT).show()
        }
    }
    SDialog("Clear Cache", onDismiss, maxHeight = 460.dp) {
        Text(
            "The cache holds posters and logos you already saw, temporary files and the saved copy of your channel, movie and series lists. " +
                "Clearing it frees space and fixes pictures that show wrong or stay blank. Nothing important is lost: favorites, history and settings stay.",
            color = C.Text, fontSize = 13.sp
        )
        SubBlock {
            Text("Pictures (posters, logos):  " + if (imgs < 0) "..." else fmtSize(imgs), color = Color.White, fontSize = 14.sp)
            Text("Temporary files:  " + fmtSize(temp), color = Color.White, fontSize = 14.sp)
            Text("Saved lists (make the app open fast):  " + fmtSize(lists), color = Color.White, fontSize = 14.sp)
        }
        Button(
            onClick = { clear(false) }, enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy), modifier = Modifier.fillMaxWidth()
        ) { Text("Clear pictures & temporary files") }
        Text(
            "Clearing the saved lists too makes the next start download everything again (slower, but gets a fresh copy).",
            color = C.Text, fontSize = 12.sp
        )
        Button(
            onClick = { clear(true) }, enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = C.BurgundyDark), modifier = Modifier.fillMaxWidth()
        ) { Text("Clear everything (including saved lists)") }
        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = C.Blue), modifier = Modifier.fillMaxWidth()) { Text("Close") }
    }
}
