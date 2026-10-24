package com.joyplayer.iptv

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class VInfo(
    @SerializedName("movie_image") val movieImage: String?,
    val cover: String?,
    @SerializedName("backdrop_path") val backdrop: JsonElement?,
    val plot: String?,
    val description: String?,
    val genre: String?,
    @SerializedName("releasedate") val releaseDate: String?,
    @SerializedName("releaseDate") val releaseDate2: String?,
    @SerializedName("release_date") val releaseDate3: String?,
    @SerializedName("rating_5based") val rating5: String?,
    val rating: String?,
    @SerializedName("youtube_trailer") val trailer: String?
)

data class VMovieInfo(val info: JsonElement?)
data class VSeriesFull(val info: JsonElement?, val episodes: JsonElement?)

interface DetailService {
    @GET("player_api.php")
    suspend fun vod(
        @Query("username") u: String, @Query("password") p: String,
        @Query("vod_id") id: Int, @Query("action") a: String = "get_vod_info"
    ): VMovieInfo

    @GET("player_api.php")
    suspend fun series(
        @Query("username") u: String, @Query("password") p: String,
        @Query("series_id") id: Int, @Query("action") a: String = "get_series_info"
    ): VSeriesFull
}

data class Detail(
    val poster: String?, val backdrop: String?, val plot: String?, val genre: String?,
    val release: String?, val rating5: Float, val trailer: String?
)

data class Ep(val ch: Channel, val num: Int)

data class SubMeta(val title: String, val year: String?, val season: Int?, val episode: Int?)

object PlayMeta {
    val map = mutableMapOf<String, SubMeta>()

    fun cleanTitle(raw: String): String =
        raw.replace(Regex("""^\s*[A-Za-z]{2,3}\s*[-|:]\s*"""), "")
            .replace(Regex("""\s*[-–(\[]*\s*(?:19|20)\d{2}\s*[)\]]*\s*$"""), "")
            .trim()

    fun year(raw: String): String? = Regex("""(?:19|20)\d{2}""").findAll(raw).lastOrNull()?.value
}

object Details {
    private val gson = Gson()
    private val epType = object : TypeToken<Map<String, List<VEpisode>>>() {}.type
    private val listType = object : TypeToken<List<VEpisode>>() {}.type

    private fun base(server: String): String {
        var b = server.trim()
        if (!b.startsWith("http")) b = "http://$b"
        if (!b.endsWith("/")) b += "/"
        return b
    }

    private fun api(b: String): DetailService =
        Retrofit.Builder().baseUrl(b).client(Http.client).addConverterFactory(GsonConverterFactory.create())
            .build().create(DetailService::class.java)

    private fun toDetail(el: JsonElement?): Detail {
        val i: VInfo? = try {
            if (el != null && el.isJsonObject) gson.fromJson(el, VInfo::class.java) else null
        } catch (e: Exception) { null }
        val bd = try {
            val b = i?.backdrop
            when {
                b == null -> null
                b.isJsonArray && b.asJsonArray.size() > 0 -> b.asJsonArray[0].asString
                b.isJsonPrimitive -> b.asString
                else -> null
            }
        } catch (e: Exception) { null }
        val r10 = i?.rating?.toFloatOrNull()
        val r5 = i?.rating5?.toFloatOrNull() ?: ((r10 ?: 0f) / 2f)
        return Detail(
            poster = (i?.movieImage ?: i?.cover)?.ifBlank { null },
            backdrop = bd?.ifBlank { null },
            plot = (i?.plot ?: i?.description)?.ifBlank { null },
            genre = i?.genre?.ifBlank { null },
            release = (i?.releaseDate ?: i?.releaseDate2 ?: i?.releaseDate3)?.ifBlank { null },
            rating5 = r5.coerceIn(0f, 5f),
            trailer = i?.trailer?.ifBlank { null }
        )
    }

    suspend fun movie(l: SavedList, id: Int): Detail =
        toDetail(api(base(l.server)).vod(l.user, l.pass, id).info)

