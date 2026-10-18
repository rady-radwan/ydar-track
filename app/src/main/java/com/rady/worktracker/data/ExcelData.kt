package com.rady.worktracker.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.math.roundToLong

/*
 * Turning the app's data into an Excel workbook, and reading an Excel
 * (or CSV) file back into sessions and projects.
 */

private const val EXCEL_EPOCH_OFFSET = 25569L // days from 1899-12-30 to 1970-01-01
private const val DAY_MS = 86_400_000L

fun excelDate(d: LocalDate): Double = (d.toEpochDay() + EXCEL_EPOCH_OFFSET).toDouble()
fun excelTime(t: LocalTime): Double = t.toSecondOfDay() / 86_400.0

fun colorHex(c: Int): String = String.format(Locale.US, "#%06X", c and 0xFFFFFF)

object ExcelExport {

    private val SESSION_HEADERS = listOf("Project", "Subproject", "Date", "Start", "End", "Hours", "Minutes", "Note")

    fun workbook(data: AppData, zone: ZoneId): ByteArray {
        val sessionRows = mutableListOf<List<XCell>>()
        sessionRows += SESSION_HEADERS.map { XCell.Text(it) }
        data.sessions.sortedBy { it.start }.forEachIndexed { i, s ->
            val r = i + 2
            val p = data.project(s.projectId)
            val parent = p?.parentId?.let { data.project(it) }
            val start = Instant.ofEpochMilli(s.start).atZone(zone)
            val end = Instant.ofEpochMilli(s.end).atZone(zone)
            // Whole minutes, so Hours always matches the Start/End shown in the sheet.
            val hours = minuteDuration(s) / 3_600_000.0
            val roundedStart = start.toLocalTime().withSecond(0).withNano(0)
            val roundedEnd = end.toLocalTime().withSecond(0).withNano(0)
            sessionRows += listOf(
                XCell.Text(parent?.name ?: p?.name ?: "Unknown"),
                if (parent != null) XCell.Text(p.name) else XCell.Empty,
                XCell.Num(excelDate(start.toLocalDate()), XStyle.DATE),
                XCell.Num(excelTime(roundedStart), XStyle.TIME),
                XCell.Num(excelTime(roundedEnd), XStyle.TIME),
                // Recalculates if you edit Start or End in Excel (a negative gap means it ran past midnight).
                XCell.Formula("ROUND(MOD(E$r-D$r,1)*24,2)", Math.round(hours * 100) / 100.0, XStyle.DECIMAL2),
                XCell.Formula("ROUND(F$r*60,0)", Math.round(hours * 60).toDouble(), XStyle.INTEGER),
                if (s.note.isNotBlank()) XCell.Text(s.note) else XCell.Empty,
            )
        }

        // Summary per project and subproject
        val totals = data.sessions.groupBy { it.projectId }.mapValues { (_, l) -> l.size to l.sumOf { minuteDuration(it) } }
        val summary = mutableListOf<List<XCell>>()
        summary += listOf("Project", "Subproject", "Sessions", "Hours").map { XCell.Text(it) }
        var grandCount = 0
        var grandMs = 0L
        data.topLevel.forEach { top ->
            val ids = listOf(top.id) + data.children(top.id).map { it.id }
            val count = ids.sumOf { totals[it]?.first ?: 0 }
            val ms = ids.sumOf { totals[it]?.second ?: 0L }
            grandCount += count; grandMs += ms
            summary += listOf(XCell.Text(top.name, bold = true), XCell.Empty, XCell.Num(count.toDouble(), XStyle.INTEGER), XCell.Num(round2(ms), XStyle.BOLD_DECIMAL2))
            data.children(top.id).forEach { c ->
                val (cc, cm) = totals[c.id] ?: (0 to 0L)
                summary += listOf(XCell.Empty, XCell.Text(c.name), XCell.Num(cc.toDouble(), XStyle.INTEGER), XCell.Num(round2(cm), XStyle.DECIMAL2))
            }
            if (data.children(top.id).isNotEmpty() && (totals[top.id]?.first ?: 0) > 0) {
                val (tc, tm) = totals[top.id]!!
                summary += listOf(XCell.Empty, XCell.Text("(no subproject)"), XCell.Num(tc.toDouble(), XStyle.INTEGER), XCell.Num(round2(tm), XStyle.DECIMAL2))
            }
        }
        summary += listOf(XCell.Text("Total", bold = true), XCell.Empty, XCell.Num(grandCount.toDouble(), XStyle.INTEGER), XCell.Num(round2(grandMs), XStyle.BOLD_DECIMAL2))
        val legacy = legacyOf(data.sessions)
        val thisWeek = weekRank(data, data.sessions, java.time.LocalDate.now(zone), java.time.LocalDate.now(zone), zone)
        summary += listOf(XCell.Empty)
        summary += listOf(XCell.Text("Your legacy", bold = true), XCell.Text("${legacy.floors} floor${if (legacy.floors == 1) "" else "s"} built (1 floor = 10 hours)"), XCell.Empty, XCell.Num(round2(legacy.totalMs), XStyle.BOLD_DECIMAL2))
        summary += listOf(XCell.Text("Rank this week", bold = true), XCell.Text("${thisWeek.rank.emoji} ${thisWeek.rank.title} (level ${thisWeek.rank.number} of 10)"))

        val projects = mutableListOf<List<XCell>>()
        projects += listOf("Project", "Subproject", "Emoji", "Color", "Weekly goal (h)", "Total goal (h)", "Hours so far", "% of goal", "Archived").map { XCell.Text(it) }
        data.ordered.forEach { p ->
            val parent = p.parentId?.let { data.project(it) }
            val so = if (parent == null) projectTotal(data, data.sessions, p.id) else data.sessions.filter { it.projectId == p.id }.sumOf { minuteDuration(it) }
            projects += listOf(
                XCell.Text(parent?.name ?: p.name),
                if (parent != null) XCell.Text(p.name) else XCell.Empty,
                if (p.emoji.isNotBlank()) XCell.Text(p.emoji) else XCell.Empty,
                XCell.Text(colorHex(p.color)),
                if (p.weeklyGoalMin > 0) XCell.Num(p.weeklyGoalMin / 60.0, XStyle.DECIMAL2) else XCell.Empty,
                if (p.totalGoalMin > 0) XCell.Num(p.totalGoalMin / 60.0, XStyle.DECIMAL2) else XCell.Empty,
                XCell.Num(round2(so), XStyle.DECIMAL2),
                if (p.totalGoalMin > 0) XCell.Num(Math.round(so * 100.0 / (p.totalGoalMin * 60_000.0)).toDouble(), XStyle.INTEGER) else XCell.Empty,
                if (p.archived) XCell.Text("Yes") else XCell.Empty,
            )
        }

        // Weekly ranks, newest first
        val zoneToday = java.time.LocalDate.now(zone)
        val firstWeek = earliestDate(data.sessions, zone)?.let { weekStartOf(it) } ?: weekStartOf(zoneToday)
        val weekCount = (java.time.temporal.ChronoUnit.WEEKS.between(firstWeek, weekStartOf(zoneToday)).toInt() + 1).coerceIn(1, 520)
        val ranks = mutableListOf<List<XCell>>()
        ranks += listOf("Week of (Monday)", "Hours", "Days counted", "Average per day (h)", "Rank", "Level").map { XCell.Text(it) }
        rankHistory(data, data.sessions, zoneToday, zone, weekCount).asReversed().forEach { w ->
            ranks += listOf(
                XCell.Num(excelDate(w.weekStart), XStyle.DATE),
                XCell.Num(round2(w.total), XStyle.DECIMAL2),
                XCell.Num(w.daysElapsed.toDouble(), XStyle.INTEGER),
                XCell.Num(round2(w.avgPerDay), XStyle.DECIMAL2),
                XCell.Text("${w.rank.emoji} ${w.rank.title}", bold = w.weekStart == weekStartOf(zoneToday)),
                XCell.Num(w.rank.number.toDouble(), XStyle.INTEGER),
            )
        }

        return XlsxWriter.write(
            listOf(
                XSheet("Sessions", sessionRows, widths = listOf(24.0, 20.0, 12.0, 8.0, 8.0, 8.0, 9.0, 44.0), autoFilter = true),
                XSheet("Summary", summary, widths = listOf(24.0, 36.0, 10.0, 10.0)),
                XSheet("Projects", projects, widths = listOf(24.0, 22.0, 8.0, 10.0, 14.0, 13.0, 12.0, 10.0, 9.0)),
                XSheet("Ranks", ranks, widths = listOf(16.0, 9.0, 13.0, 18.0, 20.0, 7.0)),
            )
        )
    }

