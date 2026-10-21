package com.joyplayer.iptv

import java.text.Normalizer

private val marks = Regex("\\p{M}+")

fun normSearch(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(marks, "")
        .lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace('ı', 'i')
