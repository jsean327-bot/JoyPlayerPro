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
        CrashLog.install(this)
        // images (logos, posters): bigger memory + disk cache, lighter bitmaps, no fade (smoother lists)
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .okHttpClient(Http.client)
                .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.25).build() }
                .diskCache {
                    coil.disk.DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(200L * 1024 * 1024).build()
                }
                .respectCacheHeaders(false)
                .allowRgb565(true)
                .crossfade(false)
                .build()
        )
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
                        CrashNotice()
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
