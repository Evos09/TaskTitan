package com.rork.tasktitan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.tasktitan.R

/** Candy Pop Light tokens. */
data class Palette(
    val canvas: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val tangerine: Color,
    val tangerineSoft: Color,
    val sky: Color,
    val skySoft: Color,
    val bubblegum: Color,
    val bubblegumSoft: Color,
    val mint: Color,
    val mintSoft: Color,
    val sun: Color,
    val outline: Color,
)

val CandyPalette = Palette(
    canvas = Color(0xFFFFF8EC),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF14254A),
    muted = Color(0xFF66708A),
    tangerine = Color(0xFFFF8A1F),
    tangerineSoft = Color(0xFFFFE9D2),
    sky = Color(0xFF2BB5FF),
    skySoft = Color(0xFFDDF2FF),
    bubblegum = Color(0xFFFF6FB5),
    bubblegumSoft = Color(0xFFFFE3F0),
    mint = Color(0xFF2EAD5B),
    mintSoft = Color(0xFFE2F7E6),
    sun = Color(0xFFFFC21A),
    outline = Color(0x1414254A),
)

val HighContrastPalette = CandyPalette.copy(
    canvas = Color(0xFFFFFFFF),
    ink = Color(0xFF000000),
    muted = Color(0xFF2B2F3A),
    tangerine = Color(0xFFC24E00),
    sky = Color(0xFF00609A),
    bubblegum = Color(0xFFB0005A),
    mint = Color(0xFF00702E),
    outline = Color(0x99000000),
)

val LocalPalette = staticCompositionLocalOf { CandyPalette }
val LocalReducedMotion = staticCompositionLocalOf { false }

object TT {
    val c: Palette @Composable get() = LocalPalette.current
    val reducedMotion: Boolean @Composable get() = LocalReducedMotion.current
}

val Display = FontFamily(Font(R.font.lilita_one))

@OptIn(ExperimentalTextApi::class)
val Body = FontFamily(
    Font(R.font.nunito, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.nunito, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.nunito, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.body() = copy(fontFamily = Body)
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Display),
        displayMedium = base.displayMedium.copy(fontFamily = Display),
        displaySmall = base.displaySmall.copy(fontFamily = Display, fontSize = 34.sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = Display, fontSize = 30.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = Display, fontSize = 26.sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = Display, fontSize = 22.sp),
        titleLarge = base.titleLarge.copy(fontFamily = Display, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
        titleSmall = base.titleSmall.copy(fontFamily = Body, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.body().copy(fontWeight = FontWeight.SemiBold),
        bodyMedium = base.bodyMedium.body().copy(fontWeight = FontWeight.Medium),
        bodySmall = base.bodySmall.body().copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.body().copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
        labelMedium = base.labelMedium.body().copy(fontWeight = FontWeight.Bold),
        labelSmall = base.labelSmall.body().copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
fun AppTheme(
    highContrast: Boolean = false,
    reducedMotion: Boolean = false,
    largeText: Boolean = false,
    content: @Composable () -> Unit,
) {
    val p = if (highContrast) HighContrastPalette else CandyPalette
    val scheme = lightColorScheme(
        primary = p.tangerine,
        onPrimary = Color.White,
        primaryContainer = p.tangerineSoft,
        onPrimaryContainer = p.ink,
        secondary = p.sky,
        onSecondary = Color.White,
        secondaryContainer = p.tangerineSoft,
        onSecondaryContainer = p.tangerine,
        tertiary = p.bubblegum,
        tertiaryContainer = p.bubblegumSoft,
        background = p.canvas,
        onBackground = p.ink,
        surface = p.canvas,
        onSurface = p.ink,
        surfaceVariant = Color(0xFFF4EDE1),
        onSurfaceVariant = p.muted,
        surfaceContainer = p.surface,
        surfaceContainerLow = p.surface,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = Color(0xFFF4EDE1),
        surfaceContainerLowest = p.surface,
        outline = p.outline,
        outlineVariant = p.outline,
    )
    val density = LocalDensity.current
    val scaled = if (largeText) Density(density.density, density.fontScale * 1.2f) else density
    CompositionLocalProvider(
        LocalPalette provides p,
        LocalReducedMotion provides reducedMotion,
        LocalDensity provides scaled,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography(),
            shapes = Shapes(
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
            content = content,
        )
    }
}
