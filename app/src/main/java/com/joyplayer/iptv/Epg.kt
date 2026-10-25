package com.joyplayer.iptv

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class EpgItem(val title: String, val desc: String, val start: Long, val end: Long, val startStr: String = "")

/** Program guide (EPG) for Xtream live channels, read from the server's get_short_epg. */
object Epg {
    private val http = OkHttpClient.Builder().dns(SmartDns)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<EpgItem>>>()
    private val rx = Regex("""^(https?://[^/]+/)(?:live/)?([^/]+)/([^/]+)/(\d+)(?:\.\w+)?(?:\?.*)?$""")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun JsonObject.str(k: String): String =
        runCatching { get(k).asString }.getOrDefault("")

    private fun dec(s: String): String {
        if (s.isBlank()) return ""
        return try {
            val t = String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
            val bad = t.any { it == '�' || (it.isISOControl() && it != '\n' && it != '\r' && it != '\t') }
            if (bad) s.trim() else t.trim()
        } catch (e: Exception) {
            s.trim()
        }
    }

    /** Whole guide of one channel: past programs (catch-up window) and the coming days. */
    suspend fun loadFull(url: String): List<EpgItem> = withContext(Dispatchers.IO) {
        val m = rx.find(url) ?: return@withContext emptyList()
        val (base, user, pass, id) = m.destructured
        val key = "full$base$id"
        val now = System.currentTimeMillis()
        cache[key]?.let { (t, list) -> if (now - t < 5 * 60_000L) return@withContext list }
        try {
            val req = Request.Builder().url(
                "${base}player_api.php?username=${enc(user)}&password=${enc(pass)}" +
                        "&action=get_simple_data_table&stream_id=$id"
            ).header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()
            http.newBuilder().readTimeout(20, TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                val arr = JsonParser.parseString(r.body?.string().orEmpty()).asJsonObject.getAsJsonArray("epg_listings")
                    ?: return@withContext emptyList()
                val list = arr.mapNotNull { e ->
                    val o = e.asJsonObject
                    val st = o.str("start_timestamp").toLongOrNull() ?: return@mapNotNull null
                    val en = o.str("stop_timestamp").toLongOrNull() ?: return@mapNotNull null
                    EpgItem(dec(o.str("title")), dec(o.str("description")), st, en, o.str("start"))
                }.sortedBy { it.start }
                if (list.isNotEmpty()) cache[key] = now to list
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Catch-up (timeshift) link of a past program, in the usual Xtream form. Uses the server's own clock text for the start. */
    fun catchupUrl(liveUrl: String, p: EpgItem): String? {
        val m = rx.find(liveUrl) ?: return null
        val (base, user, pass, id) = m.destructured
        val minutes = ((p.end - p.start) / 60L).coerceAtLeast(1L) + 1L
        val st = if (p.startStr.length >= 16) {
            p.startStr.substring(0, 16).replace(' ', ':').let { it.substring(0, 13) + "-" + it.substring(14) }
        } else {
            SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(Date(p.start * 1000))
        }
        return "${base}timeshift/$user/$pass/$minutes/$st/$id.ts"
    }

    suspend fun load(url: String): List<EpgItem> = withContext(Dispatchers.IO) {
        val m = rx.find(url) ?: return@withContext emptyList()
        val (base, user, pass, id) = m.destructured
        val key = "$base$id"
        val now = System.currentTimeMillis()
        cache[key]?.let { (t, list) -> if (now - t < 5 * 60_000L) return@withContext list }
        try {
            val req = Request.Builder().url(
                "${base}player_api.php?username=${enc(user)}&password=${enc(pass)}" +
                        "&action=get_short_epg&stream_id=$id&limit=12"
            ).header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                val body = r.body?.string().orEmpty()
                val arr = JsonParser.parseString(body).asJsonObject.getAsJsonArray("epg_listings")
                    ?: return@withContext emptyList()
                val list = arr.mapNotNull { e ->
                    val o = e.asJsonObject
                    val st = o.str("start_timestamp").toLongOrNull() ?: return@mapNotNull null
                    val en = o.str("stop_timestamp").toLongOrNull() ?: return@mapNotNull null
                    EpgItem(dec(o.str("title")), dec(o.str("description")), st, en)
                }.sortedBy { it.start }
                if (list.isNotEmpty()) cache[key] = now to list
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Composable
fun rememberEpg(ch: Channel): List<EpgItem>? =
    produceState<List<EpgItem>?>(null, ch.url, ch.tvgId) {
        value = null
        val x = Epg.load(ch.url)                       // Xtream servers
        value = if (x.isNotEmpty()) x else XmlTv.forChannel(ch)   // plain M3U playlists with an XMLTV guide
    }.value

@Composable
fun rememberEpgNow(): Long =
    produceState(System.currentTimeMillis() / 1000) {
        while (true) {
            delay(20_000)
            value = System.currentTimeMillis() / 1000
        }
    }.value

private fun fmtT(sec: Long): String =
    SimpleDateFormat(if (AppPrefs.clock24) "HH:mm" else "hh:mm a", Locale.getDefault()).format(Date(sec * 1000))

/** Now / Next block shown under the channel name in the Live preview pane. */
@Composable
fun EpgInfo(ch: Channel?, onPlay: (Channel) -> Unit = {}) {
    if (ch == null) return
    val items = rememberEpg(ch)
    val now = rememberEpgNow()
    var guide by remember { mutableStateOf(false) }
    val cur = items?.firstOrNull { it.start <= now && now < it.end }
    val nxt = items?.firstOrNull { it.start > now }
    val dim = C.Text.copy(alpha = 0.65f)

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        when {
            items == null -> Text("Loading guide...", color = dim, fontSize = 12.sp)
            cur == null && nxt == null -> Text("No program info", color = dim, fontSize = 12.sp)
            else -> {
                if (cur != null) {
                    Text(
                        "Now: ${cur.title}", color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text("${fmtT(cur.start)} - ${fmtT(cur.end)}", color = C.Text, fontSize = 11.sp)
                    LinearProgressIndicator(
                        progress = { ((now - cur.start).toFloat() / (cur.end - cur.start).coerceAtLeast(1L)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = C.Blue
                    )
                }
                if (nxt != null) {
                    Text(
                        "Next: ${fmtT(nxt.start)}  ${nxt.title}", color = C.Text, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (!items.isNullOrEmpty() || ch.archiveDays > 0) {
            Text(
                "📅  Program Guide" + if (ch.archiveDays > 0) "  ⏪" else "", color = C.Blue, fontSize = 13.sp,
                modifier = Modifier.clickable { guide = true }.padding(vertical = 6.dp)
            )
        }
    }

    if (guide) ProgramGuideDialog(ch, items.orEmpty(), onPlay) { guide = false }
}

/** Small Now / Next label over the fullscreen live player. */
@Composable
fun EpgBadge(ch: Channel, modifier: Modifier = Modifier) {
    val items = rememberEpg(ch)
    val now = rememberEpgNow()
    val cur = items?.firstOrNull { it.start <= now && now < it.end } ?: return
    val nxt = items?.firstOrNull { it.start > now }
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x99000000))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            "${cur.title}  (${fmtT(cur.start)} - ${fmtT(cur.end)})", color = Color.White, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (nxt != null) {
            Text(
                "Next: ${fmtT(nxt.start)}  ${nxt.title}", color = C.Text, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