    /** An empty workbook with the right columns, plus a sheet explaining how to fill it. */
    fun template(): ByteArray {
        val help = listOf(
            listOf("How to fill the Sessions sheet"),
            listOf(""),
            listOf("Project", "Required. Created automatically if it doesn't exist yet."),
            listOf("Subproject", "Optional. Created under the project if needed."),
            listOf("Date", "Required. An Excel date, or text like 2026-10-09 or 10/9/2026."),
            listOf("Start", "Start time, e.g. 09:00 or 9:00 AM."),
            listOf("End", "End time. An end earlier than the start means it ran past midnight."),
            listOf("Hours / Minutes", "Use these instead of End if you only know how long you worked."),
            listOf("Note", "Optional."),
            listOf(""),
            listOf("Example rows"),
            listOf("Project", "Subproject", "Date", "Start", "End", "Hours", "Minutes", "Note"),
            listOf("Newcastle Medical Center", "Clash detection", "2026-10-09", "08:00", "10:30", "", "", "Level 3 MEP"),
            listOf("Part 107 study", "", "2026-10-09", "19:00", "", "1.5", "", "Airspace chapter"),
            listOf(""),
            listOf("Rows that are already in the app (same project, start and end) are skipped, so importing the same file twice is safe."),
        ).mapIndexed { i, row -> row.mapIndexed { j, s -> XCell.Text(s, bold = i == 0 || i == 10 || i == 11 || (j == 0 && i in 2..8)) } }
        return XlsxWriter.write(
            listOf(
                XSheet("Sessions", listOf(SESSION_HEADERS.map { XCell.Text(it) }), widths = listOf(24.0, 20.0, 12.0, 8.0, 8.0, 8.0, 9.0, 44.0)),
                XSheet("Projects", listOf(listOf("Project", "Subproject", "Emoji", "Color", "Weekly goal (h)", "Total goal (h)", "Archived").map { XCell.Text(it) }), widths = listOf(24.0, 22.0, 8.0, 10.0, 14.0, 13.0, 9.0)),
                XSheet("How to fill", help, widths = listOf(26.0, 60.0, 12.0, 8.0, 8.0, 8.0, 9.0, 30.0), freezeHeader = false),
            )
        )
    }

