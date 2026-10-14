package com.rady.worktracker.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Extra statistics: averages by day of the week, when in the day you work,
 * and your streak of days in a row.
 */

/** Average time per weekday (Monday first) over the days in [from]..[to] that have happened. */
fun weekdayAverages(sessions: List<Session>, from: LocalDate, to: LocalDate, today: LocalDate, zone: ZoneId): List<Pair<DayOfWeek, Long>> {
    val last = if (to.isAfter(today)) today else to
    val totals = LongArray(7)
    val counts = IntArray(7)
    var d = from
    while (!d.isAfter(last)) { counts[d.dayOfWeek.value - 1]++; d = d.plusDays(1) }
    for (s in sessions) {
        for ((date, ms) in splitByDay(s.start, s.end, zone)) {
            if (date < from || date > last) continue
            totals[date.dayOfWeek.value - 1] += ms
        }
    }
    return DayOfWeek.entries.map { it to if (counts[it.value - 1] == 0) 0L else totals[it.value - 1] / counts[it.value - 1] }
}

/** Time worked per weekday (rows, Monday first) and hour of day (24 columns). */
fun hourHeatmap(sessions: List<Session>, from: LocalDate, to: LocalDate, zone: ZoneId): Array<LongArray> {
    val grid = Array(7) { LongArray(24) }
    for (s in sessions) {
        var cur = s.start
        while (cur < s.end) {
            val z = Instant.ofEpochMilli(cur).atZone(zone)
            val nextHour = z.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
            val segEnd = minOf(s.end, nextHour)
            val date = z.toLocalDate()
            if (date in from..to) grid[date.dayOfWeek.value - 1][z.hour] += segEnd - cur
            cur = segEnd
        }
    }
    return grid
}

/**
 * Days in a row with at least [minMs] of work, ending today
 * (or yesterday, if today isn't done yet — so the streak isn't "lost" in the morning).
 */
fun streakDays(sessions: List<Session>, today: LocalDate, zone: ZoneId, minMs: Long = 15 * 60_000L): Int {
    val perDay = HashMap<LocalDate, Long>()
    for (s in sessions) for ((date, ms) in splitByDay(s.start, s.end, zone)) perDay[date] = (perDay[date] ?: 0L) + ms
    var d = if ((perDay[today] ?: 0L) >= minMs) today else today.minusDays(1)
    var n = 0
    while ((perDay[d] ?: 0L) >= minMs) { n++; d = d.minusDays(1) }
    return n
}

/** Total time this week for a project and its subprojects. */
fun weekTotalFor(data: AppData, sessions: List<Session>, projectId: Long, today: LocalDate, zone: ZoneId): Long {
    val start = weekStartOf(today)
    val ids = setOf(projectId) + data.children(projectId).map { it.id }
    var total = 0L
    for (s in sessions) {
        if (s.projectId !in ids) continue
        for ((date, ms) in splitByDay(s.start, s.end, zone)) if (date >= start && date <= today) total += ms
    }
    return total
}

/** All-time total for a project and its subprojects. */
fun projectTotal(data: AppData, sessions: List<Session>, projectId: Long): Long {
    val ids = setOf(projectId) + data.children(projectId).map { it.id }
    return sessions.filter { it.projectId in ids }.sumOf { it.durationMillis }
}

/** Progress toward a project's total-hours goal. */
data class GoalProgress(
    val doneMs: Long,
    val goalMs: Long,
    /** Average per week over the last 4 weeks (0 if nothing recent). */
    val weeklyPaceMs: Long,
) {
    val leftMs: Long get() = (goalMs - doneMs).coerceAtLeast(0L)
    val fraction: Float get() = if (goalMs <= 0) 0f else (doneMs.toFloat() / goalMs).coerceIn(0f, 1f)
    val done: Boolean get() = goalMs in 1..doneMs
    /** Weeks to finish at the recent pace, or null if there's no recent pace. */
    val weeksToGo: Double? get() = if (weeklyPaceMs <= 0 || done) null else leftMs.toDouble() / weeklyPaceMs
}

fun goalProgress(data: AppData, sessions: List<Session>, projectId: Long, nowMs: Long): GoalProgress? {
    val p = data.project(projectId) ?: return null
    if (p.totalGoalMin <= 0) return null
    val ids = setOf(projectId) + data.children(projectId).map { it.id }
    val since = nowMs - 28L * 86_400_000L
    val recent = sessions.filter { it.projectId in ids && it.end > since }
        .sumOf { (it.end - maxOf(it.start, since)).coerceAtLeast(0L) }
    return GoalProgress(projectTotal(data, sessions, projectId), p.totalGoalMin * 60_000L, recent / 4)
}
