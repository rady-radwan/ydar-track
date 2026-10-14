package com.rady.worktracker.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class StatsRange(val label: String) {
    WEEK("This week"),
    MONTH("This month"),
    YEAR("This year"),
    ALL("All time"),
    CUSTOM("Custom"),
}

enum class Granularity { DAY, MONTH, YEAR }

/** One bar in the bar chart: time per top-level project inside one day, month or year. */
data class Bucket(val label: String, val perProject: Map<Long, Long>) {
    val total: Long get() = perProject.values.sum()
}

data class ProjectTotal(val projectId: Long, val millis: Long)

data class StatsResult(
    val from: LocalDate,
    val to: LocalDate,
    /** Days from [from] up to today (or [to] if earlier) — the days that have actually happened. */
    val elapsedDays: Int,
    val total: Long,
    val sessionCount: Int,
    val activeDays: Int,
    val longestSession: Long,
    /** Totals per top-level project (subprojects rolled into their parent). */
    val perRoot: List<ProjectTotal>,
    /** Totals per exact project or subproject. */
    val perProject: List<ProjectTotal>,
    val buckets: List<Bucket>,
    val granularity: Granularity,
) {
    val averagePerDay: Long get() = if (elapsedDays <= 0) 0L else total / elapsedDays
    val averagePerActiveDay: Long get() = if (activeDays == 0) 0L else total / activeDays
    val averagePerWeek: Long get() = if (elapsedDays <= 0) 0L else (total * 7L) / maxOf(elapsedDays, 7)
    val averagePerSession: Long get() = if (sessionCount == 0) 0L else total / sessionCount
}

/**
 * Splits a time span into pieces that each fall on a single calendar day,
 * so a session from 23:00 to 01:00 counts 1 h on each day.
 */
fun splitByDay(start: Long, end: Long, zone: ZoneId): List<Pair<LocalDate, Long>> {
    val out = mutableListOf<Pair<LocalDate, Long>>()
    var cur = start
    while (cur < end) {
        val date = Instant.ofEpochMilli(cur).atZone(zone).toLocalDate()
        val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val segEnd = minOf(end, nextMidnight)
        out += date to (segEnd - cur)
        cur = segEnd
    }
    return out
}

fun earliestDate(sessions: List<Session>, zone: ZoneId): LocalDate? =
    sessions.minOfOrNull { it.start }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

/** Start and end date (inclusive) of a range. */
fun rangeDates(
    range: StatsRange,
    today: LocalDate,
    earliest: LocalDate?,
    custom: Pair<LocalDate, LocalDate>? = null,
): Pair<LocalDate, LocalDate> = when (range) {
    StatsRange.WEEK -> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        monday to monday.plusDays(6)
    }
    StatsRange.MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
    StatsRange.YEAR -> today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())
    StatsRange.ALL -> (earliest?.takeIf { it < today } ?: today) to today
    StatsRange.CUSTOM -> custom ?: (today.minusDays(29) to today)
}

