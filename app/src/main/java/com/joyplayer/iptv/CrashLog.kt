package com.joyplayer.iptv

import android.content.Context
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** If the app crashes, the reason is saved and shown on the next start (so it can be photographed and sent). */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                file(app).writeText(
                    "Thread: ${t.name}\n" + android.util.Log.getStackTraceString(e).take(3500)
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun pending(ctx: Context): String? =
        try { file(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } } catch (_: Throwable) { null }

    fun clear(ctx: Context) {
        try { file(ctx).delete() } catch (_: Throwable) { }
    }
}

@Composable
fun CrashNotice() {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(CrashLog.pending(ctx)) }
    val t = text ?: return
    AlertDialog(
        onDismissRequest = { CrashLog.clear(ctx); text = null },
        title = { Text("The app closed unexpectedly last time") },
        text = {
            Text(
                t, fontSize = 10.sp,
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
            )
        },
        confirmButton = { TextButton(onClick = { CrashLog.clear(ctx); text = null }) { Text("OK") } }
    )
}