    suspend fun series(l: SavedList, id: Int): Pair<Detail, Map<String, List<Ep>>> {
        val b = base(l.server)
        val r = api(b).series(l.user, l.pass, id)
        val raw: Map<String, List<VEpisode>> = try {
            val e = r.episodes
            when {
                e == null -> emptyMap()
                e.isJsonObject -> gson.fromJson(e, epType)
                e.isJsonArray -> {          // some servers send the seasons as a list: position = season number
                    val m = LinkedHashMap<String, List<VEpisode>>()
                    e.asJsonArray.forEachIndexed { i, el ->
                        if (el.isJsonArray) m[i.toString()] = gson.fromJson(el, listType)
                    }
                    m
                }
                else -> emptyMap()
            }
        } catch (e: Exception) { throw IllegalStateException("Episode list could not be read") }
        val eps = raw.entries.sortedBy { it.key.toIntOrNull() ?: 0 }.associate { (season, list) ->
            season to list.sortedBy { it.num ?: 0 }.map { e ->
                Ep(
                    Channel(
                        e.title ?: "Episode ${e.num}",
                        "${b}series/${l.user}/${l.pass}/${e.id}.${e.ext ?: "mp4"}",
                        null, season
                    ),
                    e.num ?: 0
                )
            }
        }
        return toDetail(r.info) to eps
    }
}

private fun fmtDate(sec: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(sec * 1000))

private fun stars(r: Float): String {
    val full = r.roundToInt().coerceIn(0, 5)
    return "★".repeat(full) + "☆".repeat(5 - full)
}

