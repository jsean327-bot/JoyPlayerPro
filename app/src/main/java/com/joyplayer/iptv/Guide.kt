package com.joyplayer.iptv

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun hm(sec: Long): String =
    SimpleDateFormat(if (AppPrefs.clock24) "HH:mm" else "hh:mm a", Locale.getDefault()).format(Date(sec * 1000))

private fun dayKey(sec: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sec * 1000))

/**
 * Full program guide of one channel: a tab for every day, past programs of channels with
 * catch-up are playable (⏪), the running program plays live (●).
 */
@Composable
fun ProgramGuideDialog(ch: Channel, fallback: List<EpgItem>, onPlay: (Channel) -> Unit, onDismiss: () -> Unit) {
    val now = rememberEpgNow()
    val loaded by produceState<List<EpgItem>?>(null, ch.url) {
        val f = Epg.loadFull(ch.url)
        value = if (f.isNotEmpty()) f else fallback
    }
    val items = loaded
    val ar = Lang.code == "ar"
    var selDay by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<EpgItem?>(null) }

    SDialog(ch.name, onDismiss, maxHeight = 380.dp) {
        if (items == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@SDialog
        }
        if (items.isEmpty()) {
            Text("No program info", color = C.Text, fontSize = 13.sp)
            return@SDialog
        }
        val days = items.map { dayKey(it.start) }.distinct()
        val today = dayKey(now)
        val yesterday = dayKey(now - 86400)
        val day = selDay ?: today.takeIf { it in days } ?: days.first()
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { d ->
                val label = when (d) {
                    today -> if (ar) "اليوم" else "Today"
                    yesterday -> if (ar) "أمس" else "Yesterday"
                    else -> SimpleDateFormat("EEE dd/MM", Locale.getDefault()).format(
                        items.first { dayKey(it.start) == d }.let { Date(it.start * 1000) }
                    )
                }
                FilterChip(day == d, { selDay = d; open = null }, { Text(label) })
            }
        }
        if (ch.archiveDays > 0) {
            Text(
                if (ar) "⏪ البرامج السابقة قابلة للتشغيل (آخر ${ch.archiveDays} أيام)"
                else "⏪ Past programs can be played (last ${ch.archiveDays} days)",
                color = Color(0xFF6EE7A0), fontSize = 12.sp
            )
        }
        items.filter { dayKey(it.start) == day }.forEach { p ->
            val isNow = p.start <= now && now < p.end
            val past = p.end <= now
            val archive = past && ch.archiveDays > 0 && p.start > now - ch.archiveDays * 86400L
            val icon = when { isNow -> "●  "; archive -> "⏪  "; else -> "" }
            SOpt("$icon${hm(p.start)} - ${hm(p.end)}   ${p.title}", isNow) {
                when {
                    isNow -> { onPlay(ch); onDismiss() }
                    archive -> {
                        val u = Epg.catchupUrl(ch.url, p)
                        if (u != null) {
                            onPlay(Channel("${ch.name} • ${p.title}", u, ch.logo, ch.group))
                            onDismiss()
                        }
                    }
                    else -> open = if (open === p) null else p
                }
            }
            if (open === p && p.desc.isNotBlank()) {
                Text(p.desc, color = C.Text, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}
