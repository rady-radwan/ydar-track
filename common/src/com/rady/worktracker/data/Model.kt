package com.rady.worktracker.data

/**
 * A thing you work on. A project with a [parentId] is a subproject
 * (one level deep: Project › Subproject). [color] is an ARGB int.
 * [emoji] is an emoji or a short label like "MEP" (may be empty).
 */
data class Project(
    val id: Long,
    val name: String,
    val color: Int,
    val emoji: String = "",
    val parentId: Long? = null,
    /** Optional weekly goal in minutes (0 = no goal). */
    val weeklyGoalMin: Int = 0,
    /** Optional total goal in minutes for the whole project, e.g. 100 h (0 = no goal). */
    val totalGoalMin: Int = 0,
    /** Finished projects are archived: hidden when starting sessions, kept in history and stats. */
    val archived: Boolean = false,
) {
    val isSub: Boolean get() = parentId != null
}

/** One finished block of work. Times are epoch milliseconds. */
data class Session(
    val id: Long,
    val projectId: Long,
    val start: Long,
    val end: Long,
    val note: String = "",
) {
    val durationMillis: Long get() = (end - start).coerceAtLeast(0L)
}

/**
 * The timer that is running right now. Only the start time is stored, so the
 * timer keeps counting even if the app or the phone is closed.
 * [targetMillis] is set for a countdown ("work for 1 hour"); null = open stopwatch.
 */
data class ActiveTimer(
    val projectId: Long,
    val start: Long,
    val note: String = "",
    val targetMillis: Long? = null,
) {
    val isCountdown: Boolean get() = targetMillis != null
    val targetEnd: Long? get() = targetMillis?.let { start + it }
}

data class AppData(
    val projects: List<Project> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val active: ActiveTimer? = null,
    /** Kept for old data files; new records use [IdGen] so IDs are unique across devices. */
    val nextId: Long = 1L,
) {
    fun project(id: Long): Project? = projects.firstOrNull { it.id == id }

    val topLevel: List<Project> get() = projects.filter { it.parentId == null }

    /** A project is archived if it, or its parent, is archived. */
    fun isArchived(id: Long): Boolean {
        val p = project(id) ?: return false
        return p.archived || (p.parentId?.let { project(it)?.archived } ?: false)
    }

    /** Projects you can start sessions on (not archived). */
    val activeTopLevel: List<Project> get() = topLevel.filter { !it.archived }
    fun activeChildren(id: Long): List<Project> = children(id).filter { !it.archived }
    val archivedTopLevel: List<Project> get() = topLevel.filter { it.archived }

    fun children(id: Long): List<Project> = projects.filter { it.parentId == id }

    /** The top-level project a project belongs to (itself if it is top-level). */
    fun rootOf(id: Long): Project? {
        val p = project(id) ?: return null
        return p.parentId?.let { project(it) } ?: p
    }

    fun rootIdOf(id: Long): Long = rootOf(id)?.id ?: id

    /** "Revit" or "BIM › Revit" for subprojects. */
    fun displayName(id: Long): String {
        val p = project(id) ?: return "Unknown"
        val parent = p.parentId?.let { project(it) }
        return if (parent != null) "${parent.name} › ${p.name}" else p.name
    }

    /** Emoji to show for a project; subprojects without their own use the parent's. */
    fun emojiOf(id: Long): String {
        val p = project(id) ?: return ""
        if (p.emoji.isNotBlank()) return p.emoji
        return p.parentId?.let { project(it)?.emoji } ?: ""
    }

    /** Projects in tree order: each parent followed by its subprojects. */
    val ordered: List<Project>
        get() = topLevel.sortedBy { it.archived }.flatMap { listOf(it) + children(it.id) }

    /** Finished sessions plus the running timer (ending [now]), for live totals. */
    fun sessionsIncludingActive(now: Long): List<Session> {
        val a = active ?: return sessions
        val end = minOf(now, a.targetEnd ?: Long.MAX_VALUE)
        if (end <= a.start) return sessions
        return sessions + Session(id = -1L, projectId = a.projectId, start = a.start, end = end, note = a.note)
    }

    /** Ends the running timer at [now] and saves it as a session (if it lasted at least a second). */
    fun closeActive(now: Long): AppData {
        val a = active ?: return this
        val end = minOf(now, a.targetEnd ?: Long.MAX_VALUE)
        if (end - a.start < 1_000L) return copy(active = null)
        val s = Session(id = IdGen.next(), projectId = a.projectId, start = a.start, end = end, note = a.note.trim())
        return copy(sessions = sessions + s, active = null)
    }

    /** If a countdown has reached its target, save it. Returns null when nothing changed. */
    fun finishCountdownIfDue(now: Long): AppData? {
        val end = active?.targetEnd ?: return null
        return if (now >= end) closeActive(end) else null
    }

    fun addProject(name: String, color: Int, emoji: String, parentId: Long?, weeklyGoalMin: Int = 0, totalGoalMin: Int = 0): Pair<AppData, Long> {
        val id = IdGen.next()
        val p = Project(id, name.trim(), color, emoji.trim(), parentId, weeklyGoalMin, totalGoalMin)
        return copy(projects = projects + p) to id
    }

    /** Deletes a project, its subprojects, and all their sessions. */
    fun deleteProject(id: Long): AppData {
        val ids = setOf(id) + children(id).map { it.id }
        return copy(
            projects = projects.filterNot { it.id in ids },
            sessions = sessions.filterNot { it.projectId in ids },
            active = active?.takeIf { it.projectId !in ids },
        )
    }
}

