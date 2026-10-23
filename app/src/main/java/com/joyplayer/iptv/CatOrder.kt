package com.joyplayer.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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

/** [names] with [name] moved one step up (delta = -1) or down (delta = +1). */
fun moveName(names: List<String>, name: String, delta: Int): List<String> {
    val i = names.indexOf(name)
    val j = i + delta
    if (i < 0 || j < 0 || j >= names.size) return names
    val m = names.toMutableList()
    m[i] = m[j].also { m[j] = m[i] }
    return m
}

/** Small bar above the category list: switch the reorder mode on / off and reset to the normal order. */
@Composable
fun OrderBar(editing: Boolean, onToggle: () -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            if (editing) "✔ Done" else "↕ Reorder", color = Color.White, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(14.dp))
                .background(if (editing) C.Burgundy else C.Row)
                .clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 7.dp)
        )
        if (editing) {
            Text(
                "↺ Reset", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(C.Row)
                    .clickable(onClick = onReset).padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}
