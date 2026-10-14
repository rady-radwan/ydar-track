package com.rady.worktracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rady.worktracker.data.Legacy
import com.rady.worktracker.data.Rank
import com.rady.worktracker.data.Ranks
import com.rady.worktracker.data.WeekRank
import com.rady.worktracker.data.formatDuration
import com.rady.worktracker.data.rankUpMessage
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "YOUR RANK THIS WEEK  🧱 FOUNDATION BUILDER   1h 40m/day  >" + what's needed today. */
@Composable
fun RankBar(week: WeekRank, onClick: () -> Unit) {
    Slab(onClick = onClick, padding = 0.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RankBadge(week.rank, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("YOUR RANK THIS WEEK", style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
                Text("${week.rank.emoji} ${week.rank.title.uppercase()}", style = MaterialTheme.typography.headlineSmall,
                    color = rankInk(week.rank), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDuration(week.avgPerDay), style = MaterialTheme.typography.titleMedium, color = P.ink)
                Text("per day", style = MaterialTheme.typography.labelSmall, color = P.inkMuted)
            }
            Icon(ChevronIcon, contentDescription = "Open rank", tint = P.inkMuted, modifier = Modifier.rotate(-90f).padding(start = 4.dp))
        }
        week.next?.let { n ->
            Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Work ", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                Text(formatDuration(week.neededToday(n)), style = MaterialTheme.typography.bodySmall, color = P.accent, fontWeight = FontWeight.Bold)
                Text(" more today to reach ${n.emoji} ${n.title}", style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(P.slabRaised)) {
            Box(Modifier.fillMaxWidth(week.progress.coerceIn(0.02f, 1f)).fillMaxHeight().background(Color(week.rank.color)))
        }
    }
}

/** The pop-up shown when the app opens (and when you tap your rank). */
@Composable
fun RankDialog(week: WeekRank, legacy: Legacy, quote: String, onDismiss: () -> Unit) {
    var ladder by remember { mutableStateOf(false) }
    val color = Color(week.rank.color)
    val dayFmt = remember { DateTimeFormatter.ofPattern("MMM d") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = P.slab,
            border = androidx.compose.foundation.BorderStroke(1.dp, P.border),
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 680.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent)))
                        .padding(top = 24.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 20.dp)) {
                        RankBadge(week.rank, 104.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("THIS WEEK'S RANK", style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
                        Text("${week.rank.emoji} ${week.rank.title.uppercase()}", style = MaterialTheme.typography.headlineLarge,
                            color = rankInk(week.rank), textAlign = TextAlign.Center)
                        Text("Level ${week.rank.number} of 10", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                        Spacer(Modifier.height(6.dp))
                        Text(week.rank.tagline, style = MaterialTheme.typography.bodyMedium, color = P.ink, textAlign = TextAlign.Center)
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row {
                        Figure2("Per day", formatDuration(week.avgPerDay), Modifier.weight(1f))
                        Figure2("This week", formatDuration(week.total), Modifier.weight(1f))
                        Figure2("Days counted", "${week.daysElapsed}", Modifier.weight(1f))
                    }
                    week.next?.let { n ->
                        Column {
                            Row {
                                Text(week.rank.title, style = MaterialTheme.typography.labelMedium, color = rankInk(week.rank), modifier = Modifier.weight(1f))
                                Text("${n.emoji} ${n.title} · ${formatDuration(n.minPerDay * 60_000L)}/day", style = MaterialTheme.typography.labelMedium, color = rankInk(n))
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(P.slabRaised)) {
                                Box(
                                    Modifier.fillMaxWidth(week.progress.coerceIn(0.03f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50))
                                        .background(Brush.horizontalGradient(listOf(color, Color(n.color))))
                                )
                            }
                        }
                        // What it takes today
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.accent.copy(alpha = 0.12f))
                                .border(1.dp, P.accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("TO RANK UP TODAY", style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
                                Text(rankUpMessage(week), style = MaterialTheme.typography.bodyMedium, color = P.ink)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatDuration(week.neededToday(n)), style = MaterialTheme.typography.headlineMedium, color = P.accent)
                                Text("more today", style = MaterialTheme.typography.labelSmall, color = P.inkMuted)
                            }
                        }
                    } ?: Text(rankUpMessage(week), style = MaterialTheme.typography.bodyMedium, color = P.ink)

                    QuoteBox(quote)
                    LegacyStrip(legacy)
                    Text(
                        "Your rank shows how high you're climbing (average per day from Monday ${week.weekStart.format(dayFmt)}; it starts fresh every Monday). Your legacy shows how far you've come.",
                        style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
                    )
                    TextButton(onClick = { ladder = !ladder }) { Text(if (ladder) "Hide all ranks" else "See all 10 ranks", color = P.info) }
                    AnimatedVisibility(ladder) { RankLadder(week) }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = P.accent, contentColor = P.onAccent),
                    ) { Text("KEEP BUILDING", style = MaterialTheme.typography.headlineSmall) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** Celebration when you climb to a higher rank during the week. */
@Composable
fun RankUpDialog(rank: Rank, onDismiss: () -> Unit) {
    val color = Color(rank.color)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp), color = P.slab,
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Column(
                Modifier.background(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent))).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("RANK UP!", style = MaterialTheme.typography.headlineMedium, color = P.accent)
                Spacer(Modifier.height(14.dp))
                RankBadge(rank, 116.dp)
                Spacer(Modifier.height(14.dp))
                Text("LEVEL ${rank.number} OF 10", style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
                Text("${rank.emoji} ${rank.title.uppercase()}", style = MaterialTheme.typography.headlineLarge, color = rankInk(rank), textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("“${rank.message}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = P.ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = P.accent, contentColor = P.onAccent),
                ) { Text("KEEP CLIMBING", style = MaterialTheme.typography.headlineSmall) }
            }
        }
    }
}

