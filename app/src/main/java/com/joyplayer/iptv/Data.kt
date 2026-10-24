package com.joyplayer.iptv

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class Channel(
    val name: String, val url: String, val logo: String?, val group: String,
    val tvgId: String? = null
)

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var name = ""; var logo: String? = null; var group = "Other"
        for (raw in text.lineSequence()) {
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

    fun fetch(url: String): List<Channel> {
        val req = Request.Builder().url(url).header("User-Agent", "JoyPlayer/1.0").build()
        Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return parse(r.body?.string().orEmpty())
        }
    }
}

data class XCategory(@SerializedName("category_id") val id: String?, @SerializedName("category_name") val name: String?)
data class XLive(
    val name: String?,
    @SerializedName("stream_id") val streamId: Int?,
    @SerializedName("stream_icon") val icon: String?,
    @SerializedName("category_id") val categoryId: String?
)

interface XtreamService {
    @GET("player_api.php")
    suspend fun categories(
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") action: String = "get_live_categories"
    ): List<XCategory>

    @GET("player_api.php")
    suspend fun live(
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") action: String = "get_live_streams"
    ): List<XLive>
}

object Xtream {
    suspend fun load(server: String, user: String, pass: String): List<Channel> {
        var base = server.trim()
        if (!base.startsWith("http")) base = "http://$base"
        if (!base.endsWith("/")) base += "/"
        val api = Retrofit.Builder().baseUrl(base).client(Http.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(XtreamService::class.java)
        fun enc(x: String) = java.net.URLEncoder.encode(x, "UTF-8")
        val liveUrl = "${base}player_api.php?username=${enc(user)}&password=${enc(pass)}&action=get_live_streams"
        // categories and channels are asked at the same time; the big channel list is read piece by piece
        val (catList, streams) = coroutineScope {
            val c = async { api.categories(user, pass) }
            val s = async(kotlinx.coroutines.Dispatchers.IO) { readStreams(liveUrl) }
            c.await() to s.await()
        }
        val cats = catList.associate { it.id to (it.name ?: "Other") }
        return streams.map { s ->
            Channel(s.name ?: "Channel ${s.id}", "${base}live/$user/$pass/${s.id}.m3u8",
                s.icon?.ifBlank { null }, cats[s.cat] ?: "Other", s.epg?.ifBlank { null })
        }
    }

    private class Raw(val id: Int, val name: String?, val icon: String?, val cat: String?, val epg: String?)

    private fun com.google.gson.stream.JsonReader.str(): String? = when (peek()) {
        com.google.gson.stream.JsonToken.NULL -> { nextNull(); null }
        com.google.gson.stream.JsonToken.STRING, com.google.gson.stream.JsonToken.NUMBER -> nextString()
        com.google.gson.stream.JsonToken.BOOLEAN -> nextBoolean().toString()
        else -> { skipValue(); null }
    }

    private fun readStreams(url: String): List<Raw> = Vod.streamArray(url) { rd ->
        var id: Int? = null; var name: String? = null; var icon: String? = null
        var cat: String? = null; var epg: String? = null
        rd.beginObject()
        while (rd.hasNext()) {
            when (rd.nextName()) {
                "stream_id" -> id = rd.str()?.toIntOrNull()
                "name" -> name = rd.str()
                "stream_icon" -> icon = rd.str()
                "category_id" -> cat = rd.str()
                "epg_channel_id" -> epg = rd.str()
                else -> rd.skipValue()
            }
        }
        rd.endObject()
        id?.let { Raw(it, name, icon, cat, epg) }
    }
}