fun computeStats(
    data: AppData,
    sessions: List<Session>,
    from: LocalDate,
    to: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    forceGranularity: Granularity? = null,
    locale: Locale = Locale.getDefault(),
): StatsResult {
    val perDay = HashMap<LocalDate, HashMap<Long, Long>>()
    val perProject = HashMap<Long, Long>()
    var count = 0
    var longest = 0L

    for (s in sessions) {
        var inRange = 0L
        val root = data.rootIdOf(s.projectId)
        for ((date, ms) in splitByDay(s.start, s.end, zone)) {
            if (date < from || date > to || ms <= 0) continue
            val day = perDay.getOrPut(date) { HashMap() }
            day[root] = (day[root] ?: 0L) + ms
            perProject[s.projectId] = (perProject[s.projectId] ?: 0L) + ms
            inRange += ms
        }
        if (inRange > 0) {
            count++
            if (inRange > longest) longest = inRange
        }
    }

    val perRoot = HashMap<Long, Long>()
    perProject.forEach { (pid, ms) ->
        val r = data.rootIdOf(pid)
        perRoot[r] = (perRoot[r] ?: 0L) + ms
    }

    val spanDays = ChronoUnit.DAYS.between(from, to).toInt() + 1
    val granularity = forceGranularity ?: when {
        spanDays <= 45 -> Granularity.DAY
        spanDays <= 3 * 366 -> Granularity.MONTH
        else -> Granularity.YEAR
    }

    val buckets: List<Bucket> = when (granularity) {
        Granularity.DAY -> {
            val list = mutableListOf<Bucket>()
            var d = from
            while (!d.isAfter(to)) {
                val label = if (spanDays <= 7) d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) else d.dayOfMonth.toString()
                list += Bucket(label, perDay[d] ?: emptyMap())
                d = d.plusDays(1)
            }
            list
        }
        Granularity.MONTH -> {
            val fmt = DateTimeFormatter.ofPattern(if (spanDays > 366) "MMM yy" else "MMM", locale)
            val first = YearMonth.from(from)
            val last = YearMonth.from(to)
            val months = ChronoUnit.MONTHS.between(first, last).toInt() + 1
            (0 until months).map { i ->
                val ym = first.plusMonths(i.toLong())
                Bucket(ym.atDay(1).format(fmt), mergeDays(perDay.filterKeys { YearMonth.from(it) == ym }))
            }
        }
        Granularity.YEAR -> (from.year..to.year).map { y ->
            Bucket(y.toString(), mergeDays(perDay.filterKeys { it.year == y }))
        }
    }

    val lastElapsed = if (to.isAfter(today)) today else to
    val elapsed = if (lastElapsed.isBefore(from)) 0 else ChronoUnit.DAYS.between(from, lastElapsed).toInt() + 1

    return StatsResult(
        from = from,
        to = to,
        elapsedDays = elapsed,
        total = perProject.values.sum(),
        sessionCount = count,
        activeDays = perDay.count { (_, m) -> m.values.sum() > 0 },
        longestSession = longest,
        perRoot = perRoot.map { ProjectTotal(it.key, it.value) }.sortedByDescending { it.millis },
        perProject = perProject.map { ProjectTotal(it.key, it.value) }.sortedByDescending { it.millis },
        buckets = buckets,
        granularity = granularity,
    )
}

private fun mergeDays(days: Map<LocalDate, Map<Long, Long>>): Map<Long, Long> {
    val merged = HashMap<Long, Long>()
    days.values.forEach { m -> m.forEach { (pid, ms) -> merged[pid] = (merged[pid] ?: 0L) + ms } }
    return merged
}

/** One row of the averages table. */
data class AverageRow(
    val label: String,
    val total: Long,
    val elapsedDays: Int,
    val activeDays: Int,
    val perDay: Long,
    val perActiveDay: Long,
    val perWeek: Long,
)

fun averageRows(
    data: AppData,
    sessions: List<Session>,
    today: LocalDate,
    zone: ZoneId,
    custom: Pair<LocalDate, LocalDate>?,
): List<AverageRow> {
    val earliest = earliestDate(sessions, zone)
    val ranges = buildList {
        add(StatsRange.WEEK); add(StatsRange.MONTH); add(StatsRange.YEAR); add(StatsRange.ALL)
        if (custom != null) add(StatsRange.CUSTOM)
    }
    return ranges.map { r ->
        val (f, t) = rangeDates(r, today, earliest, custom)
        val s = computeStats(data, sessions, f, t, today, zone, forceGranularity = Granularity.YEAR)
        AverageRow(r.label, s.total, s.elapsedDays, s.activeDays, s.averagePerDay, s.averagePerActiveDay, s.averagePerWeek)
    }
}

/** Time per project on one day (used for the "Today" list). */
fun totalsForDay(sessions: List<Session>, day: LocalDate, zone: ZoneId): Map<Long, Long> {
    val out = HashMap<Long, Long>()
    for (s in sessions) {
        for ((date, ms) in splitByDay(s.start, s.end, zone)) {
            if (date == day) out[s.projectId] = (out[s.projectId] ?: 0L) + ms
        }
    }
    return out
}
