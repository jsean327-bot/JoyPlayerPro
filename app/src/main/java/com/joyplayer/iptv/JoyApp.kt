package com.joyplayer.iptv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun JoyApp() {
    val ctx = LocalContext.current
    AppPrefs.init(ctx)
    ContentCache.init(ctx)
    val store = remember { ListStore(ctx) }
    val favStore = remember { FavStore(ctx) }
    val prefs = remember { ctx.getSharedPreferences("joyplayer_app", android.content.Context.MODE_PRIVATE) }
    var lists by remember { mutableStateOf(store.load()) }
    var favs by remember { mutableStateOf(favStore.load()) }
    var adding by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<SavedList?>(null) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var screen by remember { mutableStateOf("home") }
    var playlistsFrom by remember { mutableStateOf("home") }
    var playing by remember { mutableStateOf<Channel?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    val sortMode = AppPrefs.liveSort
    val hiddenLive = AppPrefs.hidden("live", active?.id)
    val lockedLive = AppPrefs.locked("live", active?.id)
    val showLocked = Parental.unlocked
    val liveChannels = remember(channels, sortMode, hiddenLive, lockedLive, showLocked) {
        val base = channels.filter { it.group !in hiddenLive && (showLocked || it.group !in lockedLive) }
        when (sortMode) {
            1 -> base.sortedBy { it.name.lowercase() }
            2 -> base.sortedByDescending { it.name.lowercase() }
            else -> base
        }
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun openList(l0: SavedList, force: Boolean = false, onOk: () -> Unit = {}) {
        job?.cancel()
        job = scope.launch {
            loading = true; error = null
            try {
                val l = SmartList.normalize(l0)
                if (force) {
                    ContentCache.clear(l.id)
                    Vod.invalidate(l.id)
                }
                val res = withContext(Dispatchers.IO) {
                    ContentCache.channels(l) ?: try {
                        val fresh =
                            if (l.type == "m3u") SmartList.fetchM3u(l.url) else Xtream.load(l.server, l.user, l.pass)
                        ContentCache.putChannels(l, fresh)
                        fresh
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // no connection: fall back to the last saved copy
                        ContentCache.channels(l, ignoreAge = true) ?: throw e
                    }
                }
                channels = res; active = l
                prefs.edit().putString("last", l0.id).apply()
                onOk()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Error"
            }
            loading = false
        }
    }

    fun go(target: String) {
        when {
            target == "home" || target == "playlists" || target == "settings" -> screen = target
            channels.isNotEmpty() -> screen = target
            lists.isEmpty() -> toast("أضف قائمة أولاً من Change Playlist")
            loading -> toast("جاري الاتصال بالسيرفر...")
            else -> toast("لا يوجد اتصال، جرّب Reload")
        }
    }

    LaunchedEffect(Unit) {
        (ctx as? Activity)?.requestedOrientation =
            if (isTvDevice(ctx)) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        if (AppPrefs.autoConnect) {
            val last = prefs.getString("last", null)
            val l = lists.firstOrNull { it.id == last }
            if (l != null) openList(l)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            adding -> {
                BackHandler { adding = false; error = null }
                AddScreen(loading, error) { l ->
                    openList(l) {
                        lists = lists + l
                        store.save(lists)
                        adding = false
                        screen = "home"
                    }
                }
            }
            screen == "playlists" -> {
                BackHandler(enabled = playing == null) { screen = playlistsFrom }
                PlaylistsScreen(
                    lists, active?.id, false, error, { screen = playlistsFrom },
                    onConnect = { screen = playlistsFrom; openList(it) },
                    onSave = { u ->
                        lists = lists.map { if (it.id == u.id) u else it }
                        store.save(lists)
                    },
                    onDelete = { l ->
                        lists = lists.filter { it.id != l.id }
                        store.save(lists)
                        if (active?.id == l.id) { active = null; channels = emptyList() }
                    },
                    onAdd = { error = null; adding = true }
                )
            }
            screen == "account" -> AccountScreen(active) { screen = "home" }
            screen == "settings" -> SettingsScreen(
                active = active, channels = channels,
                onBack = { screen = "home" },
                onPlaylists = { playlistsFrom = "settings"; screen = "playlists" }
            )
            (screen == "movies" || screen == "series") && channels.isNotEmpty() -> {
                BackHandler(enabled = playing == null) { screen = "home" }
                LibraryScreen(screen, active, { go(it) }, { playing = it })
            }
            screen == "live" && channels.isNotEmpty() -> {
                BackHandler(enabled = playing == null) { screen = "home" }
                LiveScreen(
                    liveChannels, favs, playing != null,
                    onFav = { c ->
                        favs = if (c.url in favs) favs - c.url else favs + c.url
                        favStore.save(favs)
                    },
                    onNav = { go(it) },
                    onFull = { playing = it }
                )
            }
            else -> HomeScreen(
                active?.name ?: if (lists.isEmpty()) "No playlist" else "",
                loading, error,
                onNav = { go(it) },
                onSoon = { if (active != null) screen = "account" else toast("اتصل بقائمة أولاً") },
                onChange = { error = null; playlistsFrom = "home"; screen = "playlists" },
                onReload = {
                    val l = active
                        ?: lists.firstOrNull { it.id == prefs.getString("last", null) }
                        ?: lists.firstOrNull()
                    if (l != null) openList(l, force = true) else toast("أضف قائمة أولاً من Change Playlist")
                },
                onExit = { (ctx as? Activity)?.finish() },
                onPlay = { playing = it }
            )
        }
        playing?.let { ProPlayer(it) { playing = null } }
    }
}

@Composable
private fun MenuBtn(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy)
    ) { Text(label) }
}