    private fun round2(ms: Long): Double = Math.round(ms / 36_000.0) / 100.0

    private fun minuteDuration(s: Session): Long = (s.end / 60_000L - s.start / 60_000L) * 60_000L
}

// ---------------------------------------------------------------- import

data class ImportIssue(val row: Int, val message: String)

data class ImportPlan(
    val result: AppData,
    val sheetName: String,
    val sessionsAdded: Int,
    val duplicatesSkipped: Int,
    val projectsCreated: List<String>,
    val issues: List<ImportIssue>,
) {
    val hasChanges: Boolean get() = sessionsAdded > 0 || projectsCreated.isNotEmpty()
}

object ExcelImport {

    private enum class Col { PROJECT, SUBPROJECT, DATE, START, END, HOURS, MINUTES, DURATION, NOTE, EMOJI, COLOR, GOAL, TOTALGOAL, ARCHIVED }

    private fun norm(s: String) = s.lowercase(Locale.ROOT).filter { it.isLetter() }

    private val HEADER_NAMES: Map<String, Col> = buildMap {
        listOf("project", "projectname", "category").forEach { put(it, Col.PROJECT) }
        listOf("subproject", "sub", "subcategory", "topic", "subtask").forEach { put(it, Col.SUBPROJECT) }
        listOf("date", "day", "workdate").forEach { put(it, Col.DATE) }
        listOf("start", "starttime", "from", "begin", "started").forEach { put(it, Col.START) }
        listOf("end", "endtime", "to", "finish", "finished", "stop").forEach { put(it, Col.END) }
        listOf("hours", "hrs", "durationhours", "durationh", "timehours", "h").forEach { put(it, Col.HOURS) }
        listOf("minutes", "mins", "min", "durationminutes", "durationmin").forEach { put(it, Col.MINUTES) }
        listOf("duration", "time", "length").forEach { put(it, Col.DURATION) }
        listOf("note", "notes", "description", "comment", "comments", "details").forEach { put(it, Col.NOTE) }
        listOf("emoji", "icon", "symbol", "label").forEach { put(it, Col.EMOJI) }
        listOf("color", "colour").forEach { put(it, Col.COLOR) }
        listOf("weeklygoalh", "weeklygoal", "goal", "goalh", "weeklygoalhours").forEach { put(it, Col.GOAL) }
        listOf("totalgoalh", "totalgoal", "totalgoalhours", "projectgoal", "goalhours").forEach { put(it, Col.TOTALGOAL) }
        listOf("archived", "archive", "finished", "done").forEach { put(it, Col.ARCHIVED) }
    }

