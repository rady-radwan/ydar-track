package com.rady.worktracker.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rady.worktracker.R

/*
 * Ydar Track visual identity: build yourself like a construction project.
 * Charcoal and concrete with construction-gold accents, outlined tiles,
 * and bold condensed uppercase titles. Dark by default; a light theme is available.
 */

val Barlow = FontFamily(
    Font(R.font.barlow_400, FontWeight.Normal),
    Font(R.font.barlow_500, FontWeight.Medium),
    Font(R.font.barlow_600, FontWeight.SemiBold),
    Font(R.font.barlow_700, FontWeight.Bold),
)

/** Condensed face for titles, tile labels and big numbers. */
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_500, FontWeight.Medium),
    Font(R.font.barlow_condensed_600, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_700, FontWeight.Bold),
)

@Immutable
data class Palette(
    val dark: Boolean,
    val ground: Color,      // screen background
    val slab: Color,        // tiles and cards
    val slabRaised: Color,  // inputs, chips, tracks
    val border: Color,      // tile outlines
    val ink: Color,
    val inkMuted: Color,
    val rule: Color,        // hairlines and dial ticks
    val accent: Color,      // cyan: live timer, main actions, big numbers
    val onAccent: Color,
    val info: Color,        // secondary text links
    val alert: Color,       // red highlight
    val danger: Color,
    val banner: Color,      // header banner background
    val bannerSkyline: Color,
)

private val DarkPalette = Palette(
    dark = true,
    ground = Color(0xFF0E0F11),
    slab = Color(0xFF16181B),
    slabRaised = Color(0xFF23262B),
    border = Color(0xFF2A2D32),
    ink = Color(0xFFF3F1EC),
    inkMuted = Color(0xFFA29D92),
    rule = Color(0xFF34373C),
    accent = Color(0xFFFFB627),     // construction gold
    onAccent = Color(0xFF1A1306),
    info = Color(0xFFFFD27A),
    alert = Color(0xFFFF6A3D),
    danger = Color(0xFFFF6B6B),
    banner = Color(0xFF141210),
    bannerSkyline = Color(0xFF2A2418),
)

private val LightPalette = Palette(
    dark = false,
    ground = Color(0xFFF4F2EE),
    slab = Color(0xFFFFFFFF),
    slabRaised = Color(0xFFEDE8DF),
    border = Color(0xFFE3DDD2),
    ink = Color(0xFF1A1814),
    inkMuted = Color(0xFF6B655A),
    rule = Color(0xFFDDD6CA),
    accent = Color(0xFFB57A00),     // darker gold so it reads on white
    onAccent = Color(0xFFFFFFFF),
    info = Color(0xFF8A5A00),
    alert = Color(0xFFD9480F),
    danger = Color(0xFFC92A2A),
    banner = Color(0xFFF6EBD3),
    bannerSkyline = Color(0xFFE9D6AE),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** Shortcut: `P.accent`, `P.inkMuted`, … inside composables. */
val P: Palette
    @Composable get() = LocalPalette.current

private fun typography(): Typography {
    val body = TextStyle(fontFamily = Barlow)
    val cond = TextStyle(fontFamily = BarlowCondensed)
    return Typography(
        displayLarge = cond.copy(fontWeight = FontWeight.SemiBold, fontSize = 72.sp, lineHeight = 74.sp, fontFeatureSettings = "tnum"),
        displayMedium = cond.copy(fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 58.sp, fontFeatureSettings = "tnum"),
        displaySmall = cond.copy(fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, fontFeatureSettings = "tnum"),
        headlineLarge = cond.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = 0.5.sp),
        headlineMedium = cond.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = 0.4.sp),
        headlineSmall = cond.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp),
        titleLarge = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
        titleSmall = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        bodyLarge = body.copy(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = body.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = body.copy(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        labelMedium = cond.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp, letterSpacing = 0.6.sp),
        labelSmall = body.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, fontFeatureSettings = "tnum"),
    )
}

@Composable
fun WorkTrackerTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            secondary = p.info, onSecondary = p.onAccent,
            tertiary = p.alert,
            background = p.ground, onBackground = p.ink,
            surface = p.ground, onSurface = p.ink, onSurfaceVariant = p.inkMuted,
            surfaceContainerLowest = p.ground, surfaceContainerLow = p.slab, surfaceContainer = p.slab,
            surfaceContainerHigh = p.slab, surfaceContainerHighest = p.slabRaised,
            surfaceVariant = p.slabRaised, outline = p.inkMuted, outlineVariant = p.rule,
            secondaryContainer = p.slabRaised, onSecondaryContainer = p.ink,
            primaryContainer = p.accent.copy(alpha = 0.22f), onPrimaryContainer = p.ink,
            error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            secondary = p.info, onSecondary = Color.White,
            tertiary = p.alert,
            background = p.ground, onBackground = p.ink,
            surface = p.ground, onSurface = p.ink, onSurfaceVariant = p.inkMuted,
            surfaceContainerLowest = Color.White, surfaceContainerLow = p.slab, surfaceContainer = p.slab,
            surfaceContainerHigh = p.slab, surfaceContainerHighest = p.slabRaised,
            surfaceVariant = p.slabRaised, outline = p.inkMuted, outlineVariant = p.rule,
            secondaryContainer = p.slabRaised, onSecondaryContainer = p.ink,
            primaryContainer = Color(0xFFF6E3B8), onPrimaryContainer = p.ink,
            error = p.danger,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6.dp),
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(18.dp),
                extraLarge = RoundedCornerShape(26.dp),
            ),
            content = content,
        )
    }
}
