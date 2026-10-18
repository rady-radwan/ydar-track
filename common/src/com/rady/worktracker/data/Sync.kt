package com.rady.worktracker.data

import kotlin.random.Random

/**
 * IDs that stay unique across devices: the time in microseconds plus a random part,
 * so a session made on the phone and one made on the PC never get the same number.
 */
object IdGen {
    private var last = 0L

    @Synchronized
    fun next(): Long {
        val candidate = System.currentTimeMillis() * 1000L + Random.nextInt(1000)
        last = maxOf(candidate, last + 1)
        return last
    }
}

// ---------------------------------------------------------------------------
// Sync between devices (phone and PC) through an online database.
//
// Each device remembers a "snapshot" of every record as it was at the last sync.
// On sync it:
//   1. sends what changed locally since then (new, edited and deleted records),
//   2. downloads what changed online since the last revision it has seen,
//   3. applies the downloaded changes, except to records edited during the sync
//      (those are sent next time).
// If the same record was edited on both devices, the last one to sync wins.
// ---------------------------------------------------------------------------

data class RemoteProject(val project: Project, val deleted: Boolean, val rev: Long)
data class RemoteSession(val session: Session, val deleted: Boolean, val rev: Long)
data class RemoteTimer(val timer: ActiveTimer?, val changedMs: Long, val rev: Long)

/** The online side. Implemented with Supabase's REST API; a fake one is used in tests. */
interface SyncRemote {
    fun pushProjects(rows: List<Pair<Project, Boolean>>)   // (project, deleted)
    fun pushSessions(rows: List<Pair<Session, Boolean>>)   // (session, deleted)
    fun pushTimer(timer: ActiveTimer?, changedMs: Long)
    /** All rows with revision > [afterRev], oldest first. */
    fun pullProjects(afterRev: Long): List<RemoteProject>
    fun pullSessions(afterRev: Long): List<RemoteSession>
    fun pullTimer(): RemoteTimer?
}

data class SyncState(
    val projectsRev: Long = 0L,
    val sessionsRev: Long = 0L,
    val lastSyncMs: Long = 0L,
    /** Record key ("p:12", "s:40", "t") -> fingerprint as of the last sync. */
    val snapshot: Map<String, String> = emptyMap(),
)

data class SyncResult(
    val pushed: Int,
    val pulled: Int,
    val newState: SyncState,
    /** Applies the downloaded changes to the newest local data. */
    val apply: (AppData) -> AppData,
)

object SyncKeys {
    fun p(id: Long) = "p:$id"
    fun s(id: Long) = "s:$id"
    const val TIMER = "t"

    fun fp(p: Project) = listOf(p.name, p.color, p.emoji, p.parentId ?: "", p.weeklyGoalMin, p.totalGoalMin, p.archived).joinToString("\u0001")
    fun fp(s: Session) = listOf(s.projectId, s.start, s.end, s.note).joinToString("\u0001")
    fun fp(t: ActiveTimer?) = if (t == null) "none" else listOf(t.projectId, t.start, t.targetMillis ?: "", t.note).joinToString("\u0001")

    fun snapshotOf(d: AppData): Map<String, String> {
        val m = HashMap<String, String>(d.projects.size + d.sessions.size + 1)
        d.projects.forEach { m[p(it.id)] = fp(it) }
        d.sessions.forEach { m[s(it.id)] = fp(it) }
        m[TIMER] = fp(d.active)
        return m
    }
}

object SyncEngine {
    /** Re-reads a few revisions before the last one seen, in case a write finished late. Re-reading is harmless. */
    private const val OVERLAP = 200L

