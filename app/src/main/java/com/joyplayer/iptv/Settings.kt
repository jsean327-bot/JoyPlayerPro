package com.joyplayer.iptv

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
        if (liveFormat == 1 && u.endsWith(".m3u8")) return u.removeSuffix(".m3u8") + ".ts"
        if (liveFormat == 0 && u.endsWith(".ts")) return u.removeSuffix(".ts") + ".m3u8"
        return u
    }

    fun styleSubtitles(v: PlayerView) {
        val sv = v.subtitleView ?: return
        val mult = listOf(0.75f, 1f, 1.3f, 1.7f)[subSize.coerceIn(0, 3)]
        sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * mult)
        val fg = when (subColor) {
            1 -> android.graphics.Color.YELLOW
            2 -> android.graphics.Color.CYAN
            else -> android.graphics.Color.WHITE
        }
        val bg = if (subBox) android.graphics.Color.argb(170, 0, 0, 0) else android.graphics.Color.TRANSPARENT
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

@Composable
fun SDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF14102E)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Column(
                Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
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
        "update" to "⬆️  Update Now"
    )

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    fun tap(id: String) {
        when (id) {
            "playlist" -> onPlaylists()
            "lang", "update" -> toast("soon")
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
        "layout" -> ChoiceDialog(
            "Poster size (Movies & Series)", listOf("Small", "Medium", "Large"), AppPrefs.posterSize,
            { AppPrefs.posterSize = it; dialog = "" }, { dialog = "" }
        )
        "sort" -> ChoiceDialog(
            "Live channel sort", listOf("Default (as provided)", "A → Z", "Z → A"), AppPrefs.liveSort,
            { AppPrefs.liveSort = it; dialog = "" }, { dialog = "" }
        )
        "format" -> ChoiceDialog(
            "Live stream format", listOf("HLS (.m3u8)", "MPEG-TS (.ts)"), AppPrefs.liveFormat,
            { AppPrefs.liveFormat = it; dialog = "" }, { dialog = "" }
        )
        "clock" -> ChoiceDialog(
            "Time format", listOf("24-hour", "12-hour (AM/PM)"), if (AppPrefs.clock24) 0 else 1,
            { AppPrefs.clock24 = it == 0; dialog = "" }, { dialog = "" }
        )
        "auto" -> SDialog("Automatic", { dialog = "" }) {
            var ac by remember { mutableStateOf(AppPrefs.autoConnect) }
            var asub by remember { mutableStateOf(subs.auto) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(ac, { ac = it; AppPrefs.autoConnect = it })
                Text("  Auto-connect to the last playlist on launch", color = Color.White, fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(asub, { asub = it; subs.auto = it })
                Text("  Auto-load subtitles (movies & series)", color = Color.White, fontSize = 14.sp)
            }
        }
        "sub" -> SDialog("Subtitle Settings", { dialog = "" }) {
            var size by remember { mutableStateOf(AppPrefs.subSize) }
            var color by remember { mutableStateOf(AppPrefs.subColor) }
            var box by remember { mutableStateOf(AppPrefs.subBox) }
            var lang by remember { mutableStateOf(subs.lang) }
            var key by remember { mutableStateOf(subs.key) }
            Text("Size", color = C.Text, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Small", "Normal", "Large", "XL").forEachIndexed { i, n ->
                    FilterChip(size == i, { size = i; AppPrefs.subSize = i }, { Text(n) })
                }
            }
            Text("Color", color = C.Text, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("White", "Yellow", "Cyan").forEachIndexed { i, n ->
                    FilterChip(color == i, { color = i; AppPrefs.subColor = i }, { Text(n) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(box, { box = it; AppPrefs.subBox = it })
                Text("  Dark background box", color = Color.White, fontSize = 14.sp)
            }
            Text("Preferred online language", color = C.Text, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                setLangs.forEach { (code, name) ->
                    FilterChip(lang == code, { lang = code; subs.lang = code }, { Text(name) })
                }
            }
            OutlinedTextField(key, { key = it }, singleLine = true, label = { Text("SubDL API key") })
            Button(onClick = { subs.key = key; toast("Saved") }) { Text("Save key") }
        }
        "pip" -> PipDialog { dialog = "" }
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
