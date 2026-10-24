package com.joyplayer.iptv

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.launch
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
    // the list keeps its scroll position and the cursor goes back to the movie you opened
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val rowsState = androidx.compose.foundation.lazy.rememberLazyListState()
    var lastOpen by remember(key) { mutableStateOf<String?>(null) }
    val backFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val navScope = rememberCoroutineScope()
    val itemReqs = remember(key) { HashMap<String, androidx.compose.ui.focus.FocusRequester>() }
    fun reqOf(id: String) = itemReqs.getOrPut(id) { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(cat, query, order, key) {
        runCatching { gridState.scrollToItem(0); rowsState.scrollToItem(0) }
    }
    // entering Movies / Series: the cursor starts on the first poster, not on "Home"
    val firstFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var firstDone by remember(key) { mutableStateOf(false) }
    LaunchedEffect(loading, open) {
        if (!loading && open == null && !firstDone && lastOpen == null) {
            kotlinx.coroutines.delay(400)
            runCatching { firstFocus.requestFocus() }
            firstDone = true
        }
    }
    LaunchedEffect(open) {
        if (open == null && lastOpen != null) {
            kotlinx.coroutines.delay(350)
            runCatching { backFocus.requestFocus() }
        }
    }

    LaunchedEffect(key) {
        if (list == null || list.type != "xtream") {
            loading = false
            error = "Movies and Series need an Xtream list"
            return@LaunchedEffect
        }
        loading = true; error = null
        try {
            val hid = AppPrefs.hidden(kind, list.id)
            all = withContext(Dispatchers.IO) { Vod.load(list, isSeries) }
                .filter { it.cat !in hid }
        } catch (e: Exception) {
            error = e.message ?: "Error"
        }
        loading = false
    }

    val lockedCats = if (Parental.unlocked) emptySet() else AppPrefs.locked(kind, list?.id)
    val openedNow = Parental.opened.toList()
    val closed = remember(lockedCats, key, openedNow) {
        lockedCats.filter { "$kind|${list?.id}|$it" !in openedNow }.toSet()
    }
    val vis = remember(all, closed) { if (closed.isEmpty()) all else all.filter { it.cat !in closed } }
    var pinFor by remember { mutableStateOf<String?>(null) }
    DisposableEffect(kind) { onDispose { Parental.opened.removeAll { it.startsWith("$kind|") } } }
    LaunchedEffect(closed) { if (cat in closed) cat = "*all*" }
    val pickCat: (String) -> Unit = { c -> if (c in closed) pinFor = c else cat = c }

    var favCatSet by remember(kind, list?.id) { mutableStateOf(AppPrefs.favCats(kind, list?.id)) }
    var catOrder by remember(kind, list?.id) { mutableStateOf(AppPrefs.catOrder(kind, list?.id)) }
    var editCats by remember { mutableStateOf(false) }
    val groups = remember(all, order, favCatSet, catOrder) {
        val g = all.groupBy { it.cat }.map { it.key to it.value.size }
        val sorted = when (order) {
            3 -> g.sortedByCategory(false) { it.first }
            4 -> g.sortedByCategory(true) { it.first }
            else -> g
        }
        if (catOrder.isNotEmpty()) sorted.applyOrder(catOrder) { it.first }     // your own order wins
        else sorted.sortedByDescending { it.first in favCatSet }                  // favorite categories first
    }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val catListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val moveNames: (Set<String>, Int) -> Unit = { ks, d ->
        val id = list?.id
        if (id != null) {
            val moved = movePickedNames(groups.map { it.first }, ks, d)
            catOrder = moved
            AppPrefs.setCatOrder(kind, id, moved)
        }
    }
    val resetOrder: () -> Unit = {
        val id = list?.id
        picked = emptySet()
        if (id != null) { catOrder = emptyList(); AppPrefs.setCatOrder(kind, id, emptyList()) }
    }
    val togglePick: (String) -> Unit = { c -> picked = if (c in picked) picked - c else picked + c }
    LaunchedEffect(groups) {      // the carried categories stay in view
        if (editCats && picked.isNotEmpty()) catListState.ensureVisible(4 + groups.indexOfFirst { it.first in picked })
    }
    // long press (hold OK) on a category: add / remove it from the favorite categories
    val toggleFavCat: (String) -> Unit = { c ->
        val id = list?.id
        if (!c.startsWith("*") && id != null) {
            favCatSet = AppPrefs.toggleFavCat(kind, id, c)
            android.widget.Toast.makeText(
                ctx, Lang.tr(if (c in favCatSet) "Category added to favorites" else "Category removed from favorites"),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    val shown = remember(vis, favs, recents, cat, query, order, favCatSet) {
        val base = when (if (query.isBlank()) cat else "*all*") {
            "*all*" -> vis
            "*fav*" -> vis.filter { it.id.toString() in favs || it.cat in favCatSet }
            "*rec*" -> recents.mapNotNull { r -> vis.firstOrNull { it.id.toString() == r } }
            else -> vis.filter { it.cat == cat }
        }.filter { query.isBlank() || it.name.contains(query, true) }
        when (order) {
            1 -> base.sortedByDescending { it.added }
            2 -> base.sortedByDescending { it.rating }
            3 -> base.sortedByTitle(false) { it.name }
            4 -> base.sortedByTitle(true) { it.name }
            else -> base
        }
    }
    val orders = listOf("Order by number", "Order by added", "Order by rating", "Order by A-Z", "Order by Z-A")

    val openItem: (LibItem) -> Unit = { item ->
        store.pushRecent(key, item.id.toString())
        recents = store.recents(key)
        lastOpen = item.id.toString()
        open = item
    }
    val toggleFav: (LibItem) -> Unit = { item ->
        val k = item.id.toString()
        favs = if (k in favs) favs - k else favs + k
        store.saveFavs(key, favs)
        android.widget.Toast.makeText(ctx, Lang.tr(if (k in favs) "Added to favorites" else "Removed from favorites"), android.widget.Toast.LENGTH_SHORT).show()
    }

    val current = open
    if (current != null && list != null) {
        DetailScreen(list, current, isSeries, current.id.toString() in favs, { toggleFav(current) }, { open = null }, onPlay)
        return
    }

    val compact = rememberIsCompact()
    val catOptions = remember(groups, vis.size, favs.size, recents.size, closed, favCatSet) {
        listOf(
            CatOpt("*rec*", "Resume to Watch", recents.size),
            CatOpt("*all*", "All", vis.size),
            CatOpt("*fav*", "Favorite", vis.count { it.id.toString() in favs || it.cat in favCatSet })
        ) + groups.map { CatOpt(it.first, (if (it.first in closed) "🔒 " else "") + it.first, it.second, it.first in favCatSet) }
    }

    val sortRow: @Composable () -> Unit = {
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
    }

    val gridArea: @Composable () -> Unit = {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = Color(0xFFFF8A8A))
            }
            AppPrefs.listLayout -> LazyColumn(Modifier.padding(horizontal = 12.dp), rowsState) {
                items(shown) { item ->
                    Box(Modifier.padding(vertical = 4.dp)) {
                        PosterRow(item, item.id.toString() in favs, { openItem(item) }, { toggleFav(item) }, if (item.id.toString() == lastOpen) Modifier.focusRequester(backFocus) else if (item === shown.firstOrNull()) Modifier.focusRequester(firstFocus) else Modifier)
                    }
                }
            }
            else -> LazyVerticalGrid(
                GridCells.Adaptive(AppPrefs.posterDp.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                state = gridState
            ) {
                gridItemsIndexed(shown) { index, item ->
                    // Up / Down move to the poster straight above / below (same column), not to the next row's first one
                    val nav = Modifier.focusRequester(reqOf(item.id.toString())).onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionDown || e.key == Key.DirectionUp)) {
                            val down = e.key == Key.DirectionDown
                            val vis = gridState.layoutInfo.visibleItemsInfo
                            val cols = (vis.maxOfOrNull { it.column } ?: 0) + 1
                            var t = index + if (down) cols else -cols
                            if (t < 0) {
                                false                                   // first row: Up goes to the top bar as usual
                            } else {
                                if (t >= shown.size) t = if (index / cols < (shown.size - 1) / cols) shown.size - 1 else -1
                                if (t >= 0) {
                                    val target = shown[t].id.toString()
                                    navScope.launch {
                                        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == t }) {
                                            gridState.scrollToItem(t)
                                            kotlinx.coroutines.delay(80)
                                        }
                                        runCatching { reqOf(target).requestFocus() }
                                    }
                                }
                                true
                            }
                        } else false
                    }
                    val base = if (item.id.toString() == lastOpen) Modifier.focusRequester(backFocus) else if (item === shown.firstOrNull()) Modifier.focusRequester(firstFocus) else Modifier
                    PosterCard(item, item.id.toString() in favs, { openItem(item) }, { toggleFav(item) }, base.then(nav))
                }
            }
        }
    }

    if (compact) {
        Column(Modifier.fillMaxSize().screenBg()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x55000000))
                    .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem("Home", false) { onNav("home") }
                NavItem("Live", false) { onNav("live") }
                NavItem("Movies", kind == "movies") { onNav("movies") }
                NavItem("Series", kind == "series") { onNav("series") }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryButton(
                    cat, catOptions, { pickCat(it) }, Modifier.weight(0.42f), onLongPick = toggleFavCat,
                    onMovePicked = moveNames, onResetOrder = resetOrder
                )
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    placeholder = { Text("Search...") }, modifier = Modifier.weight(0.58f)
                )
            }
            sortRow()
            Box(Modifier.weight(1f).fillMaxWidth()) { gridArea() }
        }
    } else {
        Column(Modifier.fillMaxSize().screenBg()) {
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
                Column(Modifier.weight(0.30f).fillMaxHeight().background(Color(0x33000000)).padding(6.dp)) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp).size(60.dp)
                            .border(3.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("▶", fontSize = 22.sp, color = Color.White) }
                    Text(
                        "← Back", color = C.Text,
                        modifier = Modifier.clickable { onNav("home") }.padding(10.dp)
                    )
                    LazyColumn(state = catListState) {
                        item {
                            OrderBar(
                                editCats, picked.size, { editCats = !editCats; picked = emptySet() },
                                { moveNames(picked, -1) }, { moveNames(picked, 1) }, resetOrder
                            )
                        }
                        item { CatRow("Resume to Watch", recents.size, cat == "*rec*") { cat = "*rec*" } }
                        item { CatRow("All", vis.size, cat == "*all*") { cat = "*all*" } }
                        item { CatRow("Favorite", vis.count { it.id.toString() in favs || it.cat in favCatSet }, cat == "*fav*") { cat = "*fav*" } }
                        items(groups, key = { it.first }) { (g, n) ->
                            CatRow(
                                (if (g in closed) "🔒 " else "") + g, n, cat == g,
                                onLongClick = { toggleFavCat(g) },
                                starred = if (editCats) null else g in favCatSet, onStar = { toggleFavCat(g) },
                                checked = if (editCats) g in picked else null, onCheck = { togglePick(g) },
                                onMoveKey = if (editCats && g in picked) ({ d -> moveNames(picked, d) }) else null
                            ) { if (editCats) togglePick(g) else pickCat(g) }
                        }
                    }
                }
                Column(Modifier.weight(0.70f).fillMaxHeight()) {
                    sortRow()
                    gridArea()
                }
            }
        }
    }
    pinFor?.let { c ->
        PinGate(
            onOk = { Parental.opened.add("$kind|${list?.id}|$c"); cat = c; pinFor = null },
            onDismiss = { pinFor = null }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PosterCard(item: LibItem, fav: Boolean, onClick: () -> Unit, onFav: () -> Unit, focusMod: Modifier = Modifier) {
    Column(
        focusMod.clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A1B2E))
            .combinedClickable(onLongClick = onFav, onClick = onClick)
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PosterRow(item: LibItem, fav: Boolean, onClick: () -> Unit, onFav: () -> Unit, focusMod: Modifier = Modifier) {
    Row(
        focusMod.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A1B2E))
            .combinedClickable(onLongClick = onFav, onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            item.poster, null,
            Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(Color.Black),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (item.rating > 0.0) {
                Text(
                    "%.1f".format(item.rating), color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(4.dp))
                        .background(C.Blue).padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            if (fav) "★" else "☆", color = Color(0xFFFFD54F), fontSize = 24.sp,
            modifier = Modifier.clickable(onClick = onFav).padding(10.dp)
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
    Column(Modifier.fillMaxSize().screenBg().padding(16.dp)) {
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
