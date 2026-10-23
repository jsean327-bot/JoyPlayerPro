package com.joyplayer.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

data class CatOpt(val key: String, val label: String, val count: Int, val fav: Boolean? = null)

/** Category picker used on narrow (portrait) screens instead of the left-hand category pane. */
@Composable
fun CategoryButton(
    current: String, options: List<CatOpt>, onPick: (String) -> Unit, modifier: Modifier = Modifier,
    onLongPick: ((String) -> Unit)? = null,
    onMovePicked: ((Set<String>, Int) -> Unit)? = null, onResetOrder: (() -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(options) {
        if (editing && picked.isNotEmpty()) listState.ensureVisible(options.indexOfFirst { it.key in picked })
    }
    val cur = options.firstOrNull { it.key == current }
    Button(
        onClick = { open = true }, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy)
    ) {
        Text((cur?.label ?: "All") + "  ▾", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF14102E)).padding(12.dp)
            ) {
                Text(
                    "Categories", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (onMovePicked != null && onResetOrder != null) {
                    OrderBar(
                        editing, picked.size, { editing = !editing; picked = emptySet() },
                        { onMovePicked(picked, -1) }, { onMovePicked(picked, 1) },
                        { picked = emptySet(); onResetOrder() }
                    )
                }
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp), state = listState) {
                    items(options) { o ->
                        CatRow(
                            o.label, o.count, o.key == current, onLongClick = onLongPick?.let { f -> { f(o.key) } },
                            starred = if (editing) null else o.fav, onStar = onLongPick?.let { f -> { f(o.key) } },
                            checked = if (editing && !o.key.startsWith("*")) o.key in picked else null,
                            onCheck = { picked = if (o.key in picked) picked - o.key else picked + o.key }
                        ) {
                            if (editing) {
                                if (!o.key.startsWith("*")) picked = if (o.key in picked) picked - o.key else picked + o.key
                            } else { onPick(o.key); open = false }
                        }
                    }
                }
            }
        }
    }
}
