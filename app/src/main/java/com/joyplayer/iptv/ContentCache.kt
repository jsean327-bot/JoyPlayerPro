package com.joyplayer.iptv

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Keeps the downloaded Live / Movies / Series lists on the phone so the app can open without
 * downloading everything again. How long a copy stays fresh is the "Automatic" setting:
 * 0 = every time (always download), 1 = every day, 2 = after 2 days.
 */
object ContentCache {
    private var dir: File? = null
    private val gson = Gson()
    private val chType = object : TypeToken<List<Channel>>() {}.type
    private val vodType = object : TypeToken<List<LibItem>>() {}.type

    fun init(ctx: Context) {
        if (dir == null) dir = File(ctx.applicationContext.filesDir, "content_cache").apply { mkdirs() }
    }

    private fun sig(l: SavedList): String =
        Integer.toHexString((l.url + "|" + l.server + "|" + l.user + "|" + l.pass).hashCode())

    private fun file(kind: String, l: SavedList): File? =
        dir?.let { File(it, "${kind}_${l.id}_${sig(l)}.json") }

    private fun maxAgeMs(): Long = when (AppPrefs.autoUpdate) {
        1 -> 24L * 3600_000L
        2 -> 48L * 3600_000L
        else -> 0L
    }

    private fun <T> read(kind: String, l: SavedList, type: java.lang.reflect.Type, ignoreAge: Boolean): T? {
        val f = file(kind, l) ?: return null
        if (!f.exists()) return null
        if (!ignoreAge) {
            val max = maxAgeMs()
            if (max <= 0L || System.currentTimeMillis() - f.lastModified() > max) return null
        }
        return try {
            val r: T? = f.reader(Charsets.UTF_8).use { gson.fromJson<T>(it, type) }
            r
        } catch (e: Exception) {
            null
        }
    }

    private fun write(kind: String, l: SavedList, data: Any, type: java.lang.reflect.Type) {
        val f = file(kind, l) ?: return
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            // streamed straight into the file: no giant string in memory (that ran out of memory on TV boxes)
            tmp.writer(Charsets.UTF_8).buffered(64 * 1024).use { w -> gson.toJson(data, type, w) }
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        } catch (e: Exception) {
            // cache is best-effort
        }
    }

    /** True when the stored copy is still younger than the "Automatic" setting (0 = never fresh: refresh every session). */
    fun isFresh(kind: String, l: SavedList): Boolean {
        val f = file(kind, l) ?: return false
        val max = maxAgeMs()
        return f.exists() && max > 0L && System.currentTimeMillis() - f.lastModified() <= max
    }

    fun channels(l: SavedList, ignoreAge: Boolean = false): List<Channel>? =
        read<List<Channel>>("live", l, chType, ignoreAge)?.takeIf { it.isNotEmpty() }

    fun putChannels(l: SavedList, list: List<Channel>) {
        if (list.isNotEmpty()) write("live", l, list, chType)
    }

    fun vod(l: SavedList, series: Boolean, ignoreAge: Boolean = false): List<LibItem>? =
        read<List<LibItem>>(if (series) "series" else "movies", l, vodType, ignoreAge)?.takeIf { it.isNotEmpty() }

    fun putVod(l: SavedList, series: Boolean, list: List<LibItem>) {
        if (list.isNotEmpty()) write(if (series) "series" else "movies", l, list, vodType)
    }

    /** Forget everything stored for one playlist (used by Reload). */
    fun clear(listId: String) {
        dir?.listFiles()?.forEach { if (it.name.contains("_${listId}_")) it.delete() }
    }
}