@Composable
fun PlaylistsScreen(
    lists: List<SavedList>, activeId: String?, loading: Boolean, error: String?,
    onBack: (() -> Unit)?, onConnect: (SavedList) -> Unit, onSave: (SavedList) -> Unit,
    onDelete: (SavedList) -> Unit, onAdd: () -> Unit
) {
    var menu by remember { mutableStateOf<SavedList?>(null) }
    var edit by remember { mutableStateOf<SavedList?>(null) }
    var del by remember { mutableStateOf<SavedList?>(null) }
    val shape = RoundedCornerShape(10.dp)

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E1A4A), Color(0xFF2A1040), Color(0xFF4A0E22))))
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Text("↩", fontSize = 30.sp, color = Color.White,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 16.dp))
            }
            Text("JoyPlayer", color = C.Blue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("  |  Change Playlist", color = Color.White, fontSize = 22.sp)
        }
        Spacer(Modifier.height(16.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = Color(0xFFFF8A8A), modifier = Modifier.padding(vertical = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            GridCells.Adaptive(230.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            gridItems(lists) { l ->
                val isActive = l.id == activeId
                Column(
                    Modifier.height(130.dp).clip(shape)
                        .background(Brush.verticalGradient(listOf(C.Burgundy, C.BurgundyDark)))
                        .then(if (isActive) Modifier.border(2.dp, C.Blue, shape) else Modifier)
                        .clickable(enabled = !loading) { menu = l }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(l.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (l.type == "m3u") l.url.substringBefore("?") else l.server,
                            color = C.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isActive) {
                        Text("Connected", color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.Blue)
                                .padding(horizontal = 10.dp, vertical = 2.dp))
                    }
                }
            }
            item {
                Column(
                    Modifier.height(130.dp).clip(shape).background(Color(0x33FFFFFF))
                        .clickable(enabled = !loading, onClick = onAdd),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(C.Blue), contentAlignment = Alignment.Center) {
                        Text("+", color = Color.White, fontSize = 26.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Add Playlist", color = Color.White, fontSize = 16.sp)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("JoyPlayer IPTV Pro doesn't sell playlists or subscriptions.",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("JoyPlayer is a general media player and it doesn't include any content or playlists.",
                color = C.Text, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }

    menu?.let { l ->
        Dialog(onDismissRequest = { menu = null }) {
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF14102E)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(l.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                MenuBtn("Connect") { menu = null; onConnect(l) }
                MenuBtn("Edit") { menu = null; edit = l }
                MenuBtn("Delete") { menu = null; del = l }
            }
        }
    }

    edit?.let { l ->
        var name by remember(l.id) { mutableStateOf(l.name) }
        var url by remember(l.id) { mutableStateOf(l.url) }
        var server by remember(l.id) { mutableStateOf(l.server) }
        var user by remember(l.id) { mutableStateOf(l.user) }
        var pass by remember(l.id) { mutableStateOf(l.pass) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text("Edit playlist") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    if (l.type == "m3u") {
                        OutlinedTextField(url, { url = it }, label = { Text("M3U URL") }, singleLine = true)
                    } else {
                        OutlinedTextField(server, { server = it }, label = { Text("Server") }, singleLine = true)
                        OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
                        OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim().ifBlank { l.name }
                    onSave(
                        if (l.type == "m3u") l.copy(name = n, url = url.trim())
                        else l.copy(name = n, server = server.trim(), user = user.trim(), pass = pass.trim())
                    )
                    edit = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } }
        )
    }

    del?.let { l ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("Delete playlist?") },
            text = { Text(l.name) },
            confirmButton = { TextButton(onClick = { onDelete(l); del = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("Cancel") } }
        )
    }
}
