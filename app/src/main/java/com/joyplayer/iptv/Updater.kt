package com.joyplayer.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

// Change this if your GitHub user or repository name is different
const val UPDATE_REPO = "jsean327-bot/JoyPlayerPro"

data class UpdateInfo(val build: Int, val name: String, val notes: String, val url: String, val size: Long)

object Updater {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Suppress("DEPRECATION")
    fun installedBuild(ctx: Context): Int {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    }

    fun installedName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    fun latest(): UpdateInfo {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$UPDATE_REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "JoyPlayer")
            .build()
        client.newCall(req).execute().use { r ->
            if (r.code == 404) error("No release found. Is the GitHub repository public?")
            if (!r.isSuccessful) error("GitHub error (HTTP ${r.code})")
            val j = JSONObject(r.body?.string().orEmpty())
            val tag = j.getString("tag_name")
            val build = tag.filter { it.isDigit() }.toIntOrNull() ?: error("Unexpected release tag: $tag")
            val assets = j.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return UpdateInfo(
                        build, j.optString("name", tag), j.optString("body"),
                        a.getString("browser_download_url"), a.optLong("size")
                    )
                }
            }
            error("The release has no APK file")
        }
    }

    fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "JoyPlayer.apk")
        val req = Request.Builder().url(info.url).header("User-Agent", "JoyPlayer").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Download failed (HTTP ${r.code})")
            val body = r.body ?: error("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        return file
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openUnknownSources(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(
                ctx, "Enable 'Install unknown apps' for JoyPlayer in the system settings", Toast.LENGTH_LONG
            ).show()
        }
    }

    fun install(ctx: Context, apk: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not open the installer", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("checking") }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var msg by remember { mutableStateOf<String?>(null) }
    val cur = remember { Updater.installedBuild(ctx) }
    val curName = remember { Updater.installedName(ctx) }

    fun runCheck() {
        scope.launch {
            state = "checking"; msg = null
            try {
                val i = withContext(Dispatchers.IO) { Updater.latest() }
                info = i
                state = if (i.build > cur) "available" else "latest"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                msg = e.message ?: "Error"; state = "error"
            }
        }
    }

    fun startDownload() {
        val i = info ?: return
        scope.launch {
            state = "downloading"; progress = 0f; msg = null
            try {
                val f = withContext(Dispatchers.IO) { Updater.download(ctx, i) { progress = it } }
                apk = f
                if (Updater.canInstall(ctx)) {
                    Updater.install(ctx, f)
                    state = "available"
                } else {
                    Updater.openUnknownSources(ctx)
                    msg = "Allow installs from JoyPlayer, come back here and press Install."
                    state = "ready"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                msg = e.message ?: "Error"; state = "error"
            }
        }
    }

    LaunchedEffect(Unit) { runCheck() }

    SDialog("Update Now", onDismiss) {
        Text("Installed: $curName (build $cur)", color = Color.White, fontSize = 14.sp)
        when (state) {
            "checking" -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Checking for updates...", color = Color(0xFFEAF0FF), fontSize = 13.sp)
            }
            "latest" -> {
                Text("✓ You are up to date.", color = Color(0xFF8DE0A0), fontSize = 14.sp)
                Button(onClick = { runCheck() }) { Text("Check again") }
            }
            "available" -> {
                info?.let { i ->
                    Text("New version: ${i.name} (build ${i.build})", color = Color(0xFFFFD54F), fontSize = 14.sp)
                    if (i.notes.isNotBlank()) Text(i.notes.take(200), color = Color(0xFFEAF0FF), fontSize = 12.sp)
                    Button(onClick = { startDownload() }) { Text("Download & install") }
                }
            }
            "downloading" -> {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("Downloading... ${(progress * 100).toInt()}%", color = Color(0xFFEAF0FF), fontSize = 13.sp)
            }
            "ready" -> {
                msg?.let { Text(it, color = Color(0xFFFFD54F), fontSize = 13.sp) }
                Button(onClick = { apk?.let { Updater.install(ctx, it) } }) { Text("Install now") }
            }
            else -> {
                msg?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp) }
                Button(onClick = { runCheck() }) { Text("Retry") }
            }
        }
    }
}
