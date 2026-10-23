package com.joyplayer.iptv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily

/** Look of the big buttons on the main screen (Live, Movies, Series, Account, Change Playlist). 0 = original look. */
object TileStyle {
    private var version by mutableStateOf(0)

    val fontNames = listOf("Default", "Sans Serif", "Serif", "Monospace", "Cursive")
    val fontFamilies = listOf(FontFamily.Default, FontFamily.SansSerif, FontFamily.Serif, FontFamily.Monospace, FontFamily.Cursive)
    val sizeNames = listOf("Small", "Normal", "Large", "Extra large")
    val scales = listOf(0.85f, 1f, 1.2f, 1.4f)

    val boxColors = listOf(
        0, 0xFF1E40AF.toInt(), 0xFF0F766E.toInt(), 0xFF6D28D9.toInt(), 0xFFB91C1C.toInt(),
        0xFFC2410C.toInt(), 0xFF15803D.toInt(), 0xFFBE185D.toInt(), 0xFF374151.toInt(), 0xFF000000.toInt()
    )
    val borderColors = listOf(
        0, 0xFFFFFFFF.toInt(), 0xFFFFD54F.toInt(), 0xFF00E5FF.toInt(), 0xFF69F0AE.toInt(),
        0xFFFF5252.toInt(), 0xFFFF80AB.toInt(), 0xFFB388FF.toInt(), 0xFF3B82F6.toInt()
    )
    val textColors = listOf(
        0, 0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF00E5FF.toInt(), 0xFF69F0AE.toInt(),
        0xFFFF8A80.toInt(), 0xFFFFCCBC.toInt(), 0xFFB388FF.toInt(), 0xFF000000.toInt()
    )

    private fun v(): Int = version

    var boxColor: Int
        get() { v(); return AppPrefs.tileBox }
        set(x) { AppPrefs.tileBox = x; version++ }
    var borderColor: Int
        get() { v(); return AppPrefs.tileBorder }
        set(x) { AppPrefs.tileBorder = x; version++ }
    var textColor: Int
        get() { v(); return AppPrefs.tileText }
        set(x) { AppPrefs.tileText = x; version++ }
    var font: Int
        get() { v(); return AppPrefs.tileFont.coerceIn(0, fontFamilies.size - 1) }
        set(x) { AppPrefs.tileFont = x; version++ }
    var textSize: Int
        get() { v(); return AppPrefs.tileTextSize.coerceIn(0, scales.size - 1) }
        set(x) { AppPrefs.tileTextSize = x; version++ }
    var iconSize: Int
        get() { v(); return AppPrefs.tileIconSize.coerceIn(0, scales.size - 1) }
        set(x) { AppPrefs.tileIconSize = x; version++ }

    fun reset() {
        AppPrefs.tileBox = 0; AppPrefs.tileBorder = 0; AppPrefs.tileText = 0
        AppPrefs.tileFont = 0; AppPrefs.tileTextSize = 1; AppPrefs.tileIconSize = 1
        version++
    }

    fun gradient(): List<Color> {
        val c = boxColor
        return if (c == 0) listOf(C.Burgundy, C.BurgundyDark)
        else Color(c).let { listOf(it, lerp(it, Color.Black, 0.5f)) }
    }
}
