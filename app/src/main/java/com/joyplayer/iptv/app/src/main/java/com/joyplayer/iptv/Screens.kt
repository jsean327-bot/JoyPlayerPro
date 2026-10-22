package com.joyplayer.iptv

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    const val TRACK_TYPE_AUDIO = androidx.media3.common.C.TRACK_TYPE_AUDIO
    const val TRACK_TYPE_VIDEO = androidx.media3.common.C.TRACK_TYPE_VIDEO
    const val TRACK_TYPE_TEXT = androidx.media3.common.C.TRACK_TYPE_TEXT
    const val SELECTION_FLAG_DEFAULT = androidx.media3.common.C.SELECTION_FLAG_DEFAULT
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

    fun soon() = Toast.makeText(ctx, "soon", Toast.LENGTH_SHORT).show()

    fun openList(l0: SavedList, onOk: () -> Unit = {}) {
        scope.launch {
            loading = true; error = null
            try {
                val l = SmartList.normalize(l0)
                val res = withContext(Dispatchers.IO) {
                    if (l.type == "m3u") SmartList.fetchM3u(l.url) else Xtream.load(l.server, l.user, l.pass)
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
            screen == "movies" || screen == "series" -> {
                BackHandler(enabled = playing == null) { screen = "home" }
                LibraryScreen(screen, active, { screen = it }, { playing = it })
            }
            screen == "live" -> {
                BackHandler(enabled = playing == null) { screen = "home" }
                LiveScreen(
                    channels, favs, playing != null,
                    onFav = { c ->
                        favs = if (c.url in favs) favs - c.url else favs + c.url
                        favStore.save(favs)
                    },
                    onNav = { screen = it },
                    onFull = { playing = it }
                )
            }
            else -> {
                BackHandler(enabled = playing == null) { channels = emptyList() }
                HomeScreen(
                    active?.name ?: "", loading, error,
                    onNav = { screen = it },
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
    onNav: (String) -> Unit, onSoon: () -> Unit, onChange: () -> Unit, onReload: () -> Unit, onExit: () -> Unit,
    onPlay: (Channel) -> Unit = {}
) {
    val compact = rememberIsCompact()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E1A4A), C.Bg)))) {
        Column(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            Text(if (loading) "Loading..." else listName, color = C.Text)
            ClockText()
        }
        if (compact) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("JoyPlayer", fontSize = 34.sp, color = C.Blue, fontWeight = FontWeight.Bold)
                Text("IPTV PRO", fontSize = 12.sp, color = C.Burgundy, fontWeight = FontWeight.Bold)
                ContinueRow(onPlay, Modifier.fillMaxWidth())
                Tile("Live", "📺", Modifier.fillMaxWidth().height(120.dp)) { onNav("live") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Movies", "🎬", Modifier.weight(1f).height(100.dp)) { onNav("movies") }
                    Tile("Series", "🎞️", Modifier.weight(1f).height(100.dp)) { onNav("series") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Account", "👤", Modifier.weight(1f).height(100.dp), onSoon)
                    Tile("Change Playlist", "🔁", Modifier.weight(1f).height(100.dp), onChange)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SideBtn("⚙  Settings", Modifier.weight(1f).height(56.dp)) { onNav("settings") }
                    SideBtn("⟳  Reload", Modifier.weight(1f).height(56.dp), onReload)
                }
                SideBtn("⏻  Exit", Modifier.fillMaxWidth().height(56.dp), onExit)
            }
        } else Column(
            Modifier.align(Alignment.Center).fillMaxWidth(0.88f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("JoyPlayer", fontSize = 34.sp, color = C.Blue, fontWeight = FontWeight.Bold)
            Text("IPTV PRO", fontSize = 12.sp, color = C.Burgundy, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Live", "📺", Modifier.weight(1.6f).fillMaxHeight()) { onNav("live") }
                Column(Modifier.weight(2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("Movies", "🎬", Modifier.weight(1f).fillMaxHeight()) { onNav("movies") }
                        Tile("Series", "🎞️", Modifier.weight(1f).fillMaxHeight()) { onNav("series") }
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("Account", "👤", Modifier.weight(1f).fillMaxHeight(), onSoon)
                        Tile("Change Playlist", "🔁", Modifier.weight(1f).fillMaxHeight(), onChange)
                    }
                }
                Column(Modifier.weight(1.6f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SideBtn("⚙  Settings", Modifier.weight(1f).fillMaxWidth()) { onNav("settings") }
                    SideBtn("⟳  Reload", Modifier.weight(1f).fillMaxWidth(), onReload)
                    SideBtn("⏻  Exit", Modifier.weight(1f).fillMaxWidth(), onExit)
                }
            }
            if (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp >= 480) {
                Spacer(Modifier.height(16.dp))
                ContinueRow(onPlay, Modifier.fillMaxWidth())
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
    onFav: (Channel) -> Unit, onNav: (String) -> Unit, onFull: (Channel) -> Unit
) {
    val ctx = LocalContext.current
    var cat by rememberSaveable { mutableStateOf("*all*") }
    var query by rememberSaveable { mutableStateOf("") }
    var selUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val histVersion = LiveHistory.version
    val groups = remember(all) { all.groupBy { it.group }.map { it.key to it.value.size } }
    val favCount = remember(all, favs) { all.count { it.url in favs } }
    val recentCount = remember(all, histVersion) {
        val urls = all.asSequence().map { it.url }.toHashSet()
        LiveHistory.list().count { it in urls }
    }
    val index by produceState(emptyList<String>(), all) {
        value = withContext(Dispatchers.Default) { all.map { normSearch(it.name + " " + it.group) } }
    }
    val shown = remember(all, index, favs, cat, query, histVersion) {
        val words = normSearch(query).split(' ').filter { it.isNotBlank() }
        when {
            words.isEmpty() && cat == "*rec*" -> {
                val byUrl = all.associateBy { it.url }
                LiveHistory.list().mapNotNull { byUrl[it] }
            }
            words.isEmpty() -> all.filter { c ->
                when (cat) {
                    "*all*" -> true
                    "*fav*" -> c.url in favs
                    else -> c.group == cat
                }
            }
            index.size == all.size -> all.filterIndexed { i, _ -> words.all { w -> index[i].contains(w) } }
            else -> all.filter { c -> words.all { w -> normSearch(c.name).contains(w) } }
        }
    }
    val sel = remember(all, selUrl) { all.firstOrNull { it.url == selUrl } }
    val player = rememberLivePlayer(sel, paused)
    val compact = rememberIsCompact()
    val onRowClick: (Channel) -> Unit = { ch ->
        if (ch.url == selUrl) onFull(ch) else { selUrl = ch.url; LiveHistory.push(ch.url) }
    }
    SideEffect {
        LiveZap.list = shown
        LiveZap.play = onFull
        LiveZap.onSelect = { c -> selUrl = c.url; LiveHistory.push(c.url) }
    }

    if (compact) {
        val catOptions = remember(groups, all.size, favCount, recentCount) {
            listOf(
                CatOpt("*all*", "All", all.size),
                CatOpt("*fav*", "Favorite", favCount),
                CatOpt("*rec*", "Recently watched", recentCount)
            ) + groups.map { CatOpt(it.first, it.first, it.second) }
        }
        Column(Modifier.fillMaxSize().background(C.Bg)) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF0B1330))
                    .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem("Home", false) { onNav("home") }
                NavItem("Live", true) {}
                NavItem("Movies", false) { onNav("movies") }
                NavItem("Series", false) { onNav("series") }
            }
            LivePane(player, sel, favs, onFav, onFull, true, Modifier.fillMaxWidth().padding(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryButton(cat, catOptions, { cat = it }, Modifier.weight(0.45f))
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    placeholder = { Text("Search") }, modifier = Modifier.weight(0.55f)
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(shown) { i, ch ->
                    LiveRow(i, ch, ch.url == selUrl, query.isNotBlank(), ch.url in favs) { onRowClick(ch) }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().background(C.Bg)) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF0B1330)).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem("Home", false) { onNav("home") }
                NavItem("Live", true) {}
                NavItem("Movies", false) { onNav("movies") }
                NavItem("Series", false) { onNav("series") }
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
                    item { CatRow("Recently watched", recentCount, cat == "*rec*") { cat = "*rec*" } }
                    items(groups) { (g, n) -> CatRow(g, n, cat == g) { cat = g } }
                }
                LazyColumn(Modifier.weight(0.37f).fillMaxHeight()) {
                    itemsIndexed(shown) { i, ch ->
                        LiveRow(i, ch, ch.url == selUrl, query.isNotBlank(), ch.url in favs) { onRowClick(ch) }
                    }
                }
                LivePane(player, sel, favs, onFav, onFull, false, Modifier.weight(0.36f).fillMaxHeight().padding(8.dp))
            }
        }
    }
}

