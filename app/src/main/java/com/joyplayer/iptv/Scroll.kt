package com.joyplayer.iptv

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FastScroller(
    modifier: Modifier,
    fraction: Float,
    scrolling: Boolean,
    onFraction: (Float) -> Unit
) {
    val current by rememberUpdatedState(onFraction)
    var heightPx by remember { mutableStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(false) }
    val thumbPx = with(LocalDensity.current) { 52.dp.toPx() }
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) show = true else { delay(1200); show = false }
    }
    val alpha by animateFloatAsState(if (show) 1f else 0f, label = "scrollbar")
    fun toFraction(y: Float) =
        ((y - thumbPx / 2) / (heightPx - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)

    Box(
        modifier.width(30.dp).fillMaxHeight()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { o -> dragging = true; current(toFraction(o.y)) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, _ -> change.consume(); current(toFraction(change.position.y)) }
                )
            }
    ) {
        if (alpha > 0.01f) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 4.dp)
                    .offset { IntOffset(0, (fraction.coerceIn(0f, 1f) * (heightPx - thumbPx).coerceAtLeast(0f)).toInt()) }
                    .alpha(alpha).width(10.dp).height(52.dp)
                    .clip(RoundedCornerShape(5.dp)).background(C.Blue)
            )
        }
    }
}

// Same name as the built-in list so existing screens get a scrollbar automatically
@Composable
fun LazyColumn(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val fraction by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val span = info.totalItemsCount - info.visibleItemsInfo.size
            if (span > 0) state.firstVisibleItemIndex.toFloat() / span else 0f
        }
    }
    Box(modifier) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(end = 26.dp),
            content = content
        )
        FastScroller(Modifier.align(Alignment.CenterEnd), fraction, state.isScrollInProgress) { f ->
            val info = state.layoutInfo
            val span = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(0)
            scope.launch { state.scrollToItem((f * span).toInt()) }
        }
    }
}

@Composable
fun LazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyGridScope.() -> Unit
) {
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val dir = LocalLayoutDirection.current
    val pad = PaddingValues(
        start = contentPadding.calculateStartPadding(dir),
        top = contentPadding.calculateTopPadding(),
        end = contentPadding.calculateEndPadding(dir) + 26.dp,
        bottom = contentPadding.calculateBottomPadding()
    )
    val fraction by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val span = info.totalItemsCount - info.visibleItemsInfo.size
            if (span > 0) state.firstVisibleItemIndex.toFloat() / span else 0f
        }
    }
    Box(modifier) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = pad,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            content = content
        )
        FastScroller(Modifier.align(Alignment.CenterEnd), fraction, state.isScrollInProgress) { f ->
            val info = state.layoutInfo
            val span = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(0)
            scope.launch { state.scrollToItem((f * span).toInt()) }
        }
    }
}

// Small preview window: double tap -> fullscreen
@Composable
fun PreviewSurface(player: ExoPlayer, sel: Channel?, onFull: (Channel) -> Unit) {
    val current by rememberUpdatedState(sel)
    val full by rememberUpdatedState(onFull)
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                this.player = player
                val gd = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        current?.let { full(it) }
                        return true
                    }
                })
                setOnTouchListener { _, e -> gd.onTouchEvent(e); true }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// Fullscreen player: double tap -> back to small
@Composable
fun FullPlayer(ch: Channel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val back by rememberUpdatedState(onBack)
    val player = remember(ch) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(ch.url)); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                val gd = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        back()
                        return true
                    }
                })
                setOnTouchListener { _, e -> gd.onTouchEvent(e); false }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
