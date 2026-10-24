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
