package com.rady.worktracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rady.worktracker.data.Rank

const val APP_NAME = "Ydar Track"
const val SLOGAN = "Build yourself, one hour at a time."


/** "YDAR TRACK" with the Y in gold. */
@Composable
fun Wordmark(fontSize: TextUnit = 34.sp, color: Color = P.ink) {
    val style = MaterialTheme.typography.headlineLarge.copy(fontSize = fontSize, lineHeight = fontSize * 1.1f, letterSpacing = 1.sp)
    Row {
        Text("Y", style = style, color = P.accent)
        Text("DAR TRACK", style = style, color = color)
    }
}

/**
 * Home header (compact): a construction skyline at golden hour, the logo, a greeting,
 * today's line, and a gold call-to-action strip.
 */
@Composable
fun HomeBanner(
    greeting: String,
    quote: String,
    strip: String,
    modifier: Modifier = Modifier,
    onStripClick: (() -> Unit)? = null,
    /** "Synced 2m ago" etc.; null hides the Refresh button (not signed in). */
    syncLabel: String? = null,
    syncing: Boolean = false,
    onSync: (() -> Unit)? = null,
) {
    val p = P
    val sky = if (p.dark) listOf(Color(0xFF2A2214), p.banner, p.banner) else listOf(Color(0xFFFFE7B0), p.banner, p.banner)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, p.border, RoundedCornerShape(18.dp))) {
        Box(Modifier.fillMaxWidth().height(148.dp).background(Brush.verticalGradient(sky))) {
            Canvas(Modifier.fillMaxSize()) { drawSkyline(p.bannerSkyline, p.accent) }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp)) { LogoMark(136.dp) }
            Column(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 12.dp, end = 118.dp)) {
                Wordmark(20.sp)
                Spacer(Modifier.height(8.dp))
                Text(greeting, style = MaterialTheme.typography.bodySmall, color = p.inkMuted)
                Text("Keep building.", style = MaterialTheme.typography.headlineMedium, color = p.ink)
                Spacer(Modifier.height(2.dp))
                Text(quote, style = MaterialTheme.typography.bodySmall, color = p.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().background(p.accent), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = { onStripClick?.invoke() },
                enabled = onStripClick != null,
                color = Color.Transparent,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    strip, color = p.onAccent, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
            if (syncLabel != null && onSync != null) {
                Surface(onClick = onSync, enabled = !syncing, color = Color.Transparent) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Refresh, contentDescription = "Refresh",
                            tint = p.onAccent, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (syncing) "Syncing…" else syncLabel, color = p.onAccent,
                            style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSkyline(color: Color, gold: Color) {
    val w = size.width
    val h = size.height
    // Low sun glow
    drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = 0.35f), Color.Transparent), center = Offset(w * 0.78f, h * 0.72f), radius = h * 0.75f),
        radius = h * 0.75f, center = Offset(w * 0.78f, h * 0.72f))
    // Buildings: (x fraction, width fraction, height fraction)
    val blocks = listOf(
        0.00f to (0.07f to 0.26f), 0.06f to (0.05f to 0.40f), 0.11f to (0.08f to 0.22f), 0.19f to (0.05f to 0.48f),
        0.24f to (0.07f to 0.33f), 0.31f to (0.06f to 0.44f), 0.37f to (0.09f to 0.25f), 0.46f to (0.05f to 0.38f),
        0.51f to (0.08f to 0.20f), 0.59f to (0.06f to 0.34f), 0.65f to (0.07f to 0.24f), 0.72f to (0.05f to 0.30f),
        0.77f to (0.08f to 0.18f), 0.85f to (0.06f to 0.27f), 0.91f to (0.09f to 0.21f),
    )
    for ((x, wh) in blocks) {
        val (bw, bh) = wh
        drawRect(color, Offset(x * w, h * (1f - bh)), Size(bw * w, h * bh))
    }
    // Lit windows
    val win = gold.copy(alpha = 0.55f)
    for ((x, wh) in blocks.filterIndexed { i, _ -> i % 3 == 1 }) {
        val (bw, bh) = wh
        var y = h * (1f - bh) + 6.dp.toPx()
        while (y < h - 6.dp.toPx()) {
            drawRect(win, Offset(x * w + bw * w * 0.3f, y), Size(2.dp.toPx(), 2.dp.toPx()))
            y += 9.dp.toPx()
        }
    }
    // Tower crane
    val crane = gold.copy(alpha = 0.6f)
    val mastX = 0.44f * w
    val top = h * 0.30f
    val sw = 1.6.dp.toPx()
    drawLine(crane, Offset(mastX, h), Offset(mastX, top), strokeWidth = sw * 1.4f)
    drawLine(crane, Offset(mastX - 0.08f * w, top + sw * 3), Offset(mastX + 0.22f * w, top + sw * 3), strokeWidth = sw)
    drawLine(crane, Offset(mastX, top - sw * 4), Offset(mastX + 0.22f * w, top + sw * 3), strokeWidth = sw * 0.7f)
    drawLine(crane, Offset(mastX, top - sw * 4), Offset(mastX - 0.08f * w, top + sw * 3), strokeWidth = sw * 0.7f)
    drawLine(crane, Offset(mastX + 0.17f * w, top + sw * 3), Offset(mastX + 0.17f * w, h * 0.55f), strokeWidth = sw * 0.6f)
    drawRect(gold, Offset(mastX + 0.17f * w - sw * 2.2f, h * 0.55f), Size(sw * 4.4f, sw * 3f))
    var y = h
    while (y > top + sw * 6) {
        drawLine(crane, Offset(mastX - sw * 2, y), Offset(mastX + sw * 2, y - sw * 5), strokeWidth = sw * 0.5f)
        y -= sw * 5
    }
}

