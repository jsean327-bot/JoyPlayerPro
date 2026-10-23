package com.joyplayer.iptv

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        setContent {
            MaterialTheme(colorScheme = JoyColors) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.foundation.LocalIndication provides TvIndication,
                    androidx.compose.ui.platform.LocalLayoutDirection provides
                        (if (Lang.code == "ar") androidx.compose.ui.unit.LayoutDirection.Rtl
                         else androidx.compose.ui.unit.LayoutDirection.Ltr)
                ) {
                    Box(Modifier.fillMaxSize().wallpaper(inner = true)) {
                        Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) { JoyApp() }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipState.canEnter && PipState.isEnabled(this)) PipState.enter(this)
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val store = remember { ListStore(ctx) }
    var lists by remember { mutableStateOf(store.load()) }
    var adding by remember { mutableStateOf(lists.isEmpty()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var playing by remember { mutableStateOf<Channel?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun run(onOk: () -> Unit = {}, block: suspend () -> List<Channel>) {
        scope.launch {
            loading = true; error = null
            try {
                channels = withContext(Dispatchers.IO) { block() }
                onOk()
            } catch (e: Exception) {
                error = e.message ?: "Error"
            }
            loading = false
        }
    }

    suspend fun fetchList(l: SavedList): List<Channel> =
        if (l.type == "m3u") M3uParser.fetch(l.url) else Xtream.load(l.server, l.user, l.pass)

    when {
        playing != null -> PlayerScreen(playing!!) { playing = null }
        channels.isNotEmpty() -> {
            BackHandler { channels = emptyList() }
            ChannelsScreen(channels) { playing = it }
        }
        adding -> {
            BackHandler(enabled = lists.isNotEmpty()) { adding = false; error = null }
            AddScreen(loading, error) { l ->
                run(onOk = {
                    lists = lists + l
                    store.save(lists)
                    adding = false
                }) { fetchList(l) }
            }
        }
        else -> ListsScreen(
            lists, loading, error,
            onOpen = { l -> run { fetchList(l) } },
            onDelete = { l ->
                lists = lists.filter { it.id != l.id }
                store.save(lists)
                if (lists.isEmpty()) adding = true
            },
            onAdd = { error = null; adding = true }
        )
    }
}

@Composable
fun ListsScreen(
    lists: List<SavedList>, loading: Boolean, error: String?,
    onOpen: (SavedList) -> Unit, onDelete: (SavedList) -> Unit, onAdd: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("JoyPlayer IPTV", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        LazyColumn(Modifier.weight(1f)) {
            items(lists) { l ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !loading) { onOpen(l) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (l.type == "m3u") "M3U" else "Xtream", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onDelete(l) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                }
            }
        }
        Button(onClick = onAdd, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("+ Add list") }
    }
}

@Composable
fun AddScreen(loading: Boolean, error: String?, onSave: (SavedList) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("JoyPlayer IPTV", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        TabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("M3U") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Xtream") })
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it }, label = { Text("List name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (tab == 0) {
            OutlinedTextField(url, { url = it }, label = { Text("M3U URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(server, { server = it }, label = { Text("Server (http://host:port)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val id = System.currentTimeMillis().toString()
                val n = name.trim().ifBlank { "My List" }
                onSave(
                    if (tab == 0) SavedList(id, n, "m3u", url = url.trim())
                    else SavedList(id, n, "xtream", server = server.trim(), user = user.trim(), pass = pass.trim())
                )
            },
            enabled = !loading, modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Loading..." else "Save & Load") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
fun ChannelsScreen(all: List<Channel>, onPlay: (Channel) -> Unit) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    val groups = remember(all) { all.map { it.group }.distinct() }
    val shown = remember(all, query, group) {
        all.filter { (group == null || it.group == group) && it.name.contains(query, true) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(group == null, { group = null }, { Text("All") }) }
            items(groups) { g -> FilterChip(group == g, { group = g }, { Text(g) }) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown) { ch ->
                Row(Modifier.fillMaxWidth().clickable { onPlay(ch) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(ch.logo, null, Modifier.size(48.dp).clip(MaterialTheme.shapes.small))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(ch.name, maxLines = 1)
                        Text(ch.group, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(ch: Channel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val player = remember(ch) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(ch.url)); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } },
        modifier = Modifier.fillMaxSize()
    )
}