    fun run(local: AppData, state: SyncState, remote: SyncRemote, now: Long): SyncResult {
        val snap = state.snapshot

        // 1. What changed here since the last sync
        val projectPush = ArrayList<Pair<Project, Boolean>>()
        local.projects.forEach { if (snap[SyncKeys.p(it.id)] != SyncKeys.fp(it)) projectPush += it to false }
        val sessionPush = ArrayList<Pair<Session, Boolean>>()
        local.sessions.forEach { if (snap[SyncKeys.s(it.id)] != SyncKeys.fp(it)) sessionPush += it to false }
        val localProjectIds = local.projects.mapTo(HashSet()) { it.id }
        val localSessionIds = local.sessions.mapTo(HashSet()) { it.id }
        for (key in snap.keys) {
            when {
                key.startsWith("p:") -> key.substring(2).toLongOrNull()?.let { id ->
                    if (id !in localProjectIds) projectPush += Project(id, "", 0) to true
                }
                key.startsWith("s:") -> key.substring(2).toLongOrNull()?.let { id ->
                    if (id !in localSessionIds) sessionPush += Session(id, 0L, 0L, 0L) to true
                }
            }
        }
        val timerChanged = state.lastSyncMs > 0L && snap[SyncKeys.TIMER] != SyncKeys.fp(local.active)
        // On the very first sync, only send a timer that is actually running.
        val timerPush = timerChanged || (state.lastSyncMs == 0L && local.active != null)

        // 2. Send them (projects first, so sessions always have their project)
        if (projectPush.isNotEmpty()) remote.pushProjects(projectPush)
        if (sessionPush.isNotEmpty()) remote.pushSessions(sessionPush)
        if (timerPush) remote.pushTimer(local.active, now)

        // 3. Download what changed online (this includes what was just sent)
        val pulledProjects = remote.pullProjects((state.projectsRev - OVERLAP).coerceAtLeast(0L))
        val pulledSessions = remote.pullSessions((state.sessionsRev - OVERLAP).coerceAtLeast(0L))
        val pulledTimer = remote.pullTimer()

        // New snapshot = local as of the start of this sync, updated with what came down.
        val newSnap = HashMap(SyncKeys.snapshotOf(local))
        for (r in pulledProjects) {
            val k = SyncKeys.p(r.project.id)
            if (r.deleted) newSnap.remove(k) else newSnap[k] = SyncKeys.fp(r.project)
        }
        for (r in pulledSessions) {
            val k = SyncKeys.s(r.session.id)
            if (r.deleted) newSnap.remove(k) else newSnap[k] = SyncKeys.fp(r.session)
        }
        val takeTimer = pulledTimer != null && !timerPush
        if (takeTimer) newSnap[SyncKeys.TIMER] = SyncKeys.fp(pulledTimer!!.timer)

        val localSnapAtStart = SyncKeys.snapshotOf(local)

        val apply: (AppData) -> AppData = { current ->
            // Records edited on this device while the sync was running keep the local version.
            fun editedDuringSync(key: String, currentFp: String?): Boolean = localSnapAtStart[key] != currentFp

            val projects = LinkedHashMap<Long, Project>()
            current.projects.forEach { projects[it.id] = it }
            for (r in pulledProjects) {
                val id = r.project.id
                val cur = projects[id]
                if (editedDuringSync(SyncKeys.p(id), cur?.let(SyncKeys::fp))) continue
                if (r.deleted) projects.remove(id) else projects[id] = r.project
            }
            val sessions = LinkedHashMap<Long, Session>()
            current.sessions.forEach { sessions[it.id] = it }
            for (r in pulledSessions) {
                val id = r.session.id
                val cur = sessions[id]
                if (editedDuringSync(SyncKeys.s(id), cur?.let(SyncKeys::fp))) continue
                if (r.deleted) sessions.remove(id) else sessions[id] = r.session
            }
            var active = current.active
            if (takeTimer && !editedDuringSync(SyncKeys.TIMER, SyncKeys.fp(current.active))) {
                active = pulledTimer!!.timer?.takeIf { t -> projects.containsKey(t.projectId) }
            }
            // Never keep a session whose project no longer exists.
            val keptSessions = sessions.values.filter { projects.containsKey(it.projectId) }
            current.copy(projects = projects.values.toList(), sessions = keptSessions, active = active)
        }

        val newState = SyncState(
            projectsRev = maxOf(state.projectsRev, pulledProjects.maxOfOrNull { it.rev } ?: 0L),
            sessionsRev = maxOf(state.sessionsRev, pulledSessions.maxOfOrNull { it.rev } ?: 0L),
            lastSyncMs = now,
            snapshot = newSnap,
        )
        return SyncResult(
            pushed = projectPush.size + sessionPush.size + (if (timerPush) 1 else 0),
            pulled = pulledProjects.size + pulledSessions.size,
            newState = newState,
            apply = apply,
        )
    }
}
