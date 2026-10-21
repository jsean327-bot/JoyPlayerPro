package com.joyplayer.iptv

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SmartList {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

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
            server = "${u.scheme ?: "http"}://$host$port",
            user = user,
            pass = pass
        )
    }

    fun fetchM3u(url: String): List<Channel> {
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
        var group = "Other"
        for (raw in lines) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val a = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = a["tvg-logo"]?.ifBlank { null }
                    group = a["group-title"]?.ifBlank { null } ?: "Other"
                    name = line.substringAfterLast(",").trim().ifBlank { a["tvg-name"] ?: "Channel" }
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    out += Channel(name.ifBlank { "Channel" }, line, logo, group)
                    name = ""; logo = null; group = "Other"
                }
            }
        }
        return out
    }
}
