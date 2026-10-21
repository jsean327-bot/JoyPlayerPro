package com.joyplayer.iptv

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.sp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.exoplayer.ExoPlayer

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

object PipState {
    var canEnter = false
    var inPip by mutableStateOf(false)

    fun supported(a: Activity): Boolean =
        Build.VERSION.SDK_INT >= 26 &&
                a.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun isEnabled(c: Context): Boolean =
        c.getSharedPreferences("joyplayer_settings", Context.MODE_PRIVATE).getBoolean("pip_enabled", true)

    fun setEnabled(c: Context, v: Boolean) {
        c.getSharedPreferences("joyplayer_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("pip_enabled", v).apply()
    }

    fun enter(a: Activity): Boolean {
        if (!supported(a)) return false
        return try {
            a.enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        } catch (e: Exception) {
            false
        }
    }

    fun request(c: Context) {
        val a = c.findActivity()
        if (a == null || !enter(a)) {
            Toast.makeText(c, "Picture-in-picture is not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun PipSupport(player: ExoPlayer) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        PipState.canEnter = true
        onDispose { PipState.canEnter = false; PipState.inPip = false }
    }
    DisposableEffect(ctx) {
        val act = ctx.findActivity() as? ComponentActivity
        val l = Consumer<PictureInPictureModeChangedInfo> { info ->
            PipState.inPip = info.isInPictureInPictureMode
        }
        act?.addOnPictureInPictureModeChangedListener(l)
        onDispose { act?.removeOnPictureInPictureModeChangedListener(l) }
    }
    DisposableEffect(owner, player) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) player.pause()
            if (e == Lifecycle.Event.ON_START) player.play()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

@Composable
fun PipDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(PipState.isEnabled(ctx)) }
    val act = ctx.findActivity()
    val ok = act != null && PipState.supported(act)
    SDialog("PIP Settings", onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(on, { on = it; PipState.setEnabled(ctx, it) })
            Text("  Keep playing in a small window when leaving the app", color = Color.White, fontSize = 14.sp)
        }
        Text(
            if (ok) "✓ Picture-in-picture is supported on this device."
            else "✗ This device does not support picture-in-picture.",
            color = if (ok) Color(0xFF8DE0A0) else Color(0xFFFF8A8A), fontSize = 13.sp
        )
        Text(
            "While a video plays, press Home or the PiP button in the player.",
            color = Color(0xFFEAF0FF), fontSize = 13.sp
        )
    }
}
