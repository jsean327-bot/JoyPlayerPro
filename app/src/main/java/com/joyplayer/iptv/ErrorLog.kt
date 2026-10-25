package com.joyplayer.iptv

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps the last problems (playback errors, reconnects, load errors, crashes) so one tap gives a report to send. */
object AppLog {
    private const val MAX = 80
    private val lines = ArrayDeque<String>()
    private var file: File? = null

    fun init(ctx: Context) {
        synchronized(lines) {
            if (file != null) return
            val f = File(ctx.applicationContext.filesDir, "app_log.txt")
            file = f
            try { if (f.exists()) f.readLines().takeLast(MAX).forEach { lines.addLast(it) } } catch (_: Throwable) { }
        }
    }

    /** Removes passwords from links and texts before they are stored. */
    fun scrub(s: String): String = s
        .replace(Regex("(https?://[^/\\s]+/(?:live|movie|series|timeshift)/)[^/\\s]+/[^/\\s]+/"), "$1***/***/")
        .replace(Regex("(username=)[^&\\s]*"), "$1***")
        .replace(Regex("(password=)[^&\\s]*"), "$1***")

    fun add(tag: String, msg: String) {
        val t = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = "$t [$tag] " + scrub(msg).replace('\n', ' ').take(300)
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX) lines.removeFirst()
            try { file?.writeText(lines.joinToString("\n")) } catch (_: Throwable) { }
        }
    }

    fun clear() {
        synchronized(lines) {
            lines.clear()
            try { file?.delete() } catch (_: Throwable) { }
        }
    }

    fun report(ctx: Context): String {
        val sb = StringBuilder()
        sb.appendLine("JoyPlayer IPTV Pro — error report")
        sb.appendLine("Build ${Updater.installedBuild(ctx)} | ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE}")
        sb.appendLine("Secure DNS: ${AppPrefs.doh} | Live format: ${AppPrefs.liveFormat}")
        sb.appendLine()
        CrashLog.pending(ctx)?.let { sb.appendLine("Last crash:"); sb.appendLine(scrub(it).take(1500)); sb.appendLine() }
        val copy = synchronized(lines) { lines.toList() }
        if (copy.isEmpty()) sb.appendLine("(no problems recorded)") else copy.asReversed().forEach { sb.appendLine(it) }
        return sb.toString()
    }
}

@Composable
fun ErrorLogDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(AppLog.report(ctx)) }
    SDialog("Error Log", onDismiss, maxHeight = 340.dp) {
        Text(text, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                try {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("JoyPlayer report", text))
                    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { }
            }, colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy)) { Text("Copy") }
            Button(onClick = {
                try {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    ctx.startActivity(Intent.createChooser(i, "Send report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Toast.makeText(ctx, "No app to share with. Use Copy.", Toast.LENGTH_SHORT).show()
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = C.Blue)) { Text("Share") }
            Button(onClick = {
                AppLog.clear(); CrashLog.clear(ctx); text = AppLog.report(ctx)
            }, colors = ButtonDefaults.buttonColors(containerColor = C.BurgundyDark)) { Text("Clear") }
        }
    }
}
