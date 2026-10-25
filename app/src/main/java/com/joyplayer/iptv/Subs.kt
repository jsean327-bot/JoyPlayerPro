package com.joyplayer.iptv

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class SubHit(val name: String, val author: String, val url: String)

class SubSettings(context: Context) {
    private val p = context.getSharedPreferences("joyplayer_subs", Context.MODE_PRIVATE)
    var key: String
        get() = p.getString("subdl_key", "") ?: ""
        set(v) { p.edit().putString("subdl_key", v.trim()).apply() }
    var auto: Boolean
        get() = p.getBoolean("auto", true)
        set(v) { p.edit().putBoolean("auto", v).apply() }
    var lang: String
        get() = p.getString("lang", "AR") ?: "AR"
        set(v) { p.edit().putString("lang", v).apply() }
}

object SubDl {
    private val client = OkHttpClient.Builder().dns(SmartDns)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    fun search(key: String, m: SubMeta, lang: String): List<SubHit> {
        val b = "https://api.subdl.com/api/v1/subtitles".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", key)
            .addQueryParameter("film_name", m.title)
            .addQueryParameter("languages", lang)
            .addQueryParameter("subs_per_page", "30")
        if (m.season != null) {
            b.addQueryParameter("type", "tv")
            b.addQueryParameter("season_number", m.season.toString())
            m.episode?.let { b.addQueryParameter("episode_number", it.toString()) }
        } else {
            b.addQueryParameter("type", "movie")
            m.year?.let { b.addQueryParameter("year", it) }
        }
        val req = Request.Builder().url(b.build()).header("User-Agent", "JoyPlayer/1.0").build()
        client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                error(
                    when (r.code) {
                        401, 403 -> "Invalid SubDL API key"
                        429 -> "SubDL daily limit reached"
                        else -> "SubDL error (HTTP ${r.code})"
                    }
                )
            }
            val j = JSONObject(text)
            if (!j.optBoolean("status", true)) error(j.optString("error", "No subtitles found"))
            val arr = j.optJSONArray("subtitles") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val url = o.optString("url").ifBlank { o.optString("download_link") }
                if (url.isBlank()) null
                else SubHit(
                    o.optString("release_name").ifBlank { o.optString("name") }.ifBlank { "Subtitle ${i + 1}" },
                    o.optString("author"),
                    url
                )
            }
        }
    }

    fun fetch(url: String, m: SubMeta, dir: File): Pair<File, String> {
        val full = if (url.startsWith("http")) url
        else "https://dl.subdl.com" + (if (url.startsWith("/")) url else "/$url")
        val bytes = client.newCall(
            Request.Builder().url(full).header("User-Agent", "JoyPlayer/1.0").build()
        ).execute().use { r ->
            if (!r.isSuccessful) error("Subtitle download failed (HTTP ${r.code})")
            r.body?.bytes() ?: error("Empty file")
        }
        val entries = mutableListOf<Pair<String, ByteArray>>()
        if (bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val low = e.name.lowercase()
                    if (!e.isDirectory && (low.endsWith(".srt") || low.endsWith(".vtt") ||
                                low.endsWith(".ass") || low.endsWith(".ssa"))
                    ) {
                        entries += e.name to z.readBytes()
                    }
                    e = z.nextEntry
                }
            }
        } else {
            entries += "sub.srt" to bytes
        }
        if (entries.isEmpty()) error("No subtitle file inside the archive")
        val pick = pickEntry(entries, m)
        val ext = pick.first.substringAfterLast('.', "srt").lowercase()
        val file = File(dir, "sub_${System.currentTimeMillis()}.$ext")
        file.writeText(decode(pick.second), Charsets.UTF_8)
        return file to ext
    }

    private fun pickEntry(list: List<Pair<String, ByteArray>>, m: SubMeta): Pair<String, ByteArray> {
        val s = m.season
        val e = m.episode
        if (s != null && e != null) {
            val rx = Regex("(?i)(s0*$s[ ._-]*e0*$e(?!\\d)|\\b0*${s}x0*$e(?!\\d))")
            list.firstOrNull { rx.containsMatchIn(it.first) }?.let { return it }
        }
        return list.firstOrNull { it.first.lowercase().endsWith(".srt") } ?: list.first()
    }

    private fun decode(b: ByteArray): String {
        val s = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (e: CharacterCodingException) {
            String(b, Charset.forName("windows-1256"))
        }
        return s.removePrefix("\uFEFF")
    }
}

/** Moves every subtitle time forward (+) or back (-) by a fixed amount (SRT, VTT and ASS/SSA text). */
object SubShift {
    private val ts = Regex("""(?:(\d{1,2}):)?(\d{2}):(\d{2})[.,](\d{3})""")
    private val assTs = Regex("""(\d+):(\d{2}):(\d{2})\.(\d{2})""")

    private fun fmt(ms: Long, sep: Char): String {
        val t = if (ms < 0L) 0L else ms
        val h = t / 3_600_000L
        val m = (t / 60_000L) % 60L
        val s = (t / 1000L) % 60L
        val x = t % 1000L
        return String.format(java.util.Locale.US, "%02d:%02d:%02d%c%03d", h, m, s, sep, x)
    }

    private fun shiftAss(field: String, off: Long): String = assTs.replace(field) { m ->
        val ms = ((m.groupValues[1].toLong() * 60L + m.groupValues[2].toLong()) * 60L +
                m.groupValues[3].toLong()) * 1000L + m.groupValues[4].toLong() * 10L
        val t = (ms + off).coerceAtLeast(0L)
        String.format(
            java.util.Locale.US, "%d:%02d:%02d.%02d",
            t / 3_600_000L, (t / 60_000L) % 60L, (t / 1000L) % 60L, (t % 1000L) / 10L
        )
    }

    fun shift(text: String, ext: String, offsetMs: Long): String {
        if (offsetMs == 0L) return text
        val e = ext.lowercase()
        if (e == "ass" || e == "ssa") {
            return text.lines().joinToString("\n") { line ->
                if (!line.startsWith("Dialogue:")) {
                    line
                } else {
                    val parts = line.split(",", limit = 10)
                    if (parts.size < 10) {
                        line
                    } else {
                        val p = parts.toMutableList()
                        p[1] = shiftAss(p[1], offsetMs)
                        p[2] = shiftAss(p[2], offsetMs)
                        p.joinToString(",")
                    }
                }
            }
        }
        val sep = if (e == "vtt") '.' else ','
        return text.lines().joinToString("\n") { line ->
            if (!line.contains("-->")) {
                line
            } else {
                ts.replace(line) { m ->
                    val h = m.groupValues[1].ifEmpty { "0" }.toLong()
                    val ms = ((h * 60L + m.groupValues[2].toLong()) * 60L + m.groupValues[3].toLong()) * 1000L +
                            m.groupValues[4].toLong()
                    fmt(ms + offsetMs, sep)
                }
            }
        }
    }
}
