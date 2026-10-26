package com.joyplayer.iptv

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Remembers where you stopped in movies / episodes (position, last-watched time, finished flag, title). */
object ResumeStore {
    private var prefs: SharedPreferences? = null

    /** Bumped on every save so screens showing Resume / Continue labels refresh. */
    var version by mutableStateOf(0)

    fun init(ctx: Context) {
        if (prefs == null) prefs = ctx.applicationContext.getSharedPreferences("joyplayer_resume", Context.MODE_PRIVATE)
    }

    private const val SEP = '\u001F'

    private class Entry(
        val pos: Long, val dur: Long, val ts: Long, val done: Boolean,
        val name: String, val logo: String
    )

    private fun parse(raw: String?): Entry? {
        if (raw == null) return null
        val p = raw.split("|", limit = 5)
        if (p.size < 4) return null
        val extra = p.getOrNull(4).orEmpty().split(SEP)
        return Entry(
            p[0].toLongOrNull() ?: 0L, p[1].toLongOrNull() ?: 0L,
            p[2].toLongOrNull() ?: 0L, p[3] == "1",
            extra.getOrNull(0).orEmpty(), extra.getOrNull(1).orEmpty()
        )
    }

    private fun read(url: String): Entry? =
        parse(prefs?.getString(Creds.strip(url), null) ?: prefs?.getString(url, null))

    /** Moves positions saved with a full link (old versions) to the password-free key. */
    fun migrate() {
        val pr = prefs ?: return
        val ed = pr.edit()
        var any = false
        for ((k, v) in pr.all) {
            val nk = Creds.strip(k)
            if (nk != k && v is String) { ed.remove(k); ed.putString(nk, v); any = true }
        }
        if (any) ed.apply()
    }

    private fun clean(s: String?): String = (s ?: "").replace("|", " ").replace(SEP, ' ')

    /** Saved position in ms, or 0 when nothing to resume. */
    fun get(url: String): Long {
        val e = read(url) ?: return 0L
        return if (e.done) 0L else e.pos
    }

    /** Last time this item was watched (0 = never). */
    fun lastWatched(url: String): Long = read(url)?.ts ?: 0L

    fun isDone(url: String): Boolean = read(url)?.done == true

    fun save(url: String, pos: Long, dur: Long, name: String = "", logo: String? = null, notify: Boolean = true) {
        val pr = prefs ?: return
        if (dur <= 0L || pos < 0L) return
        val key = Creds.strip(url)
        val now = System.currentTimeMillis()
        val old = read(url)
        val nm = clean(name).ifBlank { old?.name.orEmpty() }
        val lg = clean(logo).ifBlank { old?.logo.orEmpty() }
        val tail = "$nm$SEP$lg"
        val finished = pos >= dur * 95 / 100
        when {
            finished -> pr.edit().putString(key, "0|$dur|$now|1|$tail").apply()
            pos > 10_000L -> pr.edit().putString(key, "$pos|$dur|$now|0|$tail").apply()
            else -> return
        }
        prune(pr)
        if (notify) version++
    }

    class Recent(val url: String, val name: String, val logo: String?, val pos: Long, val dur: Long)

    /** Unfinished movies / episodes, most recently watched first. */
    fun recent(limit: Int = 12): List<Recent> {
        val all = prefs?.all ?: return emptyList()
        return all.entries.mapNotNull { (k, v) ->
            val e = parse(v as? String) ?: return@mapNotNull null
            if (e.done || e.pos <= 0L || e.name.isBlank()) null else Triple(k, e, e.ts)
        }.sortedByDescending { it.third }.take(limit).map { (k, e, _) ->
            Recent(Creds.fill(k), e.name, e.logo.ifBlank { null }, e.pos, e.dur)
        }
    }

    private fun prune(pr: SharedPreferences) {
        val all = pr.all
        if (all.size <= 400) return
        val oldest = all.entries
            .map { it.key to ((it.value as? String)?.split("|", limit = 5)?.getOrNull(2)?.toLongOrNull() ?: 0L) }
            .sortedBy { it.second }.take(100)
        val ed = pr.edit()
        oldest.forEach { ed.remove(it.first) }
        ed.apply()
    }

    fun fmt(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}

/** The episode list of the series being watched, so the next episode can start by itself. */
object PlayQueue {
    var items: List<Pair<Channel, SubMeta>> = emptyList()
    var play: ((Channel) -> Unit)? = null

    fun clear() { items = emptyList() }

    fun nextAfter(url: String): Pair<Channel, SubMeta>? {
        val i = items.indexOfFirst { it.first.url == url }
        return if (i >= 0) items.getOrNull(i + 1) else null
    }

    /** Where "Watch Now" should continue: the last watched episode, or the one after it if it was finished. */
    fun continuePoint(): Pair<Channel, SubMeta>? {
        val last = items.filter { ResumeStore.lastWatched(it.first.url) > 0L }
            .maxByOrNull { ResumeStore.lastWatched(it.first.url) } ?: return null
        return if (ResumeStore.isDone(last.first.url)) (nextAfter(last.first.url) ?: last) else last
    }
}