    private fun headerMap(row: List<RCell>): Map<Col, Int> {
        val m = HashMap<Col, Int>()
        row.forEachIndexed { i, c -> HEADER_NAMES[norm(c.text)]?.let { col -> if (col !in m) m[col] = i } }
        return m
    }

    private fun findHeader(rows: List<List<RCell>>, required: Set<Col>): Pair<Int, Map<Col, Int>>? {
        for (i in 0 until minOf(rows.size, 15)) {
            val m = headerMap(rows[i])
            if (m.keys.containsAll(required)) return i to m
        }
        return null
    }

    fun plan(sheets: List<RSheet>, current: AppData, zone: ZoneId): ImportPlan {
        var data = current
        val created = mutableListOf<String>()
        val issues = mutableListOf<ImportIssue>()

        fun projectId(name: String, sub: String?, emoji: String = "", color: Int? = null): Long {
            val n = name.trim()
            val top = data.topLevel.firstOrNull { it.name.equals(n, ignoreCase = true) }
            val topId = top?.id ?: run {
                val usedColors = data.projects.map { it.color }.toSet()
                val c = color ?: ProjectPalette.firstOrNull { it !in usedColors } ?: ProjectPalette[data.projects.size % ProjectPalette.size]
                val (d, id) = data.addProject(n, c, emoji, null)
                data = d
                created += n
                id
            }
            if (top != null && emoji.isNotBlank() && top.emoji.isBlank()) {
                data = data.copy(projects = data.projects.map { if (it.id == topId) it.copy(emoji = emoji) else it })
            }
            val s = sub?.trim().orEmpty()
            if (s.isEmpty()) return topId
            val existing = data.children(topId).firstOrNull { it.name.equals(s, ignoreCase = true) }
            if (existing != null) return existing.id
            val parent = data.project(topId)!!
            val (d, id) = data.addProject(s, color ?: parent.color, "", topId)
            data = d
            created += "$n › $s"
            return id
        }

        // 1) Projects sheet (optional): restores emoji and colors.
        sheets.firstOrNull { norm(it.name) == "projects" }?.let { sh ->
            val (hi, cols) = findHeader(sh.rows, setOf(Col.PROJECT)) ?: return@let
            for (r in hi + 1 until sh.rows.size) {
                val row = sh.rows[r]
                val name = cell(row, cols[Col.PROJECT])?.text?.trim().orEmpty()
                if (name.isEmpty()) continue
                val sub = cell(row, cols[Col.SUBPROJECT])?.text?.trim()?.takeIf { it.isNotEmpty() }
                val emoji = cell(row, cols[Col.EMOJI])?.text?.trim().orEmpty()
                val color = cell(row, cols[Col.COLOR])?.text?.let { parseColor(it) }
                val goalMin = cell(row, cols[Col.GOAL])?.let { c -> (c.number ?: c.text.trim().replace(',', '.').toDoubleOrNull())?.let { (it * 60).roundToLong().toInt() } } ?: 0
                val id = if (sub == null) projectId(name, null, emoji, color) else projectId(name, sub, "", color)
                if (sub != null && emoji.isNotBlank()) data = data.copy(projects = data.projects.map { if (it.id == id && it.emoji.isBlank()) it.copy(emoji = emoji) else it })
                if (goalMin > 0) data = data.copy(projects = data.projects.map { if (it.id == id && it.weeklyGoalMin == 0) it.copy(weeklyGoalMin = goalMin) else it })
                val totalMin = cell(row, cols[Col.TOTALGOAL])?.let { c -> (c.number ?: c.text.trim().replace(',', '.').toDoubleOrNull())?.let { (it * 60).roundToLong().toInt() } } ?: 0
                if (totalMin > 0) data = data.copy(projects = data.projects.map { if (it.id == id && it.totalGoalMin == 0) it.copy(totalGoalMin = totalMin) else it })
                val arch = cell(row, cols[Col.ARCHIVED])?.text?.trim()?.lowercase(Locale.ROOT) in setOf("yes", "y", "true", "1", "x", "archived")
                if (arch) data = data.copy(projects = data.projects.map { if (it.id == id) it.copy(archived = true) else it })
            }
        }

        // 2) Sessions
        val candidates = sheets.sortedByDescending { norm(it.name) == "sessions" }
        var used: RSheet? = null
        var header: Pair<Int, Map<Col, Int>>? = null
        for (sh in candidates) {
            val h = findHeader(sh.rows, setOf(Col.PROJECT, Col.DATE))
            if (h != null) { used = sh; header = h; break }
        }
        if (used == null || header == null) {
            val projectsOnly = created.isNotEmpty()
            if (!projectsOnly) issues += ImportIssue(0, "No sheet with \"Project\" and \"Date\" columns was found.")
            return ImportPlan(data, sheets.firstOrNull()?.name ?: "", 0, 0, created, issues)
        }

        val (hi, cols) = header
        var added = 0
        var dupes = 0
        val existing = data.sessions.map { Triple(it.projectId, it.start / 60_000L, it.end / 60_000L) }.toHashSet()

        for (r in hi + 1 until used.rows.size) {
            val row = used.rows[r]
            val excelRow = r + 1
            if (row.all { it.isBlank }) continue
            val name = cell(row, cols[Col.PROJECT])?.text?.trim().orEmpty()
            if (name.isEmpty()) { issues += ImportIssue(excelRow, "Missing project name"); continue }

            val dateCell = cell(row, cols[Col.DATE])
            val startCell = cell(row, cols[Col.START])
            val endCell = cell(row, cols[Col.END])
            val startDT = parseDateTime(startCell)
            val date = parseDate(dateCell) ?: startDT?.toLocalDate()
            if (date == null) { issues += ImportIssue(excelRow, "Couldn't read the date \"${dateCell?.text.orEmpty()}\""); continue }

            val startTime = startDT?.toLocalTime() ?: parseTime(startCell)
            val endTime = parseTime(endCell)
            val durationMs = parseDurationMs(row, cols)

            val start: LocalDateTime
            val end: LocalDateTime
            when {
                startTime != null && endTime != null -> {
                    start = date.atTime(startTime)
                    var e = parseDateTime(endCell) ?: date.atTime(endTime)
                    if (!e.isAfter(start)) e = e.plusDays(1)
                    end = e
                }
                startTime != null && durationMs != null -> {
                    start = date.atTime(startTime)
                    end = start.plusNanos(durationMs * 1_000_000L)
                }
                endTime != null && durationMs != null -> {
                    end = date.atTime(endTime)
                    start = end.minusNanos(durationMs * 1_000_000L)
                }
                durationMs != null -> {
                    start = date.atTime(9, 0)
                    end = start.plusNanos(durationMs * 1_000_000L)
                }
                else -> { issues += ImportIssue(excelRow, "Needs a start and end time, or a duration"); continue }
            }
            val startMs = start.atZone(zone).toInstant().toEpochMilli()
            val endMs = end.atZone(zone).toInstant().toEpochMilli()
            if (endMs <= startMs) { issues += ImportIssue(excelRow, "End is not after start"); continue }
            if (endMs - startMs > 7 * DAY_MS) { issues += ImportIssue(excelRow, "Longer than 7 days — check the times"); continue }

            val sub = cell(row, cols[Col.SUBPROJECT])?.text?.trim()?.takeIf { it.isNotEmpty() }
            val pid = projectId(name, sub)
            val key = Triple(pid, startMs / 60_000L, endMs / 60_000L)
            if (key in existing) { dupes++; continue }
            existing += key
            val note = cell(row, cols[Col.NOTE])?.text?.trim().orEmpty()
            data = data.copy(sessions = data.sessions + Session(IdGen.next(), pid, startMs, endMs, note))
            added++
        }
        return ImportPlan(data, used.name, added, dupes, created, issues)
    }

