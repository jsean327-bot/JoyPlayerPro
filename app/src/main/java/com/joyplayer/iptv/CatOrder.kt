package com.joyplayer.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Your own order of the categories (saved per playlist and per section). Names that are not in it go to the end. */
fun <T> List<T>.applyOrder(order: List<String>, name: (T) -> String): List<T> {
    if (order.isEmpty()) return this
    val idx = HashMap<String, Int>()
    order.forEachIndexed { i, n -> idx[n] = i }
    return sortedBy { idx[name(it)] ?: Int.MAX_VALUE }
}

/** Moves every ticked name one step up (delta = -1) or down (delta = +1); they travel together, like carrying them. */
fun movePickedNames(names: List<String>, picked: Set<String>, delta: Int): List<String> {
    val m = names.toMutableList()
    if (delta < 0) {
        for (i in 1 until m.size) if (m[i] in picked && m[i - 1] !in picked) { val t = m[i]; m[i] = m[i - 1]; m[i - 1] = t }
    } else {
        for (i in m.size - 2 downTo 0) if (m[i] in picked && m[i + 1] !in picked) { val t = m[i]; m[i] = m[i + 1]; m[i + 1] = t }
    }
    return m
}

/** Keeps the carried category on screen while it travels. */
suspend fun LazyListState.ensureVisible(index: Int) {
    if (index < 0) return
    val v = layoutInfo.visibleItemsInfo
    if (v.isEmpty() || index <= v.first().index || index >= v.last().index) {
        animateScrollToItem((index - 3).coerceAtLeast(0))
    }
}

/** The small square with a tick shown beside each category while reordering. */
@Composable
fun PickBox(checked: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 6.dp).size(24.dp).clip(RoundedCornerShape(4.dp))
            .background(if (checked) Color(0xFF3B82F6) else Color.Transparent)
            .border(2.dp, Color.White, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { if (checked) Text("✓", color = Color.White, fontSize = 15.sp) }
}

/**
 * Bar above the category list. Reorder mode: tick the categories you want to move, then use ▲ / ▼:
 * the ticked ones stay with you while they go up or down.
 */
@Composable
fun OrderBar(
    editing: Boolean, pickedCount: Int, onToggle: () -> Unit,
    onUp: () -> Unit, onDown: () -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier
) {
    val chip = Modifier.clip(RoundedCornerShape(14.dp))
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (editing) "✔ Done" else "↕ Reorder", color = Color.White, fontSize = 13.sp,
            modifier = chip.background(if (editing) C.Burgundy else C.Row)
                .clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 7.dp)
        )
        if (editing) {
            val on = pickedCount > 0
            Text(
                "▲", color = Color.White, fontSize = 15.sp,
                modifier = chip.background(if (on) Color(0xFF3B82F6) else C.Row)
                    .clickable(enabled = on, onClick = onUp).padding(horizontal = 14.dp, vertical = 6.dp)
            )
            Text(
                "▼", color = Color.White, fontSize = 15.sp,
                modifier = chip.background(if (on) Color(0xFF3B82F6) else C.Row)
                    .clickable(enabled = on, onClick = onDown).padding(horizontal = 14.dp, vertical = 6.dp)
            )
            Text(
                "↺", color = Color.White, fontSize = 15.sp,
                modifier = chip.background(C.Row).clickable(onClick = onReset).padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
