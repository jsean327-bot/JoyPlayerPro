package com.joyplayer.iptv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Backgrounds for the main screen (drawn with gradients, so they cost no storage and fit any screen). */
object Wallpapers {
    class W(val name: String, val top: Color, val bottom: Color, val g1: Color, val g2: Color)

    val list = listOf(
        W("Classic", Color(0xFF0E1A4A), Color(0xFF0B1330), Color(0x00000000), Color(0x00000000)),
        W("Deep Blue", Color(0xFF0A1F6B), Color(0xFF050B2B), Color(0xAA2F6BFF), Color(0x883B2BD9)),
        W("Violet Dunes", Color(0xFF2A0F6E), Color(0xFF0A0420), Color(0xAAB44BFF), Color(0x88FF6FB5)),
        W("Midnight", Color(0xFF0B0820), Color(0xFF020108), Color(0x88A58BFF), Color(0x446A4BD0)),
        W("Burgundy", Color(0xFF4A1226), Color(0xFF12050C), Color(0xAAD93A6A), Color(0x66FF8A5C)),
        W("Aurora", Color(0xFF062B2B), Color(0xFF020A12), Color(0xAA2BE0B0), Color(0x883B7BFF)),
        W("Ocean", Color(0xFF05355E), Color(0xFF020B1A), Color(0xAA23B5F0), Color(0x664B5BFF)),
        W("Sunset", Color(0xFF4B1A5E), Color(0xFF1A0715), Color(0xAAFF7A3D), Color(0x88FF3D7A)),
        W("Graphite", Color(0xFF2A2D33), Color(0xFF0A0B0D), Color(0x66AAB4C8), Color(0x33FFFFFF)),
        W("Ember", Color(0xFF3A1606), Color(0xFF0E0502), Color(0xAAFF8A1F), Color(0x66FF4D1F)),
        W("Rose", Color(0xFF3F0B2A), Color(0xFF12030C), Color(0xAAFF4D9A), Color(0x66B84DFF)),
        W("Black", Color(0xFF000000), Color(0xFF000000), Color(0x00000000), Color(0x00000000))
    )

    private var cur by mutableStateOf(-1)

    /** Index of the chosen background (saved). */
    fun current(): Int = (if (cur < 0) AppPrefs.wallpaper else cur).coerceIn(0, list.size - 1)

    fun set(i: Int) {
        cur = i
        AppPrefs.wallpaper = i
    }
}

/** Paints background number [i] (default: the chosen one). */
@Composable
fun Modifier.wallpaper(i: Int = Wallpapers.current()): Modifier {
    val w = Wallpapers.list[i.coerceIn(0, Wallpapers.list.size - 1)]
    return this
        .background(Brush.verticalGradient(listOf(w.top, w.bottom)))
        .drawBehind {
            if (w.g1.alpha > 0f) {
                val r = size.maxDimension * 0.65f
                drawRect(
                    Brush.radialGradient(
                        listOf(w.g1, Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.28f), radius = r
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(w.g2, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.85f), radius = r * 0.9f
                    )
                )
            }
        }
}