@Composable
private fun LiveRow(i: Int, ch: Channel, isSel: Boolean, showGroup: Boolean, fav: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSel) C.Blue else C.Row)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${i + 1}", color = C.Text.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.width(36.dp))
        AsyncImage(ch.logo, null, Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ch.name, color = Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontSize = 14.sp
            )
            if (showGroup) {
                Text(
                    ch.group, color = C.Text.copy(alpha = 0.7f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontSize = 11.sp
                )
            }
        }
        if (fav) Text("★", color = Color(0xFFFFD54F))
    }
}

@Composable
private fun LivePane(
    player: ExoPlayer, sel: Channel?, favs: Set<String>,
    onFav: (Channel) -> Unit, onFull: (Channel) -> Unit, compact: Boolean, modifier: Modifier
) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            LivePreviewSurface(player, sel, onFull)
            if (sel == null) Text("Select a channel", color = C.Text)
        }
        Spacer(Modifier.height(8.dp))
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    sel?.name ?: "", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (sel != null) {
                    Button(
                        onClick = { onFav(sel) },
                        colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) { Text(if (sel.url in favs) "★" else "☆", fontSize = 16.sp) }
                    Button(
                        onClick = { onFull(sel) },
                        colors = ButtonDefaults.buttonColors(containerColor = C.Blue),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) { Text("⛶", fontSize = 16.sp) }
                }
            }
            EpgInfo(sel)
        } else {
            Text(sel?.name ?: "", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            EpgInfo(sel)
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
