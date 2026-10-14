package com.rady.worktracker.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** "2h 15m", "45m", "3h". */
fun formatDuration(ms: Long): String {
    val totalMin = (ms.coerceAtLeast(0L) / 60_000L)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

/** "1:05:09" for a running timer. */
fun formatClock(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / 1000L
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

/** Decimal hours, e.g. 1.5 */
fun hours(ms: Long): Double = ms / 3_600_000.0

/** Label for a chart axis value given in hours: "30m", "2h", "2.5h". */
fun formatAxisHours(h: Double): String = when {
    h <= 0.0 -> "0"
    h < 1.0 -> "${(h * 60).roundToInt()}m"
    h == Math.floor(h) -> "${h.toInt()}h"
    else -> String.format(Locale.US, "%.1fh", h)
}

/** Picks a round top value (in hours) for a chart axis that fits [maxMs]. */
fun niceAxisMaxHours(maxMs: Long): Double {
    val h = maxMs / 3_600_000.0
    val steps = listOf(0.5, 1.0, 2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 16.0, 20.0, 24.0, 30.0, 40.0, 50.0, 60.0, 80.0, 100.0,
        150.0, 200.0, 300.0, 400.0, 500.0, 750.0, 1000.0, 1500.0, 2000.0, 3000.0)
    return steps.firstOrNull { it >= h } ?: (Math.ceil(h / 1000.0) * 1000.0)
}

fun dayHeader(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> {
        val pattern = if (date.year == today.year) "EEEE, MMM d" else "EEE, MMM d, yyyy"
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}

fun toLocalDate(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

fun rangeLabel(from: LocalDate, to: LocalDate): String {
    val short = DateTimeFormatter.ofPattern("MMM d")
    val full = DateTimeFormatter.ofPattern("MMM d, yyyy")
    return if (from == to) from.format(full)
    else if (from.year == to.year) "${from.format(short)} – ${to.format(full)}"
    else "${from.format(full)} – ${to.format(full)}"
}
