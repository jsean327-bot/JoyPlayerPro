package com.joyplayer.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        { if (it.length <= 4 && it.all { c -> c.isDigit() }) onChange(it) },
        singleLine = true,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}

@Composable
fun CategoryPicker(
    title: String, categories: List<String>, initial: Set<String>,
    onSave: (Set<String>) -> Unit, onDismiss: () -> Unit
) {
    var sel by remember { mutableStateOf(initial) }
    var q by remember { mutableStateOf("") }
    val shown = categories.filter { q.isBlank() || it.contains(q, true) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF14102E)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    q, { q = it }, singleLine = true,
                    placeholder = { Text("Filter...") }, modifier = Modifier.weight(1f)
                )
                Button(onClick = { onSave(sel) }) { Text("Save (${sel.size})") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { sel = sel + shown }) { Text("Select shown") }
                TextButton(onClick = { sel = sel - shown.toSet() }) { Text("Clear shown") }
            }
            Column(Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState())) {
                shown.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { sel = if (c in sel) sel - c else sel + c },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(c in sel, { sel = if (it) sel + c else sel - c })
                        Text(c, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryPickerLoader(
    kind: String, locked: Boolean, active: SavedList?, channels: List<Channel>,
    onDismiss: () -> Unit
) {
    var cats by remember { mutableStateOf<List<String>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(kind, active?.id) {
        if (active == null) {
            err = "Connect to a playlist first"
            return@LaunchedEffect
        }
        try {
            cats = if (kind == "live") {
                channels.map { it.group }.distinct().sorted()
            } else {
                if (active.type != "xtream") error("Movies and Series need an Xtream list")
                withContext(Dispatchers.IO) { Vod.load(active, kind == "series") }
                    .map { it.cat }.distinct().sorted()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            err = e.message ?: "Error"
        }
    }
    val name = when (kind) { "live" -> "Live"; "movies" -> "Movie"; else -> "Series" }
    val title = (if (locked) "Locked " else "Hidden ") + name + " categories"
    val list = cats
    val id = active?.id ?: ""
    when {
        err != null -> SDialog(title, onDismiss) { Text(err ?: "", color = Color(0xFFFF8A8A)) }
        list == null -> SDialog(title, onDismiss) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else -> CategoryPicker(
            title, list,
            if (locked) AppPrefs.locked(kind, id) else AppPrefs.hidden(kind, id),
            { s ->
                if (locked) AppPrefs.setLocked(kind, id, s) else AppPrefs.setHidden(kind, id, s)
                onDismiss()
            },
            onDismiss
        )
    }
}

@Composable
fun ParentalDialog(active: SavedList?, channels: List<Channel>, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(if (AppPrefs.hasPin) "verify" else "create") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(Parental.unlocked) }

    if (picker.isNotEmpty()) {
        CategoryPickerLoader(picker, true, active, channels) { picker = "" }
    } else when (step) {
        "verify" -> SDialog("Enter PIN", onDismiss) {
            PinField(pin, "PIN") { pin = it }
            err?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp) }
            Button(onClick = {
                if (AppPrefs.checkPin(pin)) { step = "menu"; pin = ""; err = null } else err = "Wrong PIN"
            }) { Text("Continue") }
        }
        "create", "change" -> SDialog(if (step == "create") "Create PIN" else "New PIN", onDismiss) {
            PinField(pin, "New PIN (4 digits)") { pin = it }
            PinField(pin2, "Confirm PIN") { pin2 = it }
            err?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp) }
            Button(onClick = {
                when {
                    pin.length != 4 -> err = "PIN must be 4 digits"
                    pin != pin2 -> err = "PINs do not match"
                    else -> { AppPrefs.setPin(pin); pin = ""; pin2 = ""; err = null; step = "menu" }
                }
            }) { Text("Save PIN") }
        }
        else -> SDialog("Parental Control", onDismiss) {
            SOpt("🔒  Locked Live categories", false) { picker = "live" }
            SOpt("🔒  Locked Movie categories", false) { picker = "movies" }
            SOpt("🔒  Locked Series categories", false) { picker = "series" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(unlocked, { unlocked = it; Parental.unlocked = it })
                Text("  Show locked content now", color = Color.White, fontSize = 14.sp)
            }
            SOpt("Change PIN", false) { pin = ""; pin2 = ""; err = null; step = "change" }
            SOpt("Remove PIN", false) { AppPrefs.clearPin(); Parental.unlocked = false; onDismiss() }
        }
    }
}
