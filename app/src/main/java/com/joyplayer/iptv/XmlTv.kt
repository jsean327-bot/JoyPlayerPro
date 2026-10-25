package com.joyplayer.iptv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Program guide (EPG) for plain M3U playlists, read from an XMLTV file
 * (the link comes from the playlist header url-tvg="..." or from Settings > EPG).
 */
object XmlTv {
    @Volatile var activeUrl: String = ""

    private var cacheDir: File? = null
    private val mutex = Mutex()
    private var loadedUrl = ""
    private var programs: Map<String, List<EpgItem>> = emptyMap()   // channel id (lowercase) -> programs
    private var names: Map<String, String> = emptyMap()             // simplified channel name -> channel id

    private val http = OkHttpClient.Builder().dns(SmartDns)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun init(ctx: Context) {
        if (cacheDir == null) cacheDir = File(ctx.applicationContext.cacheDir, "xmltv").apply { mkdirs() }
    }

    fun reset() {
        loadedUrl = ""
        programs = emptyMap()
        names = emptyMap()
        cacheDir?.listFiles()?.forEach { it.delete() }
    }

    private fun simple(s: String): String = normSearch(s).filter { it.isLetterOrDigit() }

    suspend fun forChannel(ch: Channel): List<EpgItem> {
        val url = activeUrl
        if (url.isBlank()) return emptyList()
        mutex.withLock {
            if (loadedUrl != url) {
                withContext(Dispatchers.IO) { load(url) }
            }
        }
        val id = ch.tvgId?.lowercase()?.takeIf { it.isNotBlank() && programs.containsKey(it) }
            ?: names[simple(ch.name)]?.lowercase()
        return programs[id ?: return emptyList()].orEmpty()
    }

    private fun load(url: String) {
        loadedUrl = url
        programs = emptyMap()
        names = emptyMap()
        val dir = cacheDir ?: return
        val f = File(dir, "guide_${Integer.toHexString(url.hashCode())}.xml")
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 6L * 3600_000L
        if (!fresh) {
            try {
                download(url, f)
            } catch (e: Exception) {
                if (!f.exists()) return
            }
        }
        try {
            f.inputStream().buffered().use { parse(it) }
        } catch (e: Exception) {
            // unreadable guide: keep whatever was parsed
        }
    }

    private fun download(url: String, out: File) {
        val req = Request.Builder().url(url.trim()).header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            val body = r.body ?: throw IllegalStateException("Empty guide")
            val tmp = File(out.parentFile, out.name + ".tmp")
            val pb = PushbackInputStream(body.byteStream(), 2)
            val b0 = pb.read()
            val b1 = pb.read()
            if (b0 >= 0 && b1 >= 0) {
                pb.unread(b1)
                pb.unread(b0)
            }
            val gz = b0 == 0x1f && b1 == 0x8b
            val src: InputStream = if (gz) GZIPInputStream(pb) else pb
            tmp.outputStream().buffered().use { o -> src.copyTo(o) }
            out.delete()
            tmp.renameTo(out)
        }
    }

    // XMLTV time "20261022183000 +0200" -> epoch seconds
    private fun parseTime(s: String?): Long {
        if (s == null || s.length < 12) return 0L
        return try {
            val y = s.substring(0, 4).toInt()
            val mo = s.substring(4, 6).toInt()
            val d = s.substring(6, 8).toInt()
            val h = s.substring(8, 10).toInt()
            val mi = s.substring(10, 12).toInt()
            val sec = if (s.length >= 14 && s[12].isDigit()) s.substring(12, 14).toInt() else 0
            var off = 0L
            val t = s.trim()
            val sp = t.lastIndexOfAny(charArrayOf('+', '-'))
            if (sp > 10) {
                val sign = if (t[sp] == '-') -1L else 1L
                val digits = t.substring(sp + 1).filter { it.isDigit() }
                if (digits.length >= 4) off = sign * (digits.substring(0, 2).toLong() * 3600L + digits.substring(2, 4).toLong() * 60L)
            }
            daysFromCivil(y, mo, d) * 86400L + h * 3600L + mi * 60L + sec - off
        } catch (e: Exception) {
            0L
        }
    }

    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146097L + doe - 719468L
    }

    private fun parse(input: InputStream) {
        val now = System.currentTimeMillis() / 1000L
        val winStart = now - 2L * 3600L
        val winEnd = now + 30L * 3600L
        val p = android.util.Xml.newPullParser()
        p.setInput(input, null)
        val nameMap = HashMap<String, String>()
        val progs = HashMap<String, MutableList<EpgItem>>()
        var curChannel: String? = null
        var field = ""
        var pCh: String? = null
        var pStart = 0L
        var pStop = 0L
        var title = ""
        var desc = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "channel" -> curChannel = p.getAttributeValue(null, "id")
                    "display-name" -> field = "dn"
                    "programme" -> {
                        pCh = p.getAttributeValue(null, "channel")
                        pStart = parseTime(p.getAttributeValue(null, "start"))
                        pStop = parseTime(p.getAttributeValue(null, "stop"))
                        title = ""
                        desc = ""
                    }
                    "title" -> field = "title"
                    "desc" -> field = "desc"
                }
                XmlPullParser.TEXT -> {
                    val t = p.text ?: ""
                    when (field) {
                        "dn" -> curChannel?.let { id -> nameMap.putIfAbsent(simple(t), id) }
                        "title" -> if (title.isEmpty()) title = t.trim()
                        "desc" -> if (desc.isEmpty()) desc = t.trim().take(300)
                    }
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "channel" -> curChannel = null
                    "display-name", "title", "desc" -> field = ""
                    "programme" -> {
                        val id = pCh
                        if (id != null && pStop > winStart && pStart < winEnd && pStart > 0L) {
                            progs.getOrPut(id.lowercase()) { ArrayList() }.add(EpgItem(title, desc, pStart, pStop))
                        }
                        pCh = null
                    }
                }
            }
            ev = p.next()
        }
        programs = progs.mapValues { e -> e.value.sortedBy { it.start } }
        names = nameMap
    }
}