@Composable
fun QuoteBox(quote: String, sub: String? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(P.slabRaised).padding(14.dp)) {
        Box(Modifier.width(3.dp).height(40.dp).background(P.accent))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("“$quote”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = P.ink)
            if (sub != null) {
                Spacer(Modifier.height(4.dp))
                Text(sub, style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
            }
        }
    }
}

@Composable
private fun Figure2(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = P.ink)
        Text(label, style = MaterialTheme.typography.labelSmall, color = P.inkMuted)
    }
}

/** All 10 ranks, highest first, with the requirement and what you'd need today. */
@Composable
fun RankLadder(week: WeekRank) {
    val current = week.rank
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Ranks.asReversed().forEach { r ->
            val on = r.level == current.level
            val reached = r.level <= current.level
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (on) Color(r.color).copy(alpha = 0.16f) else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, Color(r.color).copy(alpha = 0.6f), RoundedCornerShape(12.dp)) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RankBadge(r, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${r.number}. ${r.emoji} ${r.title}", style = MaterialTheme.typography.titleSmall,
                        color = if (reached) P.ink else P.inkMuted, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                    Text(r.tagline, style = MaterialTheme.typography.labelSmall, color = P.inkMuted, maxLines = 2)
                }
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (r.minPerDay == 0) "start" else "${hoursLabel(r.minPerDay)}/day",
                        style = MaterialTheme.typography.labelMedium, color = if (reached) rankInk(r) else P.inkMuted)
                    if (!reached) {
                        Text("+${formatDuration(week.neededToday(r))} today", style = MaterialTheme.typography.labelSmall, color = P.accent)
                    }
                }
            }
        }
    }
}

/** 90 -> "1.5h", 120 -> "2h", 30 -> "0.5h". */
fun hoursLabel(minutes: Int): String {
    val h = minutes / 60.0
    return if (h == Math.floor(h)) "${h.toInt()}h" else String.format(Locale.US, "%.1fh", h)
}

/** Lifetime progress: a tower that gains a floor every 10 hours, and never resets. */
@Composable
fun LegacyStrip(legacy: Legacy) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.slabRaised).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegacyTower(legacy, Modifier.size(46.dp, 56.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("YOUR LEGACY", style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
            Text("${legacy.floors} floor${if (legacy.floors == 1) "" else "s"} · ${formatDuration(legacy.totalMs)} built",
                style = MaterialTheme.typography.titleMedium, color = P.ink)
            Text("${formatDuration(legacy.toNextMilestone)} to the ${legacy.nextMilestoneHours}-hour milestone",
                style = MaterialTheme.typography.labelSmall, color = P.inkMuted)
        }
    }
}

