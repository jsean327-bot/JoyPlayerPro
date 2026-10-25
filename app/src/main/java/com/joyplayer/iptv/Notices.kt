package com.joyplayer.iptv

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Small info lines on the Home screen (not focusable, so the remote is never affected):
 *  - subscription expires soon / expired
 *  - a new app version is available (checked at most once a day)
 */
object Notices {
    private val expiryCache = HashMap<String, Long?>()

    suspend fun expiryText(l: SavedList?): Pair<String, Boolean>? {
        if (l == null || l.server.isBlank()) return null
        val expOrNull: Long? = if (expiryCache.containsKey(l.id)) expiryCache[l.id] else {
            val v = try {
                withContext(Dispatchers.IO) { AccountApi.load(l).expiry }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
            expiryCache[l.id] = v
            v
        }
        val exp: Long = expOrNull ?: return null
        val days = (exp - System.currentTimeMillis() / 1000) / 86400
        val ar = Lang.code == "ar"
        return when {
            days < 0 -> (if (ar) "⚠ انتهى الاشتراك" else "⚠ Subscription expired") to true
            days == 0L -> (if (ar) "⚠ ينتهي الاشتراك اليوم" else "⚠ Subscription ends today") to true
            days <= 14 -> (if (ar) "⚠ ينتهي الاشتراك بعد $days يوم" else "⚠ Subscription ends in $days days") to (days <= 3)
            else -> null
        }
    }

    suspend fun updateText(ctx: Context): String? {
        val p = ctx.applicationContext.getSharedPreferences("joyplayer_app", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cur = Updater.installedBuild(ctx)
        var newest = p.getInt("upd_newest", 0)
        if (now - p.getLong("upd_checked", 0L) > 20L * 3600_000L) {
            try {
                newest = withContext(Dispatchers.IO) { Updater.latest().build }
                p.edit().putLong("upd_checked", now).putInt("upd_newest", newest).apply()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // offline or GitHub limit: try again next launch
            }
        }
        if (newest <= cur) return null
        return if (Lang.code == "ar") "⬆ يوجد تحديث جديد — الإعدادات ← Update Now"
        else "⬆ New version available — Settings → Update Now"
    }
}

@Composable
fun HomeNotices(list: SavedList?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var expiry by remember(list?.id) { mutableStateOf<Pair<String, Boolean>?>(null) }
    var update by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(list?.id) { expiry = Notices.expiryText(list) }
    LaunchedEffect(Unit) { update = Notices.updateText(ctx) }
    Column(modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        expiry?.let { (t, red) -> NoticePill(t, if (red) Color(0xCCB3261E) else Color(0xCC8A5A00)) }
        update?.let { NoticePill(it, Color(0xCC1B5E20)) }
    }
}

@Composable
private fun NoticePill(text: String, bg: Color) {
    Text(
        text, color = Color.White, fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/** Connection test: writes a plain-text report that can be copied and sent to the provider. */
object Diag {
    private fun mask(s: String, l: SavedList): String {
        var r = s
        if (l.user.isNotBlank()) r = r.replace(l.user, "***")
        if (l.pass.isNotBlank()) r = r.replace(l.pass, "***")
        return r
    }

    private fun ms(t0: Long) = "${(System.nanoTime() - t0) / 1_000_000} ms"

    suspend fun run(ctx: Context, l: SavedList, step: (String) -> Unit): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        fun line(s: String) { sb.appendLine(s); step(sb.toString()) }
        line("JoyPlayer IPTV Pro — connection test")
        line("App build ${Updater.installedBuild(ctx)} | ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE}")
        line("Server: ${mask(l.server, l)}")
        line("")
        val quick = Http.client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()

        // 1) plain internet
        var t0 = System.nanoTime()
        try {
            quick.newCall(okhttp3.Request.Builder().url("https://www.gstatic.com/generate_204").build()).execute().use {
                line("✓ Internet: OK (${ms(t0)})")
            }
        } catch (e: Exception) { line("✗ Internet: ${e.javaClass.simpleName} ${mask(e.message ?: "", l)}") }

        var base = l.server.trim()
        if (base.isEmpty()) { line("✗ This playlist is not an Xtream login"); return@withContext sb.toString() }
        if (!base.startsWith("http")) base = "http://$base"
        if (!base.endsWith("/")) base += "/"
        val q = "username=${URLEncoder.encode(l.user, "UTF-8")}&password=${URLEncoder.encode(l.pass, "UTF-8")}"
        fun get(extra: String): String {
            val req = okhttp3.Request.Builder().url("${base}player_api.php?$q$extra")
                .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()
            return quick.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
        }

        // 2) login
        t0 = System.nanoTime()
        try {
            val root = JsonParser.parseString(get("")).asJsonObject
            val u = root.getAsJsonObject("user_info")
            fun s(k: String) = runCatching { u.get(k).asString }.getOrDefault("")
            line("✓ Login: ${s("status").ifBlank { "?" }} (${ms(t0)})")
            val exp = s("exp_date").toLongOrNull()
            if (exp != null && exp > 0) {
                val d = (exp - System.currentTimeMillis() / 1000) / 86400
                line("  Expires in $d days")
            }
            line("  Connections: ${s("active_cons").ifBlank { "0" }} / ${s("max_connections").ifBlank { "?" }}")
        } catch (e: Exception) { line("✗ Login: ${mask(e.message ?: e.javaClass.simpleName, l)} (${ms(t0)})") ; return@withContext sb.toString() }

        // 3) categories + a first channel
        var streamUrl: String? = null
        var catIds = listOf<String>()
        var firstId: String? = null
        t0 = System.nanoTime()
        try {
            val cats = JsonParser.parseString(get("&action=get_live_categories")).asJsonArray
            line("✓ Live categories: ${cats.size()} (${ms(t0)})")
            catIds = cats.mapNotNull { runCatching { it.asJsonObject.get("category_id").asString }.getOrNull() }
            val cid = catIds.firstOrNull()
            if (cid != null) {
                t0 = System.nanoTime()
                val arr = JsonParser.parseString(get("&action=get_live_streams&category_id=$cid")).asJsonArray
                line("✓ Channels in first category: ${arr.size()} (${ms(t0)})")
                val id = arr.firstOrNull()?.asJsonObject?.get("stream_id")?.asString
                if (id != null) { streamUrl = "${base}live/${l.user}/${l.pass}/$id.ts"; firstId = id }
            }
        } catch (e: Exception) { line("✗ Channels: ${mask(e.message ?: e.javaClass.simpleName, l)}") }

        // 3b) program guide + catch-up probe
        try {
            line("")
            var epgId: String? = firstId
            var archId: String? = null
            for (cid in catIds.take(8)) {
                val arr = JsonParser.parseString(get("&action=get_live_streams&category_id=$cid")).asJsonArray
                val hit = arr.firstOrNull { e ->
                    val o = e.asJsonObject
                    runCatching { o.get("tv_archive").asString == "1" }.getOrDefault(false)
                }
                if (hit != null) { archId = hit.asJsonObject.get("stream_id").asString; break }
            }
            if (archId == null) line("• Catch-up: no channel with tv_archive=1 in the first 8 categories (the provider may not offer it)")
            val testId = archId ?: epgId
            if (testId != null) {
                val liveU = "${base}live/${l.user}/${l.pass}/$testId.m3u8"
                t0 = System.nanoTime()
                val short = Epg.load(liveU)
                line((if (short.isNotEmpty()) "✓" else "✗") + " Guide (short): ${short.size} programs (${ms(t0)})" + if (archId != null) "  [archive channel $testId]" else "  [channel $testId]")
                t0 = System.nanoTime()
                val full = Epg.loadFull(liveU)
                line((if (full.isNotEmpty()) "✓" else "✗") + " Guide (full): ${full.size} programs (${ms(t0)})")
                val nowS = System.currentTimeMillis() / 1000
                full.firstOrNull { it.start <= nowS && nowS < it.end }?.let { line("  now: ${it.title.take(40)} | server time text: '${it.startStr}'") }
                val past = full.lastOrNull { it.end <= nowS }
                if (archId != null && past != null) {
                    val u1 = Epg.catchupUrl(liveU, past)
                    val variants = listOfNotNull(
                        u1,
                        u1?.removeSuffix(".ts")?.plus(".m3u8"),
                        "${base}streaming/timeshift.php?username=${URLEncoder.encode(l.user, "UTF-8")}&password=${URLEncoder.encode(l.pass, "UTF-8")}" +
                            "&stream=$archId&start=" + (u1?.substringBeforeLast('/')?.substringAfterLast('/') ?: "") +
                            "&duration=" + (u1?.substringBeforeLast('/')?.substringBeforeLast('/')?.substringAfterLast('/') ?: "60")
                    )
                    for (u in variants) {
                        t0 = System.nanoTime()
                        try {
                            val c = Http.client.newBuilder().readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
                            c.newCall(okhttp3.Request.Builder().url(u).header("Range", "bytes=0-4095").header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()).execute().use { r ->
                                val n = r.body?.source()?.let { src -> runCatching { src.request(2048); src.buffer.size }.getOrDefault(0L) } ?: 0L
                                line((if (r.isSuccessful && n > 0) "✓" else "✗") + " Catch-up HTTP ${r.code}, ${r.header("Content-Type") ?: "?"}, ${n} bytes (${ms(t0)})")
                                line("   " + mask(u, l).replace(Regex("^https?://[^/]+"), "<server>"))
                            }
                        } catch (e: Exception) { line("✗ Catch-up: ${mask(e.message ?: e.javaClass.simpleName, l)}") }
                    }
                } else if (archId != null) line("• Catch-up: no past program in the guide to test")
            }
        } catch (e: Exception) { line("✗ Guide test: ${mask(e.message ?: e.javaClass.simpleName, l)}") }

        // 4) real stream speed (reads up to ~6 seconds)
        if (streamUrl != null) {
            line("… Testing a live stream (6 seconds)")
            t0 = System.nanoTime()
            try {
                val c = Http.client.newBuilder().readTimeout(8, TimeUnit.SECONDS).callTimeout(14, TimeUnit.SECONDS).build()
                c.newCall(okhttp3.Request.Builder().url(streamUrl).header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    val first = ms(t0)
                    val body = r.body ?: error("empty")
                    val buf = ByteArray(32 * 1024)
                    var total = 0L
                    val t1 = System.nanoTime()
                    body.byteStream().use { inp ->
                        while ((System.nanoTime() - t1) < 6_000_000_000L) {
                            val n = inp.read(buf); if (n < 0) break
                            total += n
                        }
                    }
                    val secs = ((System.nanoTime() - t1) / 1e9).coerceAtLeast(0.1)
                    val mbps = total * 8 / secs / 1_000_000
                    line("✓ Stream: first data after $first, speed %.1f Mbit/s".format(mbps))
                    line(
                        when {
                            mbps >= 8 -> "Result: connection is good (HD/FHD OK)"
                            mbps >= 4 -> "Result: OK for HD, weak for FHD"
                            else -> "Result: SLOW — buffering is likely (check Wi-Fi/provider)"
                        }
                    )
                }
            } catch (e: Exception) { line("✗ Stream: ${mask(e.message ?: e.javaClass.simpleName, l)} (${ms(t0)})") }
        }
        sb.toString()
    }
}
