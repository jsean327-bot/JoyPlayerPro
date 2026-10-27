package com.joyplayer.iptv

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SmartList {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")
    private val client get() = Http.client

    // get.php?username=..&password=.. links are Xtream accounts in disguise
    fun normalize(l: SavedList): SavedList {
        if (l.type != "m3u") return l
        val u = try { Uri.parse(l.url.trim()) } catch (e: Exception) { return l }
        val user = u.getQueryParameter("username")
        val pass = u.getQueryParameter("password")
        val host = u.host
        if (host == null || user.isNullOrBlank() || pass.isNullOrBlank()) return l
        if (u.path?.contains("get.php") != true) return l
        val port = if (u.port != -1) ":${u.port}" else ""
        return l.copy(
            type = "xtream",
            // "https" on a plain port (e.g. :80 / :8080) is a typo that ends in "Unable to parse TLS packet header"
            server = "${if (u.scheme == "https" && u.port != -1 && u.port != 443 && u.port != 8443) "http" else (u.scheme ?: "http")}://$host$port",
            user = user,
            pass = pass
        )
    }

    /** Program-guide link found in the header of the last M3U that was downloaded (url-tvg="..."). */
    @Volatile var lastEpg: String = ""

    fun fetchM3u(url: String): List<Channel> {
        lastEpg = ""
        if (url.trim().startsWith("file:")) {            // a file picked on this device
            val f = java.io.File(url.trim().removePrefix("file://").removePrefix("file:"))
            if (!f.exists()) error("The playlist file is missing. Add it again from Change Playlist.")
            val out = f.bufferedReader(Charsets.UTF_8).useLines { parseLines(it) }
            if (out.isEmpty()) error("No channels found in this file")
            return out
        }
        val req = Request.Builder().url(url.trim())
            .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Server answered HTTP ${r.code}")
            val body = r.body ?: error("Empty response")
            val out = body.charStream().useLines { parseLines(it) }
            if (out.isEmpty()) error("No channels found in this link")
            return out
        }
    }

    private fun parseLines(lines: Sequence<String>): List<Channel> {
        val out = mutableListOf<Channel>()
        var name = ""
        var logo: String? = null
        var tvgId: String? = null
        var group = "Other"
        for (raw in lines) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTM3U") -> {
                    val a = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val u = a["url-tvg"] ?: a["x-tvg-url"] ?: a["tvg-url"] ?: ""
                    lastEpg = u.split(",").firstOrNull()?.trim().orEmpty()
                }
                line.startsWith("#EXTINF") -> {
                    val a = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    tvgId = a["tvg-id"]?.ifBlank { null }
                    logo = a["tvg-logo"]?.ifBlank { null }
                    group = a["group-title"]?.ifBlank { null } ?: "Other"
                    name = line.substringAfterLast(",").trim().ifBlank { a["tvg-name"] ?: "Channel" }
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    out += Channel(name.ifBlank { "Channel" }, line, logo, group, tvgId)
                    name = ""; logo = null; tvgId = null; group = "Other"
                }
            }
        }
        return out
    }
}
