package com.rork.tasktitan.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.rork.tasktitan.data.AvatarLook
import com.rork.tasktitan.data.EnergyTier
import com.rork.tasktitan.ui.theme.TT
import kotlin.math.min

/** Spinning 3D avatar on a pedestal. Drag sideways to turn it. */
@Composable
fun AvatarView(
    look: AvatarLook,
    tier: EnergyTier,
    modifier: Modifier = Modifier,
    pose: Pose = Pose.IDLE,
    spin: Boolean = true,
    pedestal: Color = Color(0xFFFFE9D2),
    description: String = "Your 3D avatar",
) {
    val reduced = TT.reducedMotion
    val renderer = remember { Renderer() }
    var yaw by remember { mutableFloatStateOf(0.4f) }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(reduced, spin) {
        if (reduced) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                if (spin) yaw += dt * 0.5f
                frame = now
            }
        }
    }

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, drag ->
                    yaw += drag * 0.012f
                    frame++
                }
            }
    ) {
        @Suppress("UNUSED_VARIABLE") val tick = frame
        val t = if (reduced) 0f else (System.currentTimeMillis() % 100_000L) / 1000f
        val mb = MeshBuilder()
        mb.layer = 0
        mb.box(-0.5f, -0.18f, -0.5f, 0.5f, 0f, 0.5f, pedestal.copy(red = pedestal.red * 0.95f, green = pedestal.green * 0.9f), pedestal)
        mb.layer = 1
        buildAvatar(mb, look, tier, 0f, 0f, 0f, yaw, pose, t, 1f, withPet = true)
        val cam = Camera(0f, 0.28f, 8f, 0f, 0.55f, 0f, 1f)
        renderer.draw(this, mb.faces, cam, min(size.width, size.height) * 0.62f)
    }
}
