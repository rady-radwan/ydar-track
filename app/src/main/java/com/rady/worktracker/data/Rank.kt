package com.rady.worktracker.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Weekly ranks — the journey of building yourself.
 * Your rank comes from this week's average per day (Monday to today),
 * so it starts fresh every Monday.
 */
data class Rank(
    /** 0-based index; shown to people as [number] (1–10). */
    val level: Int,
    val title: String,
    val emoji: String,
    /** Average minutes per day needed for this rank. */
    val minPerDay: Int,
    /** ARGB color for the badge. */
    val color: Int,
    /** What the rank means. */
    val tagline: String,
    /** Shown when you reach this rank. */
    val message: String,
) {
    val number: Int get() = level + 1
}

val Ranks: List<Rank> = listOf(
    Rank(0, "Site Tourist", "🚧", 0, 0xFF8A9099.toInt(),
        "You're visiting the construction site. Time to get started!",
        "Every skyline started as an empty lot. Clock in and break ground."),
    Rank(1, "Blueprint Rookie", "📐", 30, 0xFF4F8FD6.toInt(),
        "You have a vision. Now you're putting it into action.",
        "The plan is on paper. Now every hour turns lines into walls."),
    Rank(2, "Groundbreaker", "⛏️", 60, 0xFFA0703F.toInt(),
        "You've broken ground. Your journey has begun.",
        "Every great structure begins with breaking ground. You've made your start."),
    Rank(3, "Foundation Builder", "🧱", 90, 0xFFB5AFA4.toInt(),
        "You're building the habits that support your future.",
        "Strong foundations are poured slowly. Yours is setting in."),
    Rank(4, "Bricklayer", "🔨", 120, 0xFFD0603A.toInt(),
        "Brick by brick, your effort is taking shape.",
        "One brick at a time. One hour at a time. You're building something that lasts."),
    Rank(5, "Steel Framer", "🏗️", 150, 0xFF7FA3BF.toInt(),
        "Your structure is rising. You're getting stronger.",
        "The steel is up. Everyone can see you're building something big."),
    Rank(6, "Site Engineer", "👷", 180, 0xFFF28C28.toInt(),
        "You're working with precision, discipline, and purpose.",
        "Precision, discipline, purpose. You're running this site now."),
    Rank(7, "Master Builder", "🏢", 210, 0xFFE0B84A.toInt(),
        "You're turning consistency into remarkable progress.",
        "What once was just a blueprint is now becoming reality."),
    Rank(8, "Skyline Architect", "🌆", 240, 0xFFB57EDC.toInt(),
        "Your achievements are beginning to define the skyline.",
        "Your work is changing the skyline. Keep raising it."),
    Rank(9, "Skyscraper Legend", "👑", 300, 0xFFFFC830.toInt(),
        "You've reached incredible heights. Keep building your legacy.",
        "You didn't just dream of reaching new heights. You built your way there."),
)

fun rankFor(avgPerDayMs: Long): Rank {
    val minutes = avgPerDayMs / 60_000L
    return Ranks.last { minutes >= it.minPerDay }
}

data class WeekRank(
    val weekStart: LocalDate,
    val total: Long,
    /** Days of this week that have happened (Monday..today), 1–7. */
    val daysElapsed: Int,
    val rank: Rank,
    val next: Rank?,
) {
    val avgPerDay: Long get() = if (daysElapsed == 0) 0L else total / daysElapsed
    val daysLeft: Int get() = 7 - daysElapsed
    /** How many more minutes per day (on average) are needed for the next rank. */
    val gapPerDay: Long get() = next?.let { (it.minPerDay * 60_000L - avgPerDay).coerceAtLeast(0L) } ?: 0L
    /** 0..1 progress from this rank's threshold to the next one. */
    val progress: Float
        get() {
            val n = next ?: return 1f
            val lo = rank.minPerDay * 60_000f
            val hi = n.minPerDay * 60_000f
            return ((avgPerDay - lo) / (hi - lo)).coerceIn(0f, 1f)
        }

    /**
     * How much more you'd need to work TODAY to hold [target] as your average
     * by the end of today. 0 if you're already there.
     */
    fun neededToday(target: Rank): Long =
        (target.minPerDay * 60_000L * daysElapsed - total).coerceAtLeast(0L)

    /** Same, spread over the days left this week including today. */
    fun neededPerRemainingDay(target: Rank): Long {
        val days = 7 - daysElapsed + 1
        return ((target.minPerDay * 60_000L * 7 - total) / days).coerceAtLeast(0L)
    }
}

fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Rank for the week containing [day]. For past weeks all 7 days count. */
fun weekRank(data: AppData, sessions: List<Session>, day: LocalDate, today: LocalDate, zone: ZoneId): WeekRank {
    val start = weekStartOf(day)
    val end = start.plusDays(6)
    val s = computeStats(data, sessions, start, end, today, zone, forceGranularity = Granularity.YEAR)
    val elapsed = if (today.isAfter(end)) 7 else (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(1, 7)
    val avg = s.total / elapsed
    val r = rankFor(avg)
    return WeekRank(start, s.total, elapsed, r, Ranks.getOrNull(r.level + 1))
}

/** The last [weeks] weeks, oldest first, ending with the current week. */
fun rankHistory(data: AppData, sessions: List<Session>, today: LocalDate, zone: ZoneId, weeks: Int = 12): List<WeekRank> =
    (weeks - 1 downTo 0).map { w -> weekRank(data, sessions, weekStartOf(today).minusWeeks(w.toLong()), today, zone) }

/** "Work 1h 25m more today to become 🔨 Bricklayer." */
fun rankUpMessage(w: WeekRank): String {
    val n = w.next ?: return "You're at the top of the skyline. Hold it until Sunday!"
    val today = w.neededToday(n)
    return if (w.daysLeft > 0)
        "Work ${formatDuration(today)} more today to become ${n.emoji} ${n.title}" +
            " — or ${formatDuration(w.neededPerRemainingDay(n))} a day until Sunday."
    else
        "Work ${formatDuration(today)} more today to finish the week as ${n.emoji} ${n.title}."
}

// ---------------------------------------------------------------- Legacy

/**
 * Lifetime progress that never resets: every 10 hours you've ever tracked
 * adds one floor to your tower.
 */
data class Legacy(val totalMs: Long) {
    val hours: Double get() = totalMs / 3_600_000.0
    val floors: Int get() = (totalMs / (10 * 3_600_000L)).toInt()
    /** 0..1 progress toward the next floor. */
    val floorProgress: Float get() = ((totalMs % (10 * 3_600_000L)).toFloat() / (10 * 3_600_000f))
    val nextMilestoneHours: Int get() = LegacyMilestones.firstOrNull { it > hours } ?: ((hours / 10_000).toInt() + 1) * 10_000
    val toNextMilestone: Long get() = (nextMilestoneHours * 3_600_000L - totalMs).coerceAtLeast(0L)
}

val LegacyMilestones = listOf(10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000)

fun legacyOf(sessions: List<Session>): Legacy = Legacy(sessions.sumOf { it.durationMillis })
