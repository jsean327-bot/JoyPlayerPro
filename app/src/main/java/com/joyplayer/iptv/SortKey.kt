package com.joyplayer.iptv

import java.text.Normalizer

/**
 * Sorting by name that ignores what providers put in front of the real title:
 * country / language tags ("AR| ", "EN - ", "[4K] ", "|FR|"), symbols, emojis, accents and capital letters.
 * Numbers are ordered as numbers (beIN 2 comes before beIN 10).
 */
object SortKey {
    private val tag = Regex("""^\s*(?:[\[(|{][^\]|)}]{1,8}[\])|}]|[A-Za-z]{2}\s*[|:\-–—])\s*""")
    private val digits = Regex("""\d+""")
    private val marks = Regex("""\p{M}+""")

    fun of(name: String): String {
        var s = name
        repeat(3) {
            val t = s.replaceFirst(tag, "")
            if (t.isBlank()) return@repeat
            s = t
        }
        s = s.trimStart { !it.isLetterOrDigit() }
        s = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "")
        return s.replace(digits) { it.value.padStart(8, '0') }
    }
}

/** A→Z (or Z→A) by title; the sort key of each item is computed once. */
fun <T> List<T>.sortedByTitle(descending: Boolean, name: (T) -> String): List<T> {
    val keyed = map { SortKey.of(name(it)) to it }
    val sorted = if (descending) keyed.sortedByDescending { it.first } else keyed.sortedBy { it.first }
    return sorted.map { it.second }
}
