package com.joyplayer.iptv

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI helpers (subtitle translation, smart search). They talk to your own small proxy (Cloudflare Worker),
 * so no AI key is ever stored inside the app. Nothing happens until a proxy address is saved in Settings → AI Assistant.
 */
object AiConfig {
    private var prefs: android.content.SharedPreferences? = null
    private fun p(ctx: Context) = prefs ?: ctx.applicationContext.getSharedPreferences("joyplayer_ai", Context.MODE_PRIVATE).also { prefs = it }
    var url by mutableStateOf("")
        private set
    var token by mutableStateOf("")
        private set
    val enabled: Boolean get() = url.startsWith("http")

    fun init(ctx: Context) {
        val sp = p(ctx)
        url = sp.getString("url", "") ?: ""
        token = sp.getString("token", "") ?: ""
    }

    fun save(ctx: Context, u: String, t: String) {
        url = u.trim(); token = t.trim()
        p(ctx).edit().putString("url", url).putString("token", token).apply()
    }
}

object Ai {
    private val client by lazy { Http.client.newBuilder().callTimeout(100, TimeUnit.SECONDS).readTimeout(100, TimeUnit.SECONDS).build() }
    private val json = "application/json; charset=utf-8".toMediaType()

    fun langName(code: String): String = when (code.uppercase()) {
        "AR" -> "Arabic"; "EN" -> "English"; "FR" -> "French"; "TR" -> "Turkish"
        "ES" -> "Spanish"; "DE" -> "German"; "IT" -> "Italian"; else -> "Arabic"
    }

    private fun call(body: JSONObject): JSONObject {
        val req = Request.Builder().url(AiConfig.url)
            .header("x-token", AiConfig.token)
            .post(body.toString().toRequestBody(json)).build()
        client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val o = try { JSONObject(text) } catch (e: Exception) { error("AI server answer not understood (HTTP ${r.code})") }
            if (!r.isSuccessful || o.has("error")) error(o.optString("error", "AI server error (HTTP ${r.code})"))
            return o
        }
    }

    suspend fun ping(): String = withContext(Dispatchers.IO) {
        call(JSONObject().put("op", "ping")); "OK"
    }

    suspend fun searchTerms(q: String): List<String> = withContext(Dispatchers.IO) {
        val a = call(JSONObject().put("op", "search").put("q", q)).optJSONArray("terms") ?: JSONArray()
        (0 until a.length()).map { a.getString(it) }.filter { it.isNotBlank() }
    }

    private class Block(val raw: String?, val header: String, val text: String)

    private fun parse(src: String): List<Block> =
        src.replace("\r\n", "\n").replace('\r', '\n').trim().split(Regex("\n\\s*\n")).map { b ->
            val lines = b.split("\n")
            val i = lines.indexOfFirst { "-->" in it }
            if (i < 0) Block(b, "", "") else Block(null, lines.take(i + 1).joinToString("\n"), lines.drop(i + 1).joinToString("\n"))
        }

    /** Translates a whole SRT / VTT text. Timing is untouched. [onProgress] gets (done, total) cues. */
    suspend fun translateSub(src: String, to: String, onProgress: (Int, Int) -> Unit): String {
        val blocks = parse(src)
        val idx = blocks.indices.filter { blocks[it].raw == null && blocks[it].text.isNotBlank() }
        if (idx.isEmpty()) error("No subtitle lines found")
        val out = HashMap<Int, String>()
        var done = 0
        val chunks = idx.chunked(50)
        val gate = Semaphore(3)
        coroutineScope {
            chunks.map { ch ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val lines = JSONArray().also { a -> ch.forEach { a.put(blocks[it].text) } }
                        var res: List<String>? = null
                        for (attempt in 1..2) {
                            try {
                                val o = call(JSONObject().put("op", "translate").put("to", to).put("lines", lines))
                                val a = o.getJSONArray("lines")
                                if (a.length() == ch.size) { res = (0 until a.length()).map { a.getString(it) }; break }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (attempt == 2 && ch === chunks.first()) throw e   // fail fast if the server is not working at all
                            }
                        }
                        synchronized(out) {
                            ch.forEachIndexed { k, bi -> out[bi] = res?.get(k) ?: blocks[bi].text }   // a failed chunk keeps the original text
                            done += ch.size
                            onProgress(done, idx.size)
                        }
                    }
                }
            }.awaitAll()
        }
        return blocks.mapIndexed { i, b ->
            if (b.raw != null) b.raw else b.header + "\n" + (out[i] ?: b.text)
        }.joinToString("\n\n") + "\n"
    }
}

@Composable
fun AiDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(AiConfig.url) }
    var token by remember { mutableStateOf(AiConfig.token) }
    var msg by remember { mutableStateOf<String?>(null) }
    SDialog("AI Assistant", onDismiss, maxHeight = 420.dp) {
        Text(
            "Subtitle translation and smart search use your own AI proxy address. Leave empty to turn AI off.",
            color = C.Text, fontSize = 13.sp
        )
        OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Proxy address (https://...)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it }, singleLine = true, label = { Text("Password (APP_TOKEN)") }, modifier = Modifier.fillMaxWidth())
        msg?.let { Text(it, color = Color(0xFFFFD54F), fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                AiConfig.save(ctx, url, token)
                scope.launch {
                    msg = "Testing..."
                    msg = try { if (AiConfig.enabled) "✓ " + Ai.ping() else "AI is off" }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { "✗ " + (e.message ?: "Error") }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = C.Burgundy)) { Text("Save & Test") }
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = C.BurgundyDark)) { Text("Close") }
        }
    }
}