/** Colors offered when creating a project. */
val ProjectPalette: List<Int> = listOf(
    0xFFFFB627, // gold
    0xFFF28C28, // hi-vis orange
    0xFF4F8FD6, // blueprint blue
    0xFF2BA89A, // teal
    0xFFD0603A, // brick
    0xFF6BBF59, // site green
    0xFFB57EDC, // violet
    0xFF7FA3BF, // steel
    0xFFE0607E, // rose
    0xFFA0703F, // timber
    0xFFE0B84A, // sand
    0xFF8A9099, // concrete
).map { it.toInt() }

/** Emoji/labels offered in the project picker, grouped. */
val EmojiGroups: List<Pair<String, List<String>>> = listOf(
    "Construction" to listOf("🏗️", "🏢", "🏭", "🏠", "🧱", "🦺", "👷", "🚧", "🌉", "🚇", "🛣️", "🏚️", "🪜", "🚜", "🏛️", "⛑️", "🏥", "🏟️", "🛤️", "🪨"),
    "Engineering" to listOf("📐", "📏", "🔧", "🔩", "⚙️", "🛠️", "🔌", "⚡", "💧", "🔥", "❄️", "🌬️", "🧯", "🚿", "🔋", "🧰", "🪛", "⛓️", "🧲", "🏎️"),
    "BIM & software" to listOf("💻", "🖥️", "🧊", "🗺️", "🛰️", "🤖", "📡", "🧩", "🗂️", "📁", "🖱️", "⌨️", "💾", "🌐", "📲", "🧑‍💻"),
    "Study" to listOf("📚", "📖", "✏️", "📝", "🎓", "🧠", "🧮", "📊", "📈", "📋", "🗒️", "💡", "🎯", "⏱️", "🗣️", "🌍", "🏫", "🖊️", "📌", "🗓️"),
    "Science" to listOf("🔬", "🔭", "🧪", "⚗️", "🧬", "⚛️", "🦠", "🧫", "🌡️", "🪐", "🌋", "🌊", "🌱", "💊", "🩺", "🔎", "☢️", "🌌", "🧊", "🌿"),
    "Reading" to listOf("📕", "📗", "📘", "📙", "📓", "📔", "📒", "📰", "🔖", "📜", "🖋️", "✍️", "🗞️", "📑", "🏷️", "🧾"),
    "Sports & workout" to listOf("🏋️", "💪", "🏃", "🚴", "🏊", "🧗", "🤸", "🧘", "🚶", "⚽", "🏀", "🎾", "🏐", "🏓", "🥊", "🥋", "⛹️", "🏸", "🥇", "🏆"),
    "Other" to listOf("✈️", "🛩️", "💼", "📅", "🎨", "🎵", "🎸", "📷", "🎬", "☕", "⭐", "❤️", "🚀", "🧭", "🕌", "🍳"),
)

/** Short text labels that are handy in construction work. */
val LabelSuggestions: List<String> = listOf("MEP", "BIM", "VDC", "PMP", "RFI", "CAD", "HVAC", "Str", "Arch", "QA", "Math", "Phys", "Chem", "Eng", "GYM")