/**
 * Rank badge: a building that gets taller with every rank
 * (one floor per level, up to 10), crowned with a flag at the top rank.
 */
@Composable
fun RankBadge(rank: Rank, badgeSize: Dp) {
    val badge = badgeSize
    val color = Color(rank.color)
    val window = P.slab
    Box(
        Modifier.size(badge).clip(RoundedCornerShape(badge * 0.24f)).background(color.copy(alpha = 0.16f))
            .border(1.5.dp, color.copy(alpha = 0.6f), RoundedCornerShape(badge * 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(badge * 0.13f)) {
            val floors = rank.number
            val ground = size.height * 0.97f
            val maxH = size.height * 0.80f
            val floorH = maxH / 10f
            val bw = size.width * 0.48f
            val left = (size.width - bw) / 2f
            val topY = ground - floorH * floors
            drawLine(color, Offset(0f, ground), Offset(size.width, ground), strokeWidth = size.height * 0.035f, cap = StrokeCap.Round)
            for (f in 0 until floors) {
                val y = ground - floorH * (f + 1)
                drawRoundRect(color, Offset(left, y + floorH * 0.1f), Size(bw, floorH * 0.9f), CornerRadius(floorH * 0.12f))
                val ww = bw * 0.2f
                val wh = floorH * 0.36f
                drawRect(window, Offset(left + bw * 0.2f, y + floorH * 0.34f), Size(ww, wh))
                drawRect(window, Offset(left + bw * 0.6f, y + floorH * 0.34f), Size(ww, wh))
            }
            val mx = size.width / 2f
            if (rank.level >= 9) {
                drawLine(color, Offset(mx, topY), Offset(mx, topY - floorH * 1.6f), strokeWidth = size.width * 0.03f)
                val flag = Path().apply {
                    moveTo(mx, topY - floorH * 1.6f); lineTo(mx + size.width * 0.2f, topY - floorH * 1.2f); lineTo(mx, topY - floorH * 0.8f); close()
                }
                drawPath(flag, color)
            } else if (rank.level >= 5) {
                drawLine(color, Offset(mx, topY), Offset(mx, topY - floorH * 1.0f), strokeWidth = size.width * 0.025f, cap = StrokeCap.Round)
            }
        }
    }
}

/** Rank color for text: darkened in the light theme so pale colors stay readable on white. */
@Composable
fun rankInk(rank: Rank): Color {
    val c = Color(rank.color)
    return if (P.dark) c else Color(c.red * 0.7f, c.green * 0.7f, c.blue * 0.7f, 1f)
}

/** "LEVEL 4 OF 10" over "🧱 FOUNDATION BUILDER". */
@Composable
fun RankTitle(rank: Rank, big: Boolean = false) {
    Column {
        Text("LEVEL ${rank.number} OF 10", style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
        Text(
            "${rank.emoji} ${rank.title.uppercase()}",
            style = if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
            color = rankInk(rank), fontWeight = FontWeight.Bold,
        )
    }
}
