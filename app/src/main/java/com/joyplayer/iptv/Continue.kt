package com.joyplayer.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** "Continue Watching": unfinished movies and episodes, one tap to carry on where you stopped. */
@Composable
fun ContinueRow(onPlay: (Channel) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    ResumeStore.init(ctx)
    val ver = ResumeStore.version
    val items = remember(ver) { ResumeStore.recent(12) }
    if (items.isEmpty()) return

    Column(modifier) {
        Text(
            "Continue Watching", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items) { r ->
                Column(
                    Modifier.width(104.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x55000000))
                        .clickable {
                            PlayMeta.map[r.url] = SubMeta(PlayMeta.cleanTitle(r.name), PlayMeta.year(r.name), null, null)
                            PlayQueue.clear()
                            onPlay(Channel(r.name, r.url, r.logo, ""))
                        }
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Color.Black)) {
                        AsyncImage(r.logo, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
                        LinearProgressIndicator(
                            progress = { (r.pos.toFloat() / r.dur.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                            color = C.Blue, trackColor = Color(0x88000000)
                        )
                    }
                    Text(
                        r.name, color = Color.White, fontSize = 11.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}
