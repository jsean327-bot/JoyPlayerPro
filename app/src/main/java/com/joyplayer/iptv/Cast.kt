package com.joyplayer.iptv

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage

/** Registered in AndroidManifest.xml: plays on any Chromecast / Google Cast TV with the default receiver. */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

object CastHelper {
    /** Null on devices without Google Play services (Fire TV, some Chinese phones): the Cast button is hidden. */
    fun context(ctx: Context): CastContext? =
        runCatching { CastContext.getSharedInstance(ctx.applicationContext) }.getOrNull()

    fun request(url: String, title: String, logo: String?, live: Boolean, startMs: Long): MediaLoadRequestData {
        val md = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
        md.putString(MediaMetadata.KEY_TITLE, title)
        if (!logo.isNullOrBlank()) runCatching { md.addImage(WebImage(Uri.parse(logo))) }
        val low = url.substringBefore('?').lowercase()
        val mime = when {
            low.endsWith(".m3u8") -> "application/x-mpegURL"
            low.endsWith(".mp4") -> "video/mp4"
            low.endsWith(".mkv") -> "video/x-matroska"
            low.endsWith(".ts") -> "video/mp2t"
            low.endsWith(".avi") -> "video/x-msvideo"
            else -> if (live) "application/x-mpegURL" else "video/mp4"
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(if (live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mime)
            .setMetadata(md)
            .build()
        return MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .setCurrentTime(startMs)
            .build()
    }

    fun seekBy(client: RemoteMediaClient?, deltaMs: Long) {
        val c = client ?: return
        val pos = (c.approximateStreamPosition + deltaMs).coerceAtLeast(0L)
        c.seek(MediaSeekOptions.Builder().setPosition(pos).build())
    }
}

/** Lists the Cast devices found on the Wi-Fi network; tapping one starts casting. */
@Composable
fun CastDialog(castCtx: CastContext, activeName: String?, onStop: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val router = remember { MediaRouter.getInstance(ctx) }
    val selector = remember {
        castCtx.mergedSelector ?: MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            ).build()
    }
    var routes by remember { mutableStateOf(listOf<MediaRouter.RouteInfo>()) }

    DisposableEffect(Unit) {
        fun refresh() {
            routes = router.routes.filter { !it.isDefaultOrBluetooth && it.matchesSelector(selector) }
        }
        val cb = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) { refresh() }
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) { refresh() }
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) { refresh() }
        }
        router.addCallback(selector, cb, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refresh()
        onDispose { router.removeCallback(cb) }
    }

    SDialog("Cast to", onDismiss) {
        if (activeName != null) {
            Text("Casting to $activeName", color = Color(0xFF6EE7A0), fontSize = 14.sp)
            SOpt("⏹  Stop casting", false) { onStop(); onDismiss() }
        }
        if (routes.isEmpty()) {
            Text("Searching for devices on your Wi-Fi...", color = C.Text, fontSize = 13.sp)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        routes.forEach { r ->
            SOpt("📺  " + r.name, activeName != null && r.name == activeName) {
                router.selectRoute(r)
                onDismiss()
            }
        }
    }
}

@Composable
private fun CastBtn(label: String, onClick: () -> Unit) {
    Text(
        label, color = Color.White, fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x99000000))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/** Small remote control shown over the player while the video plays on the TV. */
@Composable
fun CastBar(name: String, client: RemoteMediaClient?, seekable: Boolean, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xCC000000)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("📡  Casting to $name", color = Color.White, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (seekable) CastBtn("⏪ 10") { CastHelper.seekBy(client, -10_000L) }
            CastBtn("⏯") { client?.togglePlayback() }
            if (seekable) CastBtn("10 ⏩") { CastHelper.seekBy(client, 10_000L) }
            CastBtn("⏹ Stop") { onStop() }
        }
    }
}
