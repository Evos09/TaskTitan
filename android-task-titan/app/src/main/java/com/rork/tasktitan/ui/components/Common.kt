package com.rork.tasktitan.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.tasktitan.data.Catalog
import com.rork.tasktitan.data.GameRules
import com.rork.tasktitan.data.Member
import com.rork.tasktitan.data.RoomType
import com.rork.tasktitan.ui.Stats
import com.rork.tasktitan.ui.theme.Display
import com.rork.tasktitan.ui.theme.TT

fun roomIcon(type: RoomType): ImageVector = when (type) {
    RoomType.KITCHEN -> Icons.Rounded.Kitchen
    RoomType.LIVING -> Icons.Rounded.Weekend
    RoomType.DINING -> Icons.Rounded.TableRestaurant
    RoomType.BEDROOM -> Icons.Rounded.Bed
    RoomType.BATHROOM -> Icons.Rounded.Bathtub
    RoomType.LAUNDRY -> Icons.Rounded.LocalLaundryService
    RoomType.TOY -> Icons.Rounded.Toys
    RoomType.OFFICE -> Icons.Rounded.Computer
    RoomType.GARAGE -> Icons.Rounded.DirectionsCar
    RoomType.CUSTOM -> Icons.Rounded.MeetingRoom
}

fun roomTint(type: RoomType): Color = Color(Catalog.roomColors[type] ?: 0xFFF1EDE6)
fun roomAccent(type: RoomType): Color = Color(Catalog.roomAccents[type] ?: 0xFF8A6D4C)

/** Big soft rounded card with no hard border. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = TT.c.surface,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, color = color, shape = RoundedCornerShape(28.dp), shadowElevation = 0.dp, tonalElevation = 0.dp) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** Rounded square icon tile used throughout the app. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, background: Color, size: Dp = 44.dp, contentDescription: String? = null) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** Effort weight shown as filled dots plus a text label (never color-only). */
@Composable
fun WeightDots(weight: Int, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val c = TT.c
    val color = when (weight) { 1 -> c.sky; 2 -> c.mint; 3 -> c.tangerine; else -> c.bubblegum }
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Effort ${GameRules.weightLabel(weight)}, $weight of 5" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLabel) {
            Text(GameRules.weightLabel(weight), style = MaterialTheme.typography.labelMedium, color = c.muted)
            Spacer(Modifier.width(6.dp))
        }
        repeat(minOf(weight, 5)) {
            Box(Modifier.padding(horizontal = 1.5.dp).size(9.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
fun MemberAvatar(member: Member, size: Dp = 40.dp, ring: Boolean = true) {
    val color = Color(Catalog.memberColors[member.colorIndex % Catalog.memberColors.size])
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (ring) color else Color.Transparent)
            .padding(if (ring) 2.5.dp else 0.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.45f))))
            .background(Color.White.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            member.name.take(1).uppercase(),
            fontFamily = Display,
            fontSize = (size.value * 0.42f).sp,
            color = color.copy(red = color.red * 0.75f, green = color.green * 0.75f, blue = color.blue * 0.75f),
        )
    }
}

/** Animated rounded progress bar. */
@Composable
fun FatProgress(progress: Float, color: Color, track: Color, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val animated = animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = if (TT.reducedMotion) tween(0) else spring(dampingRatio = 0.7f, stiffness = 120f), label = "progress")
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(
            Modifier
                .fillMaxWidth(animated.value)
                .height(height)
                .clip(RoundedCornerShape(height))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.85f), color)))
        )
    }
}

/** Compact game HUD: level, energy tier, XP, coins. */
@Composable
fun HudStrip(stats: Stats, modifier: Modifier = Modifier) {
    val c = TT.c
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HudPill(Modifier.weight(0.82f), "Level ${stats.level.level}") {
            Icon(Icons.Rounded.Star, null, tint = c.sun, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(4.dp))
            Text("Lv ${stats.level.level}", fontFamily = Display, fontSize = 17.sp, color = c.ink)
        }
        HudPill(Modifier.weight(1.15f), "Energy ${stats.tier.label}") {
            Icon(Icons.Rounded.Bolt, null, tint = c.mint, modifier = Modifier.size(24.dp))
            Column {
                Text("Energy", style = MaterialTheme.typography.labelSmall, color = c.muted)
                Text(stats.tier.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = c.mint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HudPill(Modifier.weight(1.15f), "XP ${stats.level.xpInto} of ${stats.level.xpNeeded}") {
            Box(Modifier.size(24.dp).clip(CircleShape).background(c.sky), contentAlignment = Alignment.Center) {
                Text("XP", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Spacer(Modifier.width(5.dp))
            Column(Modifier.weight(1f)) {
                Text("${stats.level.xpInto}/${stats.level.xpNeeded}", style = MaterialTheme.typography.labelSmall, color = c.ink, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                FatProgress(stats.level.xpInto.toFloat() / stats.level.xpNeeded, c.sky, c.skySoft, Modifier.fillMaxWidth(), 6.dp)
            }
        }
        HudPill(Modifier.weight(1f), "${stats.coins} coins") {
            Icon(Icons.Rounded.MonetizationOn, null, tint = c.sun, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(3.dp))
            Column {
                Text("%,d".format(stats.coins), fontFamily = Display, fontSize = 14.sp, color = c.ink, maxLines = 1)
                Text("coins", style = MaterialTheme.typography.labelSmall, color = c.muted)
            }
        }
    }
}

@Composable
private fun HudPill(modifier: Modifier, description: String, content: @Composable () -> Unit) {
    Surface(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(18.dp),
        color = TT.c.surface,
    ) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
fun BrandMark(size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(TT.c.tangerine),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TT.c.ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