    private fun cell(row: List<RCell>, index: Int?): RCell? = index?.let { row.getOrNull(it) }?.takeIf { !it.isBlank }

    // ---- value parsing

    private val DATE_FORMATS: List<DateTimeFormatter> = listOf(
        "yyyy-M-d", "yyyy/M/d", "M/d/yyyy", "M/d/yy", "d.M.yyyy", "d-MMM-yyyy", "d MMM yyyy", "MMM d, yyyy", "MMMM d, yyyy",
    ).map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.US) }

    fun parseDate(c: RCell?): LocalDate? {
        if (c == null) return null
        c.number?.let { n ->
            if (n >= 1.0 && n < 200_000.0) return LocalDate.ofEpochDay(Math.floor(n).toLong() - EXCEL_EPOCH_OFFSET)
        }
        val t = c.text.trim()
        if (t.isEmpty()) return null
        val datePart = t.substringBefore('T').substringBefore(' ').let { if (it.contains(',')) t else it }
        for (f in DATE_FORMATS) {
            runCatching { return LocalDate.parse(datePart, f) }
            runCatching { return LocalDate.parse(t, f) }
        }
        return null
    }

    private val TIME_RE = Regex("""^\s*(\d{1,2})(?::(\d{2}))?(?::(\d{2}))?\s*([AaPp][Mm])?\s*$""")

    fun parseTime(c: RCell?): LocalTime? {
        if (c == null) return null
        c.number?.let { n ->
            val frac = n - Math.floor(n)
            if (n >= 0 && (n < 1.0 || frac > 0)) {
                val secs = (frac * 86_400).roundToLong().coerceIn(0, 86_399)
                return LocalTime.ofSecondOfDay(secs)
            }
        }
        val t = c.text.trim()
        val timePart = if (t.contains('T')) t.substringAfter('T') else if (t.count { it == ' ' } >= 1 && t.contains('-')) t.substringAfter(' ') else t
        val m = TIME_RE.matchEntire(timePart) ?: return null
        if (m.groupValues[2].isEmpty() && m.groupValues[4].isEmpty()) return null // a bare number isn't a time
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val sec = m.groupValues[3].ifEmpty { "0" }.toInt()
        val ampm = m.groupValues[4].lowercase(Locale.ROOT)
        if (ampm == "pm" && h < 12) h += 12
        if (ampm == "am" && h == 12) h = 0
        if (h > 23 || min > 59 || sec > 59) return null
        return LocalTime.of(h, min, sec)
    }

    /** A start cell holding a full date+time (e.g. 45939.375 or "2026-10-09 09:00"). */
    private fun parseDateTime(c: RCell?): LocalDateTime? {
        if (c == null) return null
        c.number?.let { n ->
            if (n >= 1.0 && n - Math.floor(n) > 0) {
                val d = parseDate(c) ?: return null
                val t = parseTime(c) ?: return null
                return d.atTime(t)
            }
            return null
        }
        val t = c.text.trim()
        if (!(t.contains('T') || (t.contains(' ') && t.contains(':')))) return null
        val d = parseDate(c) ?: return null
        val time = parseTime(c) ?: return null
        return d.atTime(time)
    }

    private fun parseDurationMs(row: List<RCell>, cols: Map<Col, Int>): Long? {
        cell(row, cols[Col.HOURS])?.let { c ->
            hoursFrom(c)?.let { return (it * 3_600_000).roundToLong() }
        }
        cell(row, cols[Col.MINUTES])?.let { c ->
            (c.number ?: c.text.trim().replace(',', '.').toDoubleOrNull())?.let { return (it * 60_000).roundToLong() }
        }
        cell(row, cols[Col.DURATION])?.let { c ->
            hoursFrom(c)?.let { return (it * 3_600_000).roundToLong() }
        }
        return null
    }

    /** "1.5", 1.5, "1:30", "1h 30m", "90m" → hours. */
    private fun hoursFrom(c: RCell): Double? {
        c.number?.let { return it }
        val t = c.text.trim().lowercase(Locale.ROOT)
        if (t.isEmpty()) return null
        Regex("""^(\d+):(\d{1,2})(?::(\d{1,2}))?$""").matchEntire(t)?.let { m ->
            return m.groupValues[1].toDouble() + m.groupValues[2].toDouble() / 60 + (m.groupValues[3].ifEmpty { "0" }.toDouble() / 3600)
        }
        Regex("""^(?:(\d+(?:[.,]\d+)?)\s*h(?:ours?|rs?)?)?\s*(?:(\d+)\s*m(?:in(?:utes?)?)?)?$""").matchEntire(t)?.let { m ->
            if (m.groupValues[1].isEmpty() && m.groupValues[2].isEmpty()) return null
            return (m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0) + (m.groupValues[2].toDoubleOrNull() ?: 0.0) / 60
        }
        return t.replace(',', '.').toDoubleOrNull()
    }

    private fun parseColor(s: String): Int? {
        val t = s.trim().removePrefix("#")
        if (t.length != 6) return null
        return t.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
    }
}
