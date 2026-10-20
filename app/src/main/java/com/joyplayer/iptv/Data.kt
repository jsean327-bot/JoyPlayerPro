package com.joyplayer.iptv

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class Channel(val name: String, val url: String, val logo: String?, val group: String)

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
        OkHttpClient().newCall(req).execute().use { r ->
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
        val api = Retrofit.Builder().baseUrl(base)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(XtreamService::class.java)
        val cats = api.categories(user, pass).associate { it.id to (it.name ?: "Other") }
        return api.live(user, pass).mapNotNull { s ->
            val id = s.streamId ?: return@mapNotNull null
            Channel(s.name ?: "Channel $id", "${base}live/$user/$pass/$id.m3u8",
                s.icon?.ifBlank { null }, cats[s.categoryId] ?: "Other")
        }
    }
}
