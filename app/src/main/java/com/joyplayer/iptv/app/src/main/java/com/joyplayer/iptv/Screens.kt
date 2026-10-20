package com.joyplayer.iptv

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object C {
    val Bg = Color(0xFF070C20)
    val Panel = Color(0xFF0F1737)
    val Row = Color(0xFF18234F)
    val Burgundy = Color(0xFF8A1538)
    val BurgundyDark = Color(0xFF4A0E22)
    val Blue = Color(0xFF3B82F6)
    val Text = Color(0xFFEAF0FF)
}

val JoyColors = darkColorScheme(
    primary = C.Blue, onPrimary = Color.White, secondary = C.Burgundy,
    background = C.Bg, surface = C.Bg, onBackground = C.Text, onSurface = C.Text
)

class FavStore(context: Context) {
    private val prefs = context.getSharedPreferences("joyplayer_fav", Context.MODE_PRIVATE)
    fun load(): Set<String> = prefs.getStringSet("fav", emptySet())?.toSet() ?: emptySet()
    fun save(s: Set<String>) { prefs.edit().putStringSet("fav", s).apply() }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val store = remember { ListStore(ctx) }
    val favStore = remember { FavStore(ctx) }
    var lists by remember { mutableStateOf(store.load()) }
    var favs by remember { mutableStateOf(favStore.load()) }
    var adding by remember { mutableStateOf(lists.isEmpty()) }
    var active by remember { mutableStateOf<SavedList?>(null) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var screen by remember { mutableStateOf("home") }
    var playing by remember { mutableStateOf<Channel?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        (ctx as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun soon() = Toast.makeText(ctx, "قريباً", Toast.LENGTH_SHORT).show()

    fun openList(l: SavedList, onOk: () -> Unit = {}) {
        scope.launch {
            loading = true; error = null
            try {
                val res = withContext(Dispatchers.IO) {
                    if (l.type == "m3u") M3uParser.fetch(l.url) else Xtream.load(l.server, l.user, l.pass)
                }
                channels = res; active = l; screen = "home"
                onOk()
            } catch (e: Exception) {
                error = e.message ?: "Error"
            }
            loading = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            channels.isEmpty() && adding -> {
                BackHandler(enabled = lists.isNotEmpty()) { adding = false; error = null }
                AddScreen(loading, error) { l ->
                    openList(l) { lists = lists + l; store.save(lists); adding = false }
                }
            }
            channels.isEmpty() -> ListsScreen(
                lists, loading, error,
                onOpen = { openList(it) },
                onDelete = { l ->
                    lists = lists.filter { it.id != l.id }
                    store.save(lists)
                    if (lists.isEmpty()) adding = true
                },
                onAdd = { error = null; adding = true }
            )
            screen == "live" -> {
                BackHandler(enabled = playing == null) { screen = "home" }
                LiveScreen(
                    channels, favs, playing != null,
                    onFav = { c ->
                        favs = if (c.url in favs) favs - c.url else favs + c.url
                        favStore.save(favs)
                    },
                    onHome = { screen = "home" },
                    onSoon = { soon() },
                    onFull = { playing = it }
                )
            }
            else -> {
                BackHandler(enabled = playing == null) { channels = emptyList() }
                HomeScreen(
                    active?.name ?: "", loading, error,
                    onLive = { screen = "live" },
                    onSoon = { soon() },
                    onChange = { channels = emptyList(); adding = false; error = null },
                    onReload = { active?.let { openList(it) } },
                    onExit = { (ctx as? Activity)?.finish() }
                )
            }
        }
        playing?.let { PlayerScreen(it) { playing = null } }
    }
}

@Composable
fun Tile(label: String, icon: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(C.Burgundy, C.BurgundyDark)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 34.sp)
            Text(label, fontSize = 15.sp, color = C.Text)
        }
    }
}

@Composable
fun SideBtn(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .border(1.dp, C.Burgundy, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = C.Text, fontSize = 15.sp) }
}