@Composable
fun DetailScreen(
    list: SavedList, item: LibItem, isSeries: Boolean, fav: Boolean,
    onFav: () -> Unit, onBack: () -> Unit, onPlay: (Channel) -> Unit
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val compact = rememberIsCompact()
    var detail by remember(item.id) { mutableStateOf<Detail?>(null) }
    var seasons by remember(item.id) { mutableStateOf<Map<String, List<Ep>>>(emptyMap()) }
    var season by remember(item.id) { mutableStateOf<String?>(null) }
    var loading by remember(item.id) { mutableStateOf(true) }
    var note by remember(item.id) { mutableStateOf<String?>(null) }
    var tick by remember(item.id) { mutableStateOf(0) }
    var failed by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id, tick) {
        loading = true; failed = false; note = null
        try {
            if (isSeries) {
                val (d, eps) = withContext(Dispatchers.IO) { Details.series(list, item.id) }
                detail = d; seasons = eps; season = eps.keys.firstOrNull()
                if (eps.isEmpty()) note = "No episodes available"
            } else {
                detail = withContext(Dispatchers.IO) { Details.movie(list, item.id) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isSeries) {
                failed = true
                note = "Could not load episodes: " + (e.message ?: e.javaClass.simpleName).take(70)
            }
        }
        loading = false
    }

    ResumeStore.init(ctx)
    val resumeVer = ResumeStore.version
    val queue = remember(seasons) {
        seasons.flatMap { (sn, list) ->
            list.map { e ->
                e.ch to SubMeta(PlayMeta.cleanTitle(item.name), PlayMeta.year(item.name), sn.toIntOrNull(), e.num)
            }
        }
    }
    LaunchedEffect(queue) {
        if (isSeries && queue.isNotEmpty()) { PlayQueue.items = queue; PlayQueue.play = onPlay }
    }

    fun playMovie() {
        PlayQueue.clear()
        PlayMeta.map[item.url] = SubMeta(PlayMeta.cleanTitle(item.name), PlayMeta.year(item.name), null, null)
        onPlay(Channel(item.name, item.url, item.poster, item.cat))
    }

    fun playQueued(ch: Channel, meta: SubMeta) {
        PlayQueue.items = queue; PlayQueue.play = onPlay
        PlayMeta.map[ch.url] = meta
        onPlay(ch)
    }

    fun playEp(s: String, ep: Ep) {
        PlayQueue.items = queue; PlayQueue.play = onPlay
        PlayMeta.map[ep.ch.url] =
            SubMeta(PlayMeta.cleanTitle(item.name), PlayMeta.year(item.name), s.toIntOrNull(), ep.num)
        onPlay(ep.ch)
    }

    fun openTrailer() {
        val id = detail?.trailer
        if (id.isNullOrBlank()) {
            Toast.makeText(ctx, "Trailer not available", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse(if (id.startsWith("http")) id else "https://www.youtube.com/watch?v=$id")
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "No app found to open the trailer", Toast.LENGTH_SHORT).show()
        }
    }

    // Remote control: start directly on the Watch Now button (not on the back arrow)
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item.id) {
        delay(250)
        runCatching { playFocus.requestFocus() }
    }

    val poster = detail?.poster ?: item.poster
    val backdrop = detail?.backdrop ?: poster
    val gold = Color(0xFFFFD54F)

    Box(
        Modifier.fillMaxSize()
            .screenBg()
    ) {
        AsyncImage(
            backdrop, null, Modifier.fillMaxWidth().height(150.dp),
            contentScale = ContentScale.Crop, alpha = 0.35f
        )
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("↩", fontSize = 28.sp, color = Color.White, modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            val info: @Composable ColumnScope.() -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val cont = if (isSeries && resumeVer >= 0) PlayQueue.continuePoint()?.takeIf { c -> queue.any { it.first.url == c.first.url } } else null
                        val movieResume = !isSeries && resumeVer >= 0 && ResumeStore.get(item.url) > 0L
                        val bw = if (compact) Modifier.weight(1f) else Modifier
                        Button(
                            modifier = bw.focusRequester(playFocus),
                            onClick = {
                                if (!isSeries) playMovie()
                                else if (cont != null) playQueued(cont.first, cont.second)
                                else {
                                    val s = season
                                    val ep = s?.let { seasons[it]?.firstOrNull() }
                                    if (s != null && ep != null) playEp(s, ep)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = C.Blue)
                        ) {
                            Text(
                                when {
                                    cont != null -> "▶  Continue S${cont.second.season ?: 1} E${cont.second.episode ?: 1}"
                                    movieResume -> "▶  Resume"
                                    else -> "▶  Watch Now"
                                }
                            )
                        }
                        Button(
                            modifier = bw,
                            onClick = { openTrailer() },
                            colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy)
                        ) { Text("🎬  Trailer") }
                        Text(
                            if (fav) "★" else "☆", fontSize = 30.sp, color = gold,
                            modifier = Modifier.clickable(onClick = onFav).padding(4.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(item.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    detail?.release?.let { Text(it, color = C.Text, fontSize = 14.sp) }
                    detail?.let { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stars(d.rating5), color = gold, fontSize = 18.sp)
                            Text("  " + "%.1f".format(d.rating5), color = C.Text, fontSize = 13.sp)
                        }
                    }
                    detail?.genre?.let { Text("Genre : $it", color = C.Text, fontSize = 14.sp) }
                    if (item.added > 0) Text("Date Added : ${fmtDate(item.added)}", color = C.Text, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    detail?.plot?.let { Text(it, color = Color.White, fontSize = 14.sp) }
                    if (isSeries) {
                        Spacer(Modifier.height(12.dp))
                        note?.let { Text(it, color = Color(0xFFFF8A8A)) }
                        if (failed) Button(
                            onClick = { tick++ },
                            colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy)
                        ) { Text("Retry") }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            seasons.keys.forEach { s ->
                                FilterChip(season == s, { season = s }, { Text("Season $s") })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        season?.let { s ->
                            seasons[s].orEmpty().forEach { ep ->
                                Text(
                                    (if (ResumeStore.isDone(ep.ch.url)) "✓  " else "▶  ") + "${ep.num}. ${ep.ch.name}", color = Color.White, fontSize = 15.sp,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(6.dp)).background(Color(0x55000000))
                                        .clickable { playEp(s, ep) }.padding(12.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
            }
            if (compact) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AsyncImage(
                        poster, null,
                        Modifier.height(190.dp).aspectRatio(2f / 3f).align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(8.dp)).background(Color.Black),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(10.dp))
                    info()
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    AsyncImage(
                        poster, null,
                        Modifier.width(140.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color.Black),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        info()
                    }
                }
            }
        }
    }
}
