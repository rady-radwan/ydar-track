package com.rady.worktracker.data

enum class ThemeMode(val label: String) { SYSTEM("Auto"), LIGHT("Light"), DARK("Dark") }

/** Google fonts bundled with the app. */
enum class AppFont(val label: String) {
    GOOGLE_SANS("Google Sans"), INTER("Inter"), ROBOTO("Roboto"), MONTSERRAT("Montserrat"),
    OPEN_SANS("Open Sans"), WORK_SANS("Work Sans"), LATO("Lato"), JOSEFIN_SANS("Josefin Sans"),
}

enum class BackupFrequency(val label: String) { OFF("Off"), DAILY("Every day"), WEEKLY("Every week") }

/** App preferences (kept separately from your tracked data). */
data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    val dailyGoalMin: Int = 120,
    /** Open timer: chime every [lapMin] minutes. */
    val lapOn: Boolean = true,
    val lapMin: Int = 30,
    /** Countdown alerts. */
    val halfOn: Boolean = true,
    val fiveOn: Boolean = true,
    val endOn: Boolean = true,
    /** Rank pop-up when the app opens. */
    val rankPopup: Boolean = true,
    val rankSound: Boolean = true,
    val font: AppFont = AppFont.GOOGLE_SANS,
    /** Google Drive backup. */
    val driveConnected: Boolean = false,
    val driveEmail: String? = null,
    val backupFrequency: BackupFrequency = BackupFrequency.OFF,
    val backupWifiOnly: Boolean = false,
    val keepBackups: Int = 10,
    val lastBackupAt: Long = 0L,
    val lastBackupStatus: String = "",
    /** Last timer mode and countdown length picked on the Track screen. */
    val countdownMode: Boolean = false,
    val countdownMinutes: Int = 60,
)

enum class TimerEvent { LAP, HALF, FIVE_LEFT, END }

/**
 * The next alert for a running timer after time [after], or null if none.
 *  - Open timer: a lap chime every [Settings.lapMin] minutes (if on).
 *  - Countdown: halfway (4 min or longer), 5 minutes left (over 10 min), and the end.
 *    The end is always returned because it also saves the session.
 */
fun nextTimerEvent(a: ActiveTimer, s: Settings, after: Long): Pair<Long, TimerEvent>? {
    val target = a.targetMillis
    if (target == null) {
        if (!s.lapOn || s.lapMin <= 0) return null
        val lap = s.lapMin * 60_000L
        val k = ((after - a.start).coerceAtLeast(0L) / lap) + 1
        return (a.start + k * lap) to TimerEvent.LAP
    }
    return countdownMilestones(a, s).firstOrNull { it.first > after }
}

/** Halfway / 5-minutes-left / end times for a countdown, in order (used for alerts and the dial marks). */
fun countdownMilestones(a: ActiveTimer, s: Settings): List<Pair<Long, TimerEvent>> {
    val t = a.targetMillis ?: return emptyList()
    val end = a.start + t
    val list = mutableListOf<Pair<Long, TimerEvent>>()
    if (s.halfOn && t >= 4 * 60_000L) list += (a.start + t / 2) to TimerEvent.HALF
    if (s.fiveOn && t > 10 * 60_000L) list += (end - 5 * 60_000L) to TimerEvent.FIVE_LEFT
    list += end to TimerEvent.END
    return list.sortedBy { it.first }
}
