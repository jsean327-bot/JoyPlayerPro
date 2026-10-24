package com.joyplayer.iptv

import com.google.gson.stream.JsonReader
import kotlinx.coroutines.launch
import com.google.gson.stream.JsonToken
import okhttp3.Request
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class VCat(
    @SerializedName("category_id") val id: String?,
    @SerializedName("category_name") val name: String?
)

data class VMovie(
    @SerializedName("stream_id") val id: Int?,
    val name: String?,
    @SerializedName("stream_icon") val icon: String?,
    val rating: String?,
    val added: String?,
    @SerializedName("category_id") val cat: String?,
    @SerializedName("container_extension") val ext: String?
)

data class VSeries(
    @SerializedName("series_id") val id: Int?,
    val name: String?,
    val cover: String?,
    val rating: String?,
    @SerializedName("last_modified") val added: String?,
    @SerializedName("category_id") val cat: String?
)

data class VEpisode(
    val id: String?,
    @SerializedName("episode_num") val num: Int?,
    val title: String?,
    @SerializedName("container_extension") val ext: String?
)

data class VSeriesInfo(val episodes: Map<String, List<VEpisode>>?)

interface VodService {
    @GET("player_api.php")
    suspend fun movieCats(
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") a: String = "get_vod_categories"
    ): List<VCat>

    @GET("player_api.php")
    suspend fun movies(
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") a: String = "get_vod_streams"
    ): List<VMovie>

    @GET("player_api.php")
    suspend fun seriesCats(
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") a: String = "get_series_categories"
    ): List<VCat>

    @GET("player_api.php")
    suspend fun series(
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") a: String = "get_series"
    ): List<VSeries>

    @GET("player_api.php")
    suspend fun seriesInfo(
        @Query("username") u: String, @Query("password") p: String,
        @Query("series_id") sid: Int,
        @Query("action") a: String = "get_series_info"
    ): VSeriesInfo
}

data class LibItem(
    val id: Int, val name: String, val poster: String?, val rating: Double,
    val added: Long, val cat: String, val url: String
)

object Vod {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<LibItem>>()

    private fun base(server: String): String {
        var b = server.trim()
        if (!b.startsWith("http")) b = "http://$b"
        if (!b.endsWith("/")) b += "/"
        return b
    }

    private fun api(b: String): VodService =
        Retrofit.Builder().baseUrl(b).client(Http.client).addConverterFactory(GsonConverterFactory.create())
            .build().create(VodService::class.java)

    fun invalidate(listId: String) {
        cache.keys.removeAll { it.startsWith(listId + "_") }
    }

