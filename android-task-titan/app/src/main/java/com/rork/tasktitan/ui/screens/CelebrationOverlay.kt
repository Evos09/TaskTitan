package com.rork.tasktitan.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.tasktitan.ui.Celebration
import com.rork.tasktitan.ui.theme.TT
import kotlin.random.Random

private data class Confetti(val x: Float, val delay: Float, val speed: Float, val size: Float, val color: Color, val spin: Float)

/** Milestone celebration with confetti. Confetti is skipped when reduced motion is on. */
@Composable
fun CelebrationOverlay(cel: Celebration, onDismiss: () -> Unit) {
    val c = TT.c
    val reduced = TT.reducedMotion
    val haptics = LocalHapticFeedback.current
    val pop = remember(cel.id) { Animatable(if (reduced) 1f else 0.6f) }
    val fall = remember(cel.id) { Animatable(0f) }
    val pieces = remember(cel.id) {
        val colors = listOf(c.tangerine, c.sky, c.bubblegum, c.sun, c.mint)
        List(if (cel.big) 70 else 36) { Confetti(Random.nextFloat(), Random.nextFloat() * 0.35f, 0.6f + Random.nextFloat() * 0.8f, 8f + Random.nextFloat() * 10f, colors.random(), Random.nextFloat() * 720f) }
    }
    LaunchedEffect(cel.id) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!reduced) {
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        }
    }
    LaunchedEffect(cel.id) {
        if (!reduced) fall.animateTo(1f, tween(2600, easing = LinearEasing))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x8814254A))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        if (!reduced) {
            Canvas(Modifier.fillMaxSize()) {
                val t = fall.value
                for (p in pieces) {
                    val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                    if (local <= 0f) continue
                    val y = -40f + local * p.speed * size.height * 1.2f
                    val x = p.x * size.width + kotlin.math.sin(local * 12f + p.spin) * 24f
                    rotate(p.spin * local, Offset(x, y)) {
                        drawRect(p.color, Offset(x - p.size / 2, y - p.size / 4), Size(p.size, p.size / 2))
                    }
                }
            }
        }
        Column(
            Modifier
                .padding(32.dp)
                .scale(pop.value)
                .clip(RoundedCornerShape(32.dp))
                .background(c.surface)
                .padding(26.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(if (cel.big) c.tangerineSoft else c.mintSoft), contentAlignment = Alignment.Center) {
                Icon(if (cel.big) Icons.Rounded.EmojiEvents else Icons.Rounded.AutoAwesome, null, tint = if (cel.big) c.tangerine else c.mint, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(cel.title, style = MaterialTheme.typography.headlineMedium, color = c.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(cel.message, style = MaterialTheme.typography.bodyLarge, color = c.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = c.tangerine),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Yay!", style = MaterialTheme.typography.labelLarge) }
        }
    }
}
