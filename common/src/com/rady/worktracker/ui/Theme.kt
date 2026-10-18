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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rady.worktracker.data.AppFont

/*
 * Ydar Track visual identity: build yourself like a construction project.
 * Charcoal and concrete with construction-gold accents, outlined tiles,
 * and clean Google-font titles. Dark by default; a light theme is available.
 */

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

private fun typography(font: AppFont): Typography {
    val f = TextStyle(fontFamily = fontFamilyFor(font))
    return Typography(
        displayLarge = f.copy(fontWeight = FontWeight.SemiBold, fontSize = 60.sp, lineHeight = 64.sp, fontFeatureSettings = "tnum"),
        displayMedium = f.copy(fontWeight = FontWeight.SemiBold, fontSize = 46.sp, lineHeight = 50.sp, fontFeatureSettings = "tnum"),
        displaySmall = f.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, fontFeatureSettings = "tnum"),
        headlineLarge = f.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 31.sp),
        headlineMedium = f.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 27.sp),
        headlineSmall = f.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp),
        titleLarge = f.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium = f.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
        titleSmall = f.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
        bodyLarge = f.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = f.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = f.copy(fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
        labelLarge = f.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
        labelMedium = f.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp, lineHeight = 15.sp, letterSpacing = 0.8.sp),
        labelSmall = f.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, fontFeatureSettings = "tnum"),
    )
}

@Composable
fun WorkTrackerTheme(dark: Boolean, font: AppFont = AppFont.GOOGLE_SANS, content: @Composable () -> Unit) {
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
            typography = remember(font) { typography(font) },
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