    private val refreshing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun refreshLater(l: SavedList, series: Boolean, key: String) {
        if (!refreshing.add(key)) return          // once per app session
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(20_000)   // let the screen / player settle first
                val items = fetchRemote(l, series)
                ContentCache.putVod(l, series, items)   // used the next time the app starts
            } catch (t: Throwable) {
                // best-effort
            }
        }
    }

    suspend fun load(l: SavedList, series: Boolean): List<LibItem> {
        val key = "${l.id}_$series"
        cache[key]?.let { return it }
        // Show the saved copy at once (however old); refresh it quietly in the background when it is stale
        ContentCache.vod(l, series, ignoreAge = true)?.let { old ->
            cache[key] = old
            if (!ContentCache.isFresh(if (series) "series" else "movies", l)) refreshLater(l, series, key)
            return old
        }
        return try {
            val items = fetchRemote(l, series)
            cache[key] = items
            ContentCache.putVod(l, series, items)
            items
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val old = ContentCache.vod(l, series, ignoreAge = true) ?: throw e
            cache[key] = old
            old
        }
    }

    private fun JsonReader.str(): String? = when (peek()) {
        JsonToken.NULL -> { nextNull(); null }
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        else -> { skipValue(); null }
    }

    /**
     * Reads the big JSON list straight from the network, item by item, keeping only the fields we show.
     * (Loading the whole thing as objects used up all the memory on TV boxes.)
     */
    private fun <T> streamArray(url: String, item: (JsonReader) -> T?): List<T> {
        val req = Request.Builder().url(url).header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()
        Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val body = r.body ?: error("Empty answer")
            JsonReader(body.charStream().buffered(64 * 1024)).use { rd ->
                rd.isLenient = true
                val out = ArrayList<T>()
                if (rd.peek() != JsonToken.BEGIN_ARRAY) { rd.skipValue(); return out }
                rd.beginArray()
                while (rd.hasNext()) {
                    if (rd.peek() == JsonToken.BEGIN_OBJECT) item(rd)?.let { out.add(it) } else rd.skipValue()
                }
                rd.endArray()
                return out
            }
        }
    }

    private suspend fun fetchRemote(l: SavedList, series: Boolean): List<LibItem> {
        val b = base(l.server)
        val a = api(b)
        val cats = if (series) a.seriesCats(l.user, l.pass) else a.movieCats(l.user, l.pass)
        val names = cats.associate { it.id to (it.name ?: "Other") }
        fun enc(x: String) = java.net.URLEncoder.encode(x, "UTF-8")
        val api = "${b}player_api.php?username=${enc(l.user)}&password=${enc(l.pass)}&action="
        return if (series) {
            streamArray(api + "get_series") { rd ->
                var id: Int? = null; var name: String? = null; var cover: String? = null
                var rating: String? = null; var added: String? = null; var cat: String? = null
                rd.beginObject()
                while (rd.hasNext()) {
                    when (rd.nextName()) {
                        "series_id" -> id = rd.str()?.toIntOrNull()
                        "name" -> name = rd.str()
                        "cover" -> cover = rd.str()
                        "rating" -> rating = rd.str()
                        "last_modified" -> added = rd.str()
                        "category_id" -> cat = rd.str()
                        else -> rd.skipValue()
                    }
                }
                rd.endObject()
                id?.let {
                    LibItem(
                        it, name ?: "Series $it", cover?.ifBlank { null },
                        rating?.toDoubleOrNull() ?: 0.0, added?.toLongOrNull() ?: 0L,
                        names[cat] ?: "Other", ""
                    )
                }
            }
        } else {
            streamArray(api + "get_vod_streams") { rd ->
                var id: Int? = null; var name: String? = null; var icon: String? = null
                var rating: String? = null; var added: String? = null; var cat: String? = null; var ext: String? = null
                rd.beginObject()
                while (rd.hasNext()) {
                    when (rd.nextName()) {
                        "stream_id" -> id = rd.str()?.toIntOrNull()
                        "name" -> name = rd.str()
                        "stream_icon" -> icon = rd.str()
                        "rating" -> rating = rd.str()
                        "added" -> added = rd.str()
                        "category_id" -> cat = rd.str()
                        "container_extension" -> ext = rd.str()
                        else -> rd.skipValue()
                    }
                }
                rd.endObject()
                id?.let {
                    LibItem(
                        it, name ?: "Movie $it", icon?.ifBlank { null },
                        rating?.toDoubleOrNull() ?: 0.0, added?.toLongOrNull() ?: 0L,
                        names[cat] ?: "Other",
                        "${b}movie/${l.user}/${l.pass}/$it.${ext ?: "mp4"}"
                    )
                }
            }
        }
    }

    suspend fun episodes(l: SavedList, seriesId: Int): Map<String, List<Channel>> {
        val b = base(l.server)
        val info = api(b).seriesInfo(l.user, l.pass, seriesId)
        return (info.episodes ?: emptyMap()).entries
            .sortedBy { it.key.toIntOrNull() ?: 0 }
            .associate { (season, eps) ->
                season to eps.sortedBy { it.num ?: 0 }.map { e ->
                    Channel(
                        e.title ?: "Episode ${e.num}",
                        "${b}series/${l.user}/${l.pass}/${e.id}.${e.ext ?: "mp4"}",
                        null, season
                    )
                }
            }
    }
}


/** Warms the Movies / Series lists into memory right after the playlist opens, so those tabs open instantly. */
object Preload {
    private val done = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun vod(l: SavedList) {
        if (l.type == "m3u" || !done.add(l.id)) return
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(1_500)
            try { Vod.load(l, false) } catch (t: Throwable) { }
            try { Vod.load(l, true) } catch (t: Throwable) { }
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun refreshLive(l: SavedList) {
        if (!done.add("live_" + l.id)) return
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(15_000)
                val fresh = if (l.type == "m3u") SmartList.fetchM3u(l.url) else Xtream.load(l.server, l.user, l.pass)
                ContentCache.putChannels(l, fresh)
            } catch (t: Throwable) { }
        }
    }

    fun reset(listId: String) { done.removeAll { it == listId || it == "live_$listId" } }
}
