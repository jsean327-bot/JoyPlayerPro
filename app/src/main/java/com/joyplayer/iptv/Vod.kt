package com.joyplayer.iptv

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
    private val cache = mutableMapOf<String, List<LibItem>>()

    private fun base(server: String): String {
        var b = server.trim()
        if (!b.startsWith("http")) b = "http://$b"
        if (!b.endsWith("/")) b += "/"
        return b
    }

    private fun api(b: String): VodService =
        Retrofit.Builder().baseUrl(b).addConverterFactory(GsonConverterFactory.create())
            .build().create(VodService::class.java)

    fun invalidate(listId: String) {
        cache.keys.removeAll { it.startsWith(listId + "_") }
    }

    suspend fun load(l: SavedList, series: Boolean): List<LibItem> {
        val key = "${l.id}_$series"
        cache[key]?.let { return it }
        ContentCache.vod(l, series)?.let { cache[key] = it; return it }
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

    private suspend fun fetchRemote(l: SavedList, series: Boolean): List<LibItem> {
        val b = base(l.server)
        val a = api(b)
        val cats = if (series) a.seriesCats(l.user, l.pass) else a.movieCats(l.user, l.pass)
        val names = cats.associate { it.id to (it.name ?: "Other") }
        val items = if (series) {
            a.series(l.user, l.pass).mapNotNull { s ->
                val id = s.id ?: return@mapNotNull null
                LibItem(
                    id, s.name ?: "Series $id", s.cover?.ifBlank { null },
                    s.rating?.toDoubleOrNull() ?: 0.0, s.added?.toLongOrNull() ?: 0L,
                    names[s.cat] ?: "Other", ""
                )
            }
        } else {
            a.movies(l.user, l.pass).mapNotNull { m ->
                val id = m.id ?: return@mapNotNull null
                LibItem(
                    id, m.name ?: "Movie $id", m.icon?.ifBlank { null },
                    m.rating?.toDoubleOrNull() ?: 0.0, m.added?.toLongOrNull() ?: 0L,
                    names[m.cat] ?: "Other",
                    "${b}movie/${l.user}/${l.pass}/$id.${m.ext ?: "mp4"}"
                )
            }
        }
        return items
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