/** A tower that shows your lifetime floors (the top floor fills as you approach the next one). */
@Composable
fun LegacyTower(legacy: Legacy, modifier: Modifier = Modifier) {
    val gold = P.accent
    val concrete = P.inkMuted.copy(alpha = 0.55f)
    val track = P.rule
    Canvas(modifier) {
        val shown = 8
        val floorH = size.height / (shown + 0.8f)
        val bw = size.width * 0.62f
        val left = (size.width - bw) / 2f
        val full = legacy.floors.coerceAtMost(shown - 1)
        // completed floors (most recent ones), then the floor under construction
        for (i in 0 until shown) {
            val y = size.height - floorH * (i + 1)
            when {
                i < full -> drawRect(if (i == full - 1) gold else concrete, Offset(left, y + 1f), Size(bw, floorH - 2f))
                i == full -> {
                    drawRect(track, Offset(left, y + 1f), Size(bw, floorH - 2f))
                    drawRect(gold, Offset(left, y + 1f), Size(bw * legacy.floorProgress, floorH - 2f))
                }
            }
        }
        // crane over the tower
        val topY = size.height - floorH * (full + 1)
        drawLine(gold, Offset(left + bw * 0.85f, topY), Offset(left + bw * 0.85f, topY - floorH * 1.4f), strokeWidth = 2f)
        drawLine(gold, Offset(left + bw * 0.1f, topY - floorH * 1.4f), Offset(size.width, topY - floorH * 1.4f), strokeWidth = 2f)
    }
}

/** The last weeks as bars: the higher the rank, the taller the bar. */
@Composable
fun RankHistoryBars(history: List<WeekRank>, modifier: Modifier = Modifier) {
    val fmt = remember { DateTimeFormatter.ofPattern("M/d") }
    Row(modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        history.forEachIndexed { i, w ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (w.total > 0) Text(w.rank.emoji, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                val frac = w.rank.number / 10f
                Box(
                    Modifier.fillMaxWidth().height((frac * 70f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(Color(w.rank.color).copy(alpha = if (i == history.lastIndex) 1f else 0.7f))
                )
                Spacer(Modifier.height(4.dp))
                Text(if (i % 2 == history.lastIndex % 2) w.weekStart.format(fmt) else "", style = MaterialTheme.typography.labelSmall,
                    color = P.inkMuted, maxLines = 1)
            }
        }
    }
}

/** Stats card: lifetime hours as a growing tower, with milestones. Never resets. */
@Composable
fun LegacyCard(legacy: Legacy) {
    Slab {
        SectionTitle("Your legacy")
        Text("Lifetime progress. Your rank shows how high you're climbing; your legacy shows how far you've come.",
            style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegacyTower(legacy, Modifier.size(70.dp, 86.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(formatDuration(legacy.totalMs), style = MaterialTheme.typography.displaySmall, color = P.accent)
                Text("built in total · ${legacy.floors} floor${if (legacy.floors == 1) "" else "s"} (1 floor = 10 h)",
                    style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        val prev = com.rady.worktracker.data.LegacyMilestones.lastOrNull { it <= legacy.hours } ?: 0
        val next = legacy.nextMilestoneHours
        val f = ((legacy.hours - prev) / (next - prev)).toFloat().coerceIn(0f, 1f)
        Row {
            Text("${prev}h", style = MaterialTheme.typography.labelSmall, color = P.inkMuted, modifier = Modifier.weight(1f))
            Text("Next milestone: ${next}h", style = MaterialTheme.typography.labelSmall, color = P.accent)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(P.slabRaised)) {
            Box(Modifier.fillMaxWidth(f.coerceAtLeast(0.015f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(P.accent))
        }
        Spacer(Modifier.height(4.dp))
        Text("${formatDuration(legacy.toNextMilestone)} to go", style = MaterialTheme.typography.labelSmall, color = P.inkMuted)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            com.rady.worktracker.data.LegacyMilestones.take(8).forEach { m ->
                val reached = legacy.hours >= m
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (reached) P.accent.copy(alpha = 0.2f) else P.slabRaised)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (m >= 1000) "${m / 1000}k" else "$m", style = MaterialTheme.typography.labelSmall,
                        color = if (reached) P.accent else P.inkMuted, fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
