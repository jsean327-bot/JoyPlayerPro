package com.joyplayer.iptv

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Saves / restores everything the app remembers (playlists, favorites, history, settings). */
object Backup {
    private const val APP = "JoyPlayer IPTV Pro"

    private val files = listOf(
        "joyplayer", "joyplayer_app", "joyplayer_fav", "joyplayer_lib", "joyplayer_settings",
        "joyplayer_subs", "joyplayer_resume", "joyplayer_player", "joyplayer_lang"
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun export(ctx: Context): String {
        val root = JsonObject()
        root.addProperty("app", APP)
        root.addProperty("version", 1)
        root.addProperty("created", System.currentTimeMillis())
        val prefs = JsonObject()
        for (name in files) {
            val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            val o = JsonObject()
            for ((k, v) in sp.all) {
                val e = JsonObject()
                when (v) {
                    is String -> { e.addProperty("t", "s"); e.addProperty("v", v) }
                    is Int -> { e.addProperty("t", "i"); e.addProperty("v", v) }
                    is Long -> { e.addProperty("t", "l"); e.addProperty("v", v) }
                    is Boolean -> { e.addProperty("t", "b"); e.addProperty("v", v) }
                    is Float -> { e.addProperty("t", "f"); e.addProperty("v", v) }
                    is Set<*> -> {
                        val a = JsonArray()
                        v.forEach { a.add(it.toString()) }
                        e.addProperty("t", "ss")
                        e.add("v", a)
                    }
                    else -> continue
                }
                o.add(k, e)
            }
            prefs.add(name, o)
        }
        root.add("prefs", prefs)
        return root.toString()
    }

    /** Replaces the saved data with the backup. Returns how many entries were restored. */
    fun restore(ctx: Context, json: String): Int {
        val root = try {
            JsonParser.parseString(json.trim()).asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("This is not a JoyPlayer backup")
        }
        val isOurs = runCatching { root.get("app").asString == APP }.getOrDefault(false)
        val prefs = root.getAsJsonObject("prefs")
        if (!isOurs || prefs == null) throw IllegalArgumentException("This is not a JoyPlayer backup")
        var n = 0
        for (name in files) {
            val o = prefs.getAsJsonObject(name) ?: continue
            val ed = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            ed.clear()
            for ((k, el) in o.entrySet()) {
                runCatching {
                    val e = el.asJsonObject
                    when (e.get("t").asString) {
                        "s" -> ed.putString(k, e.get("v").asString)
                        "i" -> ed.putInt(k, e.get("v").asInt)
                        "l" -> ed.putLong(k, e.get("v").asLong)
                        "b" -> ed.putBoolean(k, e.get("v").asBoolean)
                        "f" -> ed.putFloat(k, e.get("v").asFloat)
                        "ss" -> ed.putStringSet(k, e.getAsJsonArray("v").map { it.asString }.toSet())
                        else -> {}
                    }
                    n++
                }
            }
            ed.commit()
        }
        return n
    }

    fun fetch(url: String): String {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) throw IllegalArgumentException("Enter a link starting with http")
        val req = Request.Builder().url(u).header("User-Agent", "JoyPlayer/1.0").build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            val body = r.body ?: throw IllegalStateException("Empty response")
            if (body.contentLength() > 8_000_000L) throw IllegalStateException("File is too large")
            body.string()
        }
    }
}

@Composable
fun BackupDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                msg = try {
                    withContext(Dispatchers.IO) {
                        val data = Backup.export(ctx).toByteArray(Charsets.UTF_8)
                        ctx.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                            ?: throw IllegalStateException("Cannot write the file")
                    }
                    "✓ Backup saved"
                } catch (e: Exception) {
                    "✗ " + (e.message ?: "Could not save the backup")
                }
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            ?: throw IllegalStateException("Cannot read the file")
                    }
                    pending = text
                    msg = null
                } catch (e: Exception) {
                    msg = "✗ " + (e.message ?: "Could not read the file")
                }
            }
        }
    }

    SDialog("Backup & Restore", onDismiss, maxHeight = 460.dp) {
        Text(
            "The backup contains your playlists (with passwords), favorites, history and settings. Keep it private.",
            color = C.Text, fontSize = 12.sp
        )
        SOpt("📤  Save backup to a file", false) {
            val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            try { saveLauncher.launch("JoyPlayer-backup-$day.json") }
            catch (e: Exception) { msg = "✗ No file manager found on this device. Use the clipboard or link." }
        }
        SOpt("📥  Restore from a file", false) {
            try { openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            catch (e: Exception) { msg = "✗ No file manager found on this device. Use the clipboard or link." }
        }
        SOpt("📋  Copy backup to clipboard", false) {
            try {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("JoyPlayer backup", Backup.export(ctx)))
                msg = "✓ Copied. Paste it in a note or a file to keep it."
            } catch (e: Exception) {
                msg = "✗ " + (e.message ?: "Could not copy")
            }
        }
        SOpt("📋  Restore from clipboard", false) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
            if (t.isBlank()) msg = "✗ The clipboard is empty" else { pending = t; msg = null }
        }
        OutlinedTextField(
            link, { link = it }, singleLine = true, label = { Text("Backup link (https://...)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                scope.launch {
                    try {
                        val text = withContext(Dispatchers.IO) { Backup.fetch(link) }
                        pending = text
                        msg = null
                    } catch (e: Exception) {
                        msg = "✗ " + (e.message ?: "Could not download the backup")
                    }
                }
            },
            enabled = link.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy)
        ) { Text("Restore from link") }
        msg?.let { Text(it, color = if (it.startsWith("✓")) Color(0xFF6EE7A0) else Color(0xFFFF8A8A), fontSize = 13.sp) }
    }

    pending?.let { json ->
        SDialog("Restore backup?", { pending = null }) {
            Text(
                "This replaces your playlists, favorites, history and settings with the backup.",
                color = Color.White, fontSize = 14.sp
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        try {
                            val n = Backup.restore(ctx, json)
                            Lang.reload()
                            pending = null
                            Toast.makeText(ctx, "✓ $n", Toast.LENGTH_SHORT).show()
                            (ctx as? Activity)?.recreate()
                        } catch (e: Exception) {
                            pending = null
                            msg = "✗ " + Lang.tr(e.message ?: "Restore failed")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy),
                    modifier = Modifier.weight(1f)
                ) { Text("OK") }
                Button(
                    onClick = { pending = null },
                    colors = ButtonDefaults.buttonColors(containerColor = C.BurgundyDark),
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
            }
        }
    }
}
