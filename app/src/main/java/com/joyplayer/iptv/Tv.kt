package com.joyplayer.iptv

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Remote-control (D-pad) focus look for Fire TV / Android TV.
 * Every clickable item gets a blue frame + light tint while it is focused,
 * and a light tint while it is pressed (touch). Installed once in MainActivity.
 */
object TvIndication : Indication {
    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
        val focused = interactionSource.collectIsFocusedAsState()
        val pressed = interactionSource.collectIsPressedAsState()
        return remember(interactionSource) { TvIndicationInstance(focused, pressed) }
    }
}

private class TvIndicationInstance(
    private val focused: State<Boolean>,
    private val pressed: State<Boolean>
) : IndicationInstance {
    override fun ContentDrawScope.drawIndication() {
        drawContent()
        val r = CornerRadius(8.dpPx(this), 8.dpPx(this))
        if (focused.value) {
            drawRoundRect(Color(0x33FFFFFF), size = size, cornerRadius = r)
            val w = 3.dpPx(this)
            drawRoundRect(
                Color(0xFF3B82F6),
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                cornerRadius = r,
                style = Stroke(width = w)
            )
        } else if (pressed.value) {
            drawRoundRect(Color(0x22FFFFFF), size = size, cornerRadius = r)
        }
    }
}

private fun Int.dpPx(s: ContentDrawScope): Float = this * s.density