@Composable
fun HomeScreen(
    listName: String, loading: Boolean, error: String?,
    onLive: () -> Unit, onSoon: () -> Unit, onChange: () -> Unit, onReload: () -> Unit, onExit: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E1A4A), C.Bg)))) {
        Text(
            if (loading) "Loading..." else listName, color = C.Text,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        )
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(0.88f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("JoyPlayer", fontSize = 34.sp, color = C.Blue, fontWeight = FontWeight.Bold)
            Text("IPTV PRO", fontSize = 12.sp, color = C.Burgundy, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Live", "📺", Modifier.weight(1.6f).fillMaxHeight(), onLive)
                Column(Modifier.weight(2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("Movies", "🎬", Modifier.weight(1f).fillMaxHeight(), onSoon)
                        Tile("Series", "🎞️", Modifier.weight(1f).fillMaxHeight(), onSoon)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("Account", "👤", Modifier.weight(1f).fillMaxHeight(), onSoon)
                        Tile("Change Playlist", "🔁", Modifier.weight(1f).fillMaxHeight(), onChange)
                    }
                }
                Column(Modifier.weight(1.6f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SideBtn("⚙  Settings", Modifier.weight(1f).fillMaxWidth(), onSoon)
                    SideBtn("⟳  Reload", Modifier.weight(1f).fillMaxWidth(), onReload)
                    SideBtn("⏻  Exit", Modifier.weight(1f).fillMaxWidth(), onExit)
                }
            }
        }
        error?.let {
            Text(it, color = Color(0xFFFF8A8A), modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
}

@Composable
fun NavItem(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (active) C.Blue else C.Text, fontSize = 17.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
fun CatRow(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) C.Burgundy else C.Row)
            .clickable(onClick = onClick).padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp, modifier = Modifier.weight(1f)
        )
        Text("$count", color = if (selected) Color.White else C.Blue, fontSize = 14.sp)
    }
}

@Composable
fun LiveScreen(
    all: List<Channel>, favs: Set<String>, paused: Boolean,
    onFav: (Channel) -> Unit, onHome: () -> Unit, onSoon: () -> Unit, onFull: (Channel) -> Unit
) {
    val ctx = LocalContext.current
    var cat by rememberSaveable { mutableStateOf("*all*") }
    var query by rememberSaveable { mutableStateOf("") }
    var selUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = remember(all) { all.groupBy { it.group }.map { it.key to it.value.size } }
    val favCount = remember(all, favs) { all.count { it.url in favs } }
    val shown = remember(all, favs, cat, query) {
        all.filter { c ->
            val inCat = when (cat) {
                "*all*" -> true
                "*fav*" -> c.url in favs
                else -> c.group == cat
            }
            inCat && (query.isBlank() || c.name.contains(query, true))
        }
    }
    val sel = remember(all, selUrl) { all.firstOrNull { it.url == selUrl } }
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(selUrl, paused) {
        if (sel == null || paused) {
            player.pause()
        } else {
            player.setMediaItem(MediaItem.fromUri(sel.url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF0B1330)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem("Home", false, onHome)
            NavItem("Live", true) {}
            NavItem("Movies", false, onSoon)
            NavItem("Series", false, onSoon)
            Spacer(Modifier.weight(1f))
            OutlinedTextField(
                query, { query = it }, singleLine = true,
                placeholder = { Text("Search") }, modifier = Modifier.width(240.dp)
            )
        }
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(0.27f).fillMaxHeight().background(C.Panel)) {
                item { CatRow("All", all.size, cat == "*all*") { cat = "*all*" } }
                item { CatRow("Favorite", favCount, cat == "*fav*") { cat = "*fav*" } }
                items(groups) { (g, n) -> CatRow(g, n, cat == g) { cat = g } }
            }
            LazyColumn(Modifier.weight(0.37f).fillMaxHeight()) {
                itemsIndexed(shown) { i, ch ->
                    val isSel = ch.url == selUrl
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) C.Blue else C.Row)
                            .clickable { if (isSel) onFull(ch) else selUrl = ch.url }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}", color = C.Text.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.width(36.dp))
                        AsyncImage(ch.logo, null, Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            ch.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp, modifier = Modifier.weight(1f)
                        )
                        if (ch.url in favs) Text("★", color = Color(0xFFFFD54F))
                    }
                }
            }
            Column(Modifier.weight(0.36f).fillMaxHeight().padding(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { PlayerView(it).apply { useController = false; this.player = player } },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (sel == null) Text("Select a channel", color = C.Text)
                }
                Spacer(Modifier.height(8.dp))
                Text(sel?.name ?: "", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.weight(1f))
                if (sel != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onFav(sel) },
                            colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy)
                        ) { Text(if (sel.url in favs) "Remove Favorite" else "Add to Favorite", fontSize = 12.sp) }
                        Button(
                            onClick = { onFull(sel) },
                            colors = ButtonDefaults.buttonColors(containerColor = C.Blue)
                        ) { Text("Fullscreen", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}
