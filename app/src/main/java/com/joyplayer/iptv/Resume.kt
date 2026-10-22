package com.joyplayer.iptv

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Remembers where you stopped in movies / episodes (position, last-watched time, finished flag). */
object ResumeStore {
    private var prefs: SharedPreferences? = null

    /** Bumped on every save so screens showing Resume / Continue labels refresh. */
    var version by mutableStateOf(0)

    fun init(ctx: Context) {
        if (prefs == null) prefs = ctx.applicationContext.getSharedPreferences("joyplayer_resume", Context.MODE_PRIVATE)
    }

    private class Entry(val pos: Long, val dur: Long, val ts: Long, val done: Boolean)

    private fun read(url: String): Entry? {
        val raw = prefs?.getString(url, null) ?: return null
        val p = raw.split("|")
        if (p.size < 4) return null
        return Entry(
            p[0].toLongOrNull() ?: 0L, p[1].toLongOrNull() ?: 0L,
            p[2].toLongOrNull() ?: 0L, p[3] == "1"
        )
    }

    /** Saved position in ms, or 0 when nothing to resume. */
    fun get(url: String): Long {
        val e = read(url) ?: return 0L
        return if (e.done) 0L else e.pos
    }

    /** Last time this item was watched (0 = never). */
    fun lastWatched(url: String): Long = read(url)?.ts ?: 0L

    fun isDone(url: String): Boolean = read(url)?.done == true

    fun save(url: String, pos: Long, dur: Long) {
        val pr = prefs ?: return
        if (dur <= 0L || pos < 0L) return
        val now = System.currentTimeMillis()
        val finished = pos >= dur * 95 / 100
        when {
            finished -> pr.edit().putString(url, "0|$dur|$now|1").apply()
            pos > 10_000L -> pr.edit().putString(url, "$pos|$dur|$now|0").apply()
            else -> return
        }
        prune(pr)
        version++
    }

    private fun prune(pr: SharedPreferences) {
        val all = pr.all
        if (all.size <= 400) return
        val oldest = all.entries
            .map { it.key to ((it.value as? String)?.split("|")?.getOrNull(2)?.toLongOrNull() ?: 0L) }
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
