package com.joyplayer.iptv

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable
fun App() {
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var playing by remember { mutableStateOf<Channel?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> List<Channel>) {
        scope.launch {
            loading = true; error = null
            try { channels = withContext(Dispatchers.IO) { block() } }
            catch (e: Exception) { error = e.message ?: "Error" }
            loading = false
        }
    }

    when {
        playing != null -> PlayerScreen(playing!!) { playing = null }
        channels.isEmpty() -> AddScreen(loading, error,
            onM3u = { url -> run { M3uParser.fetch(url) } },
            onXtream = { s, u, p -> run { Xtream.load(s, u, p) } })
        else -> {
            BackHandler { channels = emptyList() }
            ChannelsScreen(channels) { playing = it }
        }
    }
}

@Composable
fun AddScreen(loading: Boolean, error: String?, onM3u: (String) -> Unit, onXtream: (String, String, String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("JoyPlayer IPTV", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        TabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("M3U") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Xtream") })
        }
        Spacer(Modifier.height(16.dp))
        if (tab == 0) {
            OutlinedTextField(url, { url = it }, label = { Text("M3U URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(server, { server = it }, label = { Text("Server (http://host:port)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (tab == 0) onM3u(url.trim()) else onXtream(server, user.trim(), pass.trim()) },
            enabled = !loading, modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Loading..." else "Load") }
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
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
