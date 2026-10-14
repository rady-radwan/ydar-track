@file:OptIn(ExperimentalMaterial3Api::class)

package com.rady.worktracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.AverageRow
import com.rady.worktracker.data.Granularity
import com.rady.worktracker.data.StatsRange
import com.rady.worktracker.data.StatsResult
import com.rady.worktracker.data.averageRows
import com.rady.worktracker.data.computeStats
import com.rady.worktracker.data.earliestDate
import com.rady.worktracker.data.formatDuration
import com.rady.worktracker.data.hourHeatmap
import com.rady.worktracker.data.rangeDates
import com.rady.worktracker.data.rangeLabel
import com.rady.worktracker.data.rankHistory
import com.rady.worktracker.data.toLocalDate
import com.rady.worktracker.data.weekRank
import com.rady.worktracker.data.weekdayAverages
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val UnknownColor = Color(0xFF9E9E9E)

@Composable
fun StatsScreen(data: AppData, onShowRank: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val now by rememberNow(30_000L)
    val today = toLocalDate(now, zone)
    var range by rememberSaveable { mutableStateOf(StatsRange.WEEK) }
    var customFrom by rememberSaveable { mutableStateOf<Long?>(null) }   // epoch days
    var customTo by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickRange by remember { mutableStateOf(false) }

    val cf: Long? = customFrom
    val ct: Long? = customTo
    val custom: Pair<LocalDate, LocalDate>? = if (cf != null && ct != null) LocalDate.ofEpochDay(cf) to LocalDate.ofEpochDay(ct) else null
    val sessions = remember(data, now) { data.sessionsIncludingActive(now) }
    val (from, to) = rangeDates(range, today, earliestDate(sessions, zone), custom)
    val stats = remember(sessions, from, to, today) { computeStats(data, sessions, from, to, today, zone) }
    val averages = remember(sessions, today, custom) { averageRows(data, sessions, today, zone, custom) }
    val weekdays = remember(sessions, from, to, today) { weekdayAverages(sessions, from, to, today, zone) }
    val heat = remember(sessions, from, to) { hourHeatmap(sessions, from, to, zone) }
    val week = remember(sessions, today) { weekRank(data, sessions, today, today, zone) }
    val history = remember(sessions, today) { rankHistory(data, sessions, today, zone, 12) }
    val legacy = remember(sessions) { com.rady.worktracker.data.legacyOf(sessions) }
    val colorOf: (Long) -> Color = { pid -> data.project(pid)?.let { Color(it.color) } ?: UnknownColor }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Stats", subtitle = rangeLabel(from, to)) }

        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsRange.entries.forEach { r ->
                    val on = r == range
                    Surface(
                        onClick = { if (r == StatsRange.CUSTOM) pickRange = true else range = r },
                        shape = RoundedCornerShape(50),
                        color = if (on) P.accent else P.slab,
                        contentColor = if (on) P.onAccent else P.ink,
                        border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, P.border),
                    ) {
                        Text(
                            if (r == StatsRange.CUSTOM && custom != null && on) rangeLabel(custom.first, custom.second)
                            else if (r == StatsRange.CUSTOM) "Pick dates…" else r.label,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        item { SummarySentence(stats) }

        item {
            Slab {
                SectionTitle("Weekly rank")
                Spacer(Modifier.height(4.dp))
                RankBar(week, onClick = onShowRank)
                Spacer(Modifier.height(12.dp))
                Text("LAST 12 WEEKS", style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
                Spacer(Modifier.height(6.dp))
                RankHistoryBars(history)
                val best = history.maxByOrNull { it.rank.level }
                if (best != null && best.total > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Best week: ${best.rank.emoji} ${best.rank.title} (${formatDuration(best.avgPerDay)} a day, week of ${best.weekStart.format(DateTimeFormatter.ofPattern("MMM d"))})",
                        style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
                    )
                }
            }
        }

        item { LegacyCard(legacy) }

        if (stats.total > 0L) {
            item { HeroCard(stats) }
            item {
                Slab {
                    SectionTitle(chartTitle(stats.granularity)) {
                        if (stats.granularity == Granularity.DAY) {
                            Box(Modifier.width(14.dp).height(2.dp).background(P.info))
                            Spacer(Modifier.width(6.dp))
                            Text("daily average", style = MaterialTheme.typography.labelMedium, color = P.info)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    StackedBarChart(
                        buckets = stats.buckets, order = stats.perRoot.map { it.projectId }, colorOf = colorOf,
                        labelEvery = when { stats.buckets.size > 24 -> 5; stats.buckets.size > 14 -> 2; else -> 1 },
                        averageMs = if (stats.granularity == Granularity.DAY) stats.averagePerDay else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item { ProjectsTable(data, stats, colorOf) }
            item { WeekdayCard(weekdays) }
            item { HeatmapCard(heat) }
        } else {
            item { EmptyState("Nothing tracked in this period", "Start a timer or add a past session, and your charts fill in here.") }
        }

        if (sessions.isNotEmpty()) {
            item { AveragesCard(averages, highlight = if (range == StatsRange.CUSTOM) "Custom" else range.label) }
        }
    }

    if (pickRange) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = custom?.first?.toEpochDay()?.times(86_400_000L),
            initialSelectedEndDateMillis = custom?.second?.toEpochDay()?.times(86_400_000L),
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val s = state.selectedStartDateMillis!! / 86_400_000L
                        val e = (state.selectedEndDateMillis ?: state.selectedStartDateMillis!!) / 86_400_000L
                        customFrom = minOf(s, e); customTo = maxOf(s, e)
                        range = StatsRange.CUSTOM
                        pickRange = false
                    },
                ) { Text("Show") }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(
                state = state,
                title = { Text("Choose a date range", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun chartTitle(g: Granularity): String = when (g) {
    Granularity.DAY -> "Hours per day"
    Granularity.MONTH -> "Hours per month"
    Granularity.YEAR -> "Hours per year"
}

/** "Since Monday, October 5 (5 days) you finished 12 sessions for a total of 14h 20m…" */
@Composable
private fun SummarySentence(s: StatsResult) {
    val fmt = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy") }
    val bold = SpanStyle(fontWeight = FontWeight.Bold, color = P.ink)
    val accent = SpanStyle(fontWeight = FontWeight.Bold, color = P.accent)
    Slab {
        Text(
            buildAnnotatedString {
                append("Since ")
                withStyle(bold) { append(s.from.format(fmt)) }
                append(" (${s.elapsedDays} day${if (s.elapsedDays == 1) "" else "s"}) you finished ${s.sessionCount} session${if (s.sessionCount == 1) "" else "s"}, for a total of ")
                withStyle(bold) { append(formatDuration(s.total)) }
                append(". That's ")
                withStyle(accent) { append("${formatDuration(s.averagePerDay)} per day") }
                append(" on average (including today).")
            },
            style = MaterialTheme.typography.bodyLarge, color = P.inkMuted,
        )
    }
}

@Composable
private fun HeroCard(s: StatsResult) {
    Slab(padding = 20.dp) {
        Text("TOTAL TIME", style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
        Text(formatDuration(s.total), style = MaterialTheme.typography.displayMedium, color = P.accent)
        Text(
            "${s.sessionCount} session${if (s.sessionCount == 1) "" else "s"} on ${s.activeDays} of ${s.elapsedDays} day${if (s.elapsedDays == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium, color = P.inkMuted,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = P.rule.copy(alpha = 0.7f))
        Spacer(Modifier.height(14.dp))
        Row {
            Figure("Per day", formatDuration(s.averagePerDay), Modifier.weight(1f))
            Figure("Per active day", formatDuration(s.averagePerActiveDay), Modifier.weight(1f))
            Figure("Per week", formatDuration(s.averagePerWeek), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Figure("Per session", formatDuration(s.averagePerSession), Modifier.weight(1f))
            Figure("Longest session", formatDuration(s.longestSession), Modifier.weight(2f))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = P.ink)
        Text(label, style = MaterialTheme.typography.labelSmall, color = P.inkMuted)
    }
}

/** Projects worked on: donut, then a table with subprojects indented (like the Work app). */
@Composable
private fun ProjectsTable(data: AppData, stats: StatsResult, colorOf: (Long) -> Color) {
    Slab {
        SectionTitle("Projects worked on")
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(stats.perRoot.map { colorOf(it.projectId) to it.millis }, Modifier.size(180.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatDuration(stats.total), style = MaterialTheme.typography.headlineMedium, color = P.ink)
                    Text("${stats.perRoot.size} project${if (stats.perRoot.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            stats.perRoot.forEach { pt ->
                val subs = stats.perProject.filter { it.projectId != pt.projectId && data.rootIdOf(it.projectId) == pt.projectId }
                val direct = stats.perProject.firstOrNull { it.projectId == pt.projectId }?.millis ?: 0L
                TableRow(
                    name = data.project(pt.projectId)?.name ?: "Unknown",
                    ms = pt.millis, total = stats.total, color = colorOf(pt.projectId), parent = true,
                    badge = { ProjectBadge(data, pt.projectId, 28.dp) },
                )
                subs.forEach { sp -> TableRow(data.project(sp.projectId)?.name ?: "Unknown", sp.millis, stats.total, colorOf(pt.projectId), parent = false) }
                if (subs.isNotEmpty() && direct > 0) TableRow("(no subproject)", direct, stats.total, colorOf(pt.projectId), parent = false)
            }
        }
    }
}

@Composable
private fun TableRow(name: String, ms: Long, total: Long, color: Color, parent: Boolean, badge: (@Composable () -> Unit)? = null) {
    val pct = if (total == 0L) 0 else (ms * 100f / total).roundToInt()
    val bg = if (parent) color.copy(alpha = 0.16f) else P.slabRaised.copy(alpha = 0.6f)
    Row(Modifier.fillMaxWidth().padding(start = if (parent) 0.dp else 38.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(bg)
                .padding(horizontal = 10.dp, vertical = if (parent) 10.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (badge != null) { badge(); Spacer(Modifier.width(8.dp)) }
            Text(name, style = if (parent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = P.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.width(76.dp).clip(RoundedCornerShape(8.dp)).background(bg)
            .padding(vertical = if (parent) 14.dp else 10.dp), contentAlignment = Alignment.Center) {
            Text(formatDuration(ms), style = MaterialTheme.typography.labelLarge, color = P.ink, maxLines = 1)
        }
        Box(Modifier.width(48.dp).clip(RoundedCornerShape(8.dp)).background(bg)
            .padding(vertical = if (parent) 14.dp else 10.dp), contentAlignment = Alignment.Center) {
            Text("$pct%", style = MaterialTheme.typography.labelLarge, color = P.inkMuted, maxLines = 1)
        }
    }
}

/** Average time worked by day of the week. */
@Composable
private fun WeekdayCard(rows: List<Pair<DayOfWeek, Long>>) {
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    Slab {
        SectionTitle("By day of the week")
        Text("Average time on each weekday in this period.", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { (day, ms) ->
                val best = ms == max && ms > 0
                Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(6.dp)).background(P.slabRaised)) {
                    Box(Modifier.fillMaxWidth((ms.toFloat() / max).coerceIn(0f, 1f)).fillMaxHeight()
                        .background(if (best) P.accent else P.accent.copy(alpha = 0.5f)))
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault()), style = MaterialTheme.typography.titleSmall,
                            color = P.ink, modifier = Modifier.weight(1f))
                        Text(formatDuration(ms), style = MaterialTheme.typography.titleSmall, color = P.ink)
                    }
                }
            }
        }
    }
}

/** Heatmap: weekday rows × hour-of-day columns. */
@Composable
private fun HeatmapCard(grid: Array<LongArray>) {
    val max = grid.maxOf { row -> row.maxOrNull() ?: 0L }.coerceAtLeast(1L)
    val accent = P.accent
    val empty = P.slabRaised
    val days = DayOfWeek.entries
    val blocks = (0 until 12).map { b -> b to days.indices.sumOf { d -> grid[d][b * 2] + grid[d][b * 2 + 1] } }
    val bestBlock = blocks.maxByOrNull { it.second }
    Slab {
        SectionTitle("When you work")
        if (bestBlock != null && bestBlock.second > 0) {
            val h = bestBlock.first * 2
            Text("Your most productive time is ${hourLabel(h)} to ${hourLabel(h + 2)}.", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
        }
        Spacer(Modifier.height(10.dp))
        days.forEachIndexed { d, day ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.5.dp)) {
                Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = P.inkMuted,
                    modifier = Modifier.width(18.dp))
                Canvas(Modifier.weight(1f).height(16.dp)) {
                    val cw = size.width / 24f
                    for (h in 0 until 24) {
                        val v = grid[d][h]
                        val c = if (v == 0L) empty else accent.copy(alpha = (0.25f + 0.75f * (v.toFloat() / max)).coerceIn(0.25f, 1f))
                        drawRoundRect(c, Offset(h * cw + 1f, 0f), Size(cw - 2f, size.height), CornerRadius(3f))
                    }
                }
            }
        }
        Row(Modifier.padding(start = 18.dp, top = 4.dp)) {
            listOf("12a", "6a", "12p", "6p").forEach { l ->
                Text(l, style = MaterialTheme.typography.labelSmall, color = P.inkMuted, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun hourLabel(h: Int): String {
    val hh = h % 24
    return when {
        hh == 0 -> "12 am"
        hh < 12 -> "$hh am"
        hh == 12 -> "12 pm"
        else -> "${hh - 12} pm"
    }
}

@Composable
private fun AveragesCard(rows: List<AverageRow>, highlight: String) {
    Slab {
        SectionTitle("Averages")
        Text("Per day counts every day in the period up to today; per active day counts only days you worked.",
            style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 8.dp)) {
            HeaderCell("Period", Modifier.weight(1.3f), TextAlign.Start)
            HeaderCell("Total", Modifier.weight(1f))
            HeaderCell("Per day", Modifier.weight(1f))
            HeaderCell("Active day", Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        rows.forEach { r ->
            val on = r.label == highlight
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (on) P.accent.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1.3f)) {
                    Text(r.label, style = MaterialTheme.typography.titleSmall, color = if (on) P.accent else P.ink)
                    Text("${r.elapsedDays} day${if (r.elapsedDays == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = P.inkMuted)
                }
                ValueCell(formatDuration(r.total), Modifier.weight(1f))
                ValueCell(formatDuration(r.perDay), Modifier.weight(1f), bold = true)
                ValueCell(formatDuration(r.perActiveDay), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.End) {
    Text(text.uppercase(), modifier = modifier, textAlign = align, style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
}

@Composable
private fun ValueCell(text: String, modifier: Modifier, bold: Boolean = false) {
    Text(text, modifier = modifier, textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = P.ink, maxLines = 1)
}
