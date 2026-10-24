package com.joyplayer.iptv

import android.graphics.Typeface
import java.io.File

/**
 * Fonts for the subtitles: the font families every Android has (with bold / italic / light ... variants)
 * plus every font file found on the device itself (Arabic fonts such as Naskh / Kufi are listed first).
 */
object SubFonts {
    data class Entry(val id: String, val label: String)

    private val cache = HashMap<String, Typeface?>()
    private var all: List<Entry>? = null

    private val families = listOf(
        "Default" to "default",
        "Sans Serif" to "fam:sans-serif:0",
        "Sans Serif Bold" to "fam:sans-serif:1",
        "Sans Serif Italic" to "fam:sans-serif:2",
        "Sans Serif Bold Italic" to "fam:sans-serif:3",
        "Sans Serif Light" to "fam:sans-serif-light:0",
        "Sans Serif Light Italic" to "fam:sans-serif-light:2",
        "Sans Serif Thin" to "fam:sans-serif-thin:0",
        "Sans Serif Medium" to "fam:sans-serif-medium:0",
        "Sans Serif Medium Italic" to "fam:sans-serif-medium:2",
        "Sans Serif Black" to "fam:sans-serif-black:0",
        "Condensed" to "fam:sans-serif-condensed:0",
        "Condensed Bold" to "fam:sans-serif-condensed:1",
        "Condensed Italic" to "fam:sans-serif-condensed:2",
        "Condensed Light" to "fam:sans-serif-condensed-light:0",
        "Condensed Medium" to "fam:sans-serif-condensed-medium:0",
        "Small Caps" to "fam:sans-serif-smallcaps:0",
        "Serif" to "fam:serif:0",
        "Serif Bold" to "fam:serif:1",
        "Serif Italic" to "fam:serif:2",
        "Serif Bold Italic" to "fam:serif:3",
        "Monospace" to "fam:monospace:0",
        "Monospace Bold" to "fam:monospace:1",
        "Serif Monospace" to "fam:serif-monospace:0",
        "Casual" to "fam:casual:0",
        "Cursive" to "fam:cursive:0",
        "Cursive Bold" to "fam:cursive:1"
    )

    private val arabicHints = listOf("arab", "naskh", "kufi", "nastaliq", "amiri", "cairo", "tajawal", "droidsansarabic", "rtl")
    private val skip = listOf("emoji", "symbol", "icon", "flag", "material", "dingbat", "fallback", "color")

    private fun prettify(file: String): String {
        val base = file.substringBeforeLast('.')
        return base.replace('-', ' ').replace('_', ' ')
            .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").replace(Regex("\\s+"), " ").trim()
    }

    /** Built once, then kept: every choice offered in the font lists. */
    @Synchronized
    fun list(): List<Entry> {
        all?.let { return it }
        val out = ArrayList<Entry>()
        families.forEach { (label, id) -> out += Entry(id, label) }
        val dirs = listOf("/system/fonts", "/product/fonts", "/system_ext/fonts", "/vendor/fonts")
        val found = ArrayList<Entry>()
        val seen = HashSet<String>()
        for (d in dirs) {
            val files = runCatching { File(d).listFiles() }.getOrNull() ?: continue
            for (f in files) {
                val n = f.name
                val low = n.lowercase()
                if (!(low.endsWith(".ttf") || low.endsWith(".otf"))) continue
                if (skip.any { low.contains(it) }) continue
                val label = prettify(n)
                if (!seen.add(label.lowercase())) continue
                val arabic = arabicHints.any { low.contains(it) }
                found += Entry("file:" + f.absolutePath, (if (arabic) "ع  " else "") + label)
            }
        }
        // Arabic fonts first, then A-Z
        found.sortWith(compareBy<Entry>({ !it.label.startsWith("ع") }, { it.label.lowercase() }))
        out += found
        all = out
        return out
    }

    fun label(id: String): String = list().firstOrNull { it.id == id }?.label ?: "Default"

    /** null = the player's own font. */
    @Synchronized
    fun typeface(id: String): Typeface? {
        if (id == "default" || id.isBlank()) return null
        if (cache.containsKey(id)) return cache[id]
        val tf: Typeface? = runCatching {
            when {
                id.startsWith("fam:") -> {
                    val p = id.split(":")
                    Typeface.create(p[1], p.getOrNull(2)?.toIntOrNull() ?: 0)
                }
                id.startsWith("file:") -> Typeface.createFromFile(id.removePrefix("file:"))
                else -> null
            }
        }.getOrNull()
        cache[id] = tf
        return tf
    }
}
