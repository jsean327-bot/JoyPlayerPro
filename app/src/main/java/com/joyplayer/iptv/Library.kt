package com.joyplayer.iptv

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibStore(context: Context) {
    private val p = context.getSharedPreferences("joyplayer_lib", Context.MODE_PRIVATE)
    fun favs(k: String): Set<String> = p.getStringSet("fav_$k", emptySet())?.toSet() ?: emptySet()
    fun saveFavs(k: String, s: Set<String>) { p.edit().putStringSet("fav_$k", s).apply() }
    fun recents(k: String): List<String> =
        p.getString("rec_$k", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    fun pushRecent(k: String, id: String) {
        val l = listOf(id) + recents(k).filter { it != id }
        p.edit().putString("rec_$k", l.take(30).joinToString(",")).apply()
    }
}

private val LibBrush = Brush.verticalGradient(listOf(Color(0xFF0E1A4A), Color(0xFF2A1040), Color(0xFF4A0E22)))

@Composable
fun LibraryScreen(kind: String, list: SavedList?, onNav: (String) -> Unit, onPlay: (Channel) -> Unit) {
    val ctx = LocalContext.current
    val isSeries = kind == "series"
    val key = "${list?.id}_$kind"
    val store = remember { LibStore(ctx) }
    var all by remember(key) { mutableStateOf<List<LibItem>>(emptyList()) }
    var loading by remember(key) { mutableStateOf(true) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var favs by remember(key) { mutableStateOf(store.favs(key)) }
    var recents by remember(key) { mutableStateOf(store.recents(key)) }
    var cat by remember(key) { mutableStateOf("*all*") }
    var query by remember { mutableStateOf("") }
    var order by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var open by remember(key) { mutableStateOf<LibItem?>(null) }

    LaunchedEffect(key) {
        if (list == null || list.type != "xtream") {
            loading = false
            error = "Movies and Series need an Xtream list"
            return@LaunchedEffect
        }
        loading = true; error = null
        try {
            all = withContext(Dispatchers.IO) { Vod.load(list, isSeries) }
        } catch (e: Exception) {
            error = e.message ?: "Error"
        }
        loading = false
    }

    val groups = remember(all) { all.groupBy { it.cat }.map { it.key to it.value.size } }
    val shown = remember(all, favs, recents, cat, query, order) {
        val base = when (cat) {
            "*all*" -> all
            "*fav*" -> all.filter { it.id.toString() in favs }
            "*rec*" -> recents.mapNotNull { r -> all.firstOrNull { it.id.toString() == r } }
            else -> all.filter { it.cat == cat }
        }.filter { query.isBlank() || it.name.contains(query, true) }
        when (order) {
            1 -> base.sortedByDescending { it.added }
            2 -> base.sortedByDescending { it.rating }
            3 -> base.sortedBy { it.name.lowercase() }
            4 -> base.sortedByDescending { it.name.lowercase() }
            else -> base
        }
    }
    val orders = listOf("Order by number", "Order by added", "Order by rating", "Order by A-Z", "Order by Z-A")

    val openItem: (LibItem) -> Unit = { item ->
        store.pushRecent(key, item.id.toString())
        recents = store.recents(key)
        if (isSeries) open = item else onPlay(Channel(item.name, item.url, item.poster, item.cat))
    }
    val toggleFav: (LibItem) -> Unit = { item ->
        val k = item.id.toString()
        favs = if (k in favs) favs - k else favs + k
        store.saveFavs(key, favs)
    }

    val current = open
    if (current != null && list != null) {
        SeriesDetail(list, current, { open = null }, onPlay)
        return
    }

    Column(Modifier.fillMaxSize().background(LibBrush)) {
        Row(
            Modifier.fillMaxWidth().background(Color(0x55000000)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem("Home", false) { onNav("home") }
            NavItem("Live", false) { onNav("live") }
            NavItem("Movies", kind == "movies") { onNav("movies") }
            NavItem("Series", kind == "series") { onNav("series") }
            Spacer(Modifier.weight(1f))
            OutlinedTextField(
                query, { query = it }, singleLine = true,
                placeholder = { Text("Search...") }, modifier = Modifier.width(240.dp)
            )
        }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(0.24f).fillMaxHeight().background(Color(0x33000000)).padding(6.dp)) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp).size(60.dp)
                        .border(3.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("▶", fontSize = 22.sp, color = Color.White) }
                Text(
                    "← Back", color = C.Text,
                    modifier = Modifier.clickable { onNav("home") }.padding(10.dp)
                )
                LazyColumn {
                    item { CatRow("Resume to Watch", recents.size, cat == "*rec*") { cat = "*rec*" } }
                    item { CatRow("All", all.size, cat == "*all*") { cat = "*all*" } }
                    item { CatRow("Favorite", favs.size, cat == "*fav*") { cat = "*fav*" } }
                    items(groups) { (g, n) -> CatRow(g, n, cat == g) { cat = g } }
                }
            }
            Column(Modifier.weight(0.76f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Button(
                            onClick = { menu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy)
                        ) { Text(orders[order] + "  ▾", fontSize = 13.sp) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            orders.forEachIndexed { i, o ->
                                DropdownMenuItem(text = { Text(o) }, onClick = { order = i; menu = false })
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("All(${shown.size})", color = C.Text, fontWeight = FontWeight.Bold)
                }
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error ?: "", color = Color(0xFFFF8A8A))
                    }
                    else -> LazyVerticalGrid(
                        GridCells.Adaptive(128.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        gridItems(shown) { item ->
                            PosterCard(item, item.id.toString() in favs, { openItem(item) }, { toggleFav(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(item: LibItem, fav: Boolean, onClick: () -> Unit, onFav: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A1B2E)).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Color.Black)) {
            AsyncImage(item.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(
                if (fav) "★" else "☆", color = Color(0xFFFFD54F), fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopEnd).clickable(onClick = onFav).padding(6.dp)
            )
            Text(
                "%.1f".format(item.rating), color = Color.White, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .clip(RoundedCornerShape(4.dp)).background(C.Blue).padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        Text(
            item.name, color = Color.White, fontSize = 12.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(6.dp)
        )
    }
}

@Composable
fun SeriesDetail(list: SavedList, item: LibItem, onBack: () -> Unit, onPlay: (Channel) -> Unit) {
    BackHandler(onBack = onBack)
    var seasons by remember(item.id) { mutableStateOf<Map<String, List<Channel>>>(emptyMap()) }
    var season by remember(item.id) { mutableStateOf<String?>(null) }
    var loading by remember(item.id) { mutableStateOf(true) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) {
        try {
            val r = withContext(Dispatchers.IO) { Vod.episodes(list, item.id) }
            seasons = r
            season = r.keys.firstOrNull()
            if (r.isEmpty()) error = "No episodes available"
        } catch (e: Exception) {
            error = "No episodes available"
        }
        loading = false
    }
    Column(Modifier.fillMaxSize().background(LibBrush).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("← Back", color = C.Text, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                item.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = Color(0xFFFF8A8A)) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons.keys.toList()) { s ->
                FilterChip(season == s, { season = s }, { Text("Season $s") })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(season?.let { seasons[it] }.orEmpty()) { ep ->
                Text(
                    "▶  " + ep.name, color = Color.White, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0x55000000))
                        .clickable { onPlay(ep) }.padding(12.dp)
                )
            }
        }
    }
}
