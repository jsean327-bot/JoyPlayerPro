package com.joyplayer.iptv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AccountInfo(
    val status: String,
    val expiry: Long?,          // epoch seconds, null = unlimited
    val trial: Boolean,
    val created: Long?,
    val activeCons: String,
    val maxCons: String,
    val server: String
)

object AccountApi {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun JsonObject.str(k: String): String =
        runCatching { get(k).asString }.getOrDefault("")

    fun load(l: SavedList): AccountInfo {
        var base = l.server.trim()
        if (base.isEmpty()) error("This playlist is not an Xtream login")
        if (!base.startsWith("http")) base = "http://$base"
        if (!base.endsWith("/")) base += "/"
        val req = Request.Builder().url(
            "${base}player_api.php?username=${URLEncoder.encode(l.user, "UTF-8")}" +
                    "&password=${URLEncoder.encode(l.pass, "UTF-8")}"
        ).header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val root = JsonParser.parseString(r.body?.string().orEmpty()).asJsonObject
            val u = root.getAsJsonObject("user_info") ?: error("No account info from server")
            val s = root.getAsJsonObject("server_info")
            val exp = u.str("exp_date").toLongOrNull()
            val status = u.str("status").ifBlank { if (u.str("auth") == "1") "Active" else "Unknown" }
            AccountInfo(
                status = status,
                expiry = if (exp == null || exp <= 0L) null else exp,
                trial = u.str("is_trial") == "1",
                created = u.str("created_at").toLongOrNull(),
                activeCons = u.str("active_cons").ifBlank { "0" },
                maxCons = u.str("max_connections").ifBlank { "-" },
                server = s?.let { o ->
                    val url = o.str("url"); val port = o.str("port")
                    if (url.isBlank()) "" else if (port.isBlank()) url else "$url:$port"
                } ?: ""
            )
        }
    }
}

private fun fmtDay(sec: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(sec * 1000))

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0x44000000))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = C.Text, fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AccountScreen(list: SavedList?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var info by remember(list?.id) { mutableStateOf<AccountInfo?>(null) }
    var error by remember(list?.id) { mutableStateOf<String?>(null) }
    var loading by remember(list?.id) { mutableStateOf(list != null) }
    var tick by remember { mutableStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    var report by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    if (report != null) {
        SDialog("Connection test", { if (!testing) report = null }, maxHeight = 320.dp) {
            Text(report ?: "", color = Color.White, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            if (testing) CircularProgressIndicator()
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    clip.setText(androidx.compose.ui.text.AnnotatedString(report ?: ""))
                    android.widget.Toast.makeText(ctx, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Copy report") }
                Button(onClick = { report = null }) { Text("Close") }
            }
        }
    }

    LaunchedEffect(list?.id, tick) {
        if (list == null) return@LaunchedEffect
        loading = true; error = null
        try {
            info = withContext(Dispatchers.IO) { AccountApi.load(list) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not load account info"
        }
        loading = false
    }

    Column(
        Modifier.fillMaxSize()
            .screenBg()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "↩", fontSize = 30.sp, color = Color.White,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 16.dp)
            )
            Text("JoyPlayer", color = C.Blue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("  |  Account", color = Color.White, fontSize = 22.sp)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (list == null) {
                Text("No playlist connected. Add one from Change Playlist.", color = Color(0xFFFF8A8A))
            } else {
                InfoRow("Playlist", list.name)
                InfoRow("Username", list.user.ifBlank { "-" })
                if (loading) CircularProgressIndicator()
                error?.let { Text(it, color = Color(0xFFFF8A8A)) }
                info?.let { a ->
                    val now = System.currentTimeMillis() / 1000
                    val left = a.expiry?.let { (it - now) / 86400 }
                    val expired = a.expiry != null && a.expiry < now
                    val statusOk = a.status.equals("Active", true) && !expired
                    InfoRow(
                        "Status", if (expired) "Expired" else a.status,
                        if (statusOk) Color(0xFF6EE7A0) else Color(0xFFFF8A8A)
                    )
                    InfoRow(
                        "Expiry date",
                        a.expiry?.let { fmtDay(it) } ?: "Unlimited",
                        if (expired) Color(0xFFFF8A8A) else Color.White
                    )
                    if (left != null) {
                        InfoRow(
                            "Days left", if (left < 0) "0" else "$left",
                            when {
                                left < 0 -> Color(0xFFFF8A8A)
                                left <= 7 -> Color(0xFFFFB74D)
                                else -> Color(0xFF6EE7A0)
                            }
                        )
                    }
                    InfoRow("Max connections", a.maxCons)
                    InfoRow("Active connections", a.activeCons)
                    InfoRow("Trial", if (a.trial) "Yes" else "No")
                    a.created?.let { InfoRow("Created", fmtDay(it)) }
                    if (a.server.isNotBlank()) InfoRow("Server", a.server)
                }
            }
        }
        if (list != null) {
            Button(
                onClick = { tick++ },
                colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy)
            ) { Text("⟳  Refresh") }
            Button(
                onClick = {
                    if (!testing) {
                        testing = true; report = "…"
                        scope.launch {
                            try {
                                report = Diag.run(ctx, list) { report = it }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                report = "Test failed: ${e.message}"
                            }
                            testing = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = C.Blue)
            ) { Text("🩺  Test connection") }
        }
    }
}
