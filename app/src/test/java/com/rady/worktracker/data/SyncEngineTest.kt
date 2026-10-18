package com.rady.worktracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

private var failures = 0
private fun check(name: String, cond: Boolean) { println((if (cond) "PASS " else "FAIL ") + name); if (!cond) failures++ }

class FakeRemote : SyncRemote {
    var rev = 0L
    val projects = HashMap<Long, RemoteProject>()
    val sessions = HashMap<Long, RemoteSession>()
    var timer: RemoteTimer? = null
    override fun pushProjects(rows: List<Pair<Project, Boolean>>) { rows.forEach { (p, del) ->
        val old = projects[p.id]
        projects[p.id] = RemoteProject(if (del && old != null) old.project else p, del, ++rev) } }
    override fun pushSessions(rows: List<Pair<Session, Boolean>>) { rows.forEach { (s, del) ->
        val old = sessions[s.id]
        sessions[s.id] = RemoteSession(if (del && old != null) old.session else s, del, ++rev) } }
    override fun pushTimer(timer: ActiveTimer?, changedMs: Long) { this.timer = RemoteTimer(timer, changedMs, ++rev) }
    override fun pullProjects(afterRev: Long) = projects.values.filter { it.rev > afterRev }.sortedBy { it.rev }
    override fun pullSessions(afterRev: Long) = sessions.values.filter { it.rev > afterRev }.sortedBy { it.rev }
    override fun pullTimer() = timer
}

class Device(val remote: SyncRemote) {
    var data = AppData()
    var state = SyncState()
    var clock = 1_000_000L
    fun sync(duringSync: ((AppData) -> AppData)? = null): SyncResult {
        clock += 1000
        val r = SyncEngine.run(data, state, remote, clock)
        if (duringSync != null) data = duringSync(data)
        data = r.apply(data); state = r.newState
        return r
    }
}

fun sameContent(a: AppData, b: AppData) =
    a.projects.sortedBy { it.id } == b.projects.sortedBy { it.id } &&
    a.sessions.sortedBy { it.id } == b.sessions.sortedBy { it.id } && a.active == b.active

class SyncEngineTest {
    @Test fun twoDevicesStayInSync() {
    val remote = FakeRemote()
    val phone = Device(remote); val pc = Device(remote)

    // Phone has old data with small ids (from before sync existed)
    phone.data = AppData(
        projects = listOf(Project(1, "Studying", 1, "💻"), Project(2, "PE", 2, "", parentId = 1), Project(3, "Gym", 3)),
        sessions = listOf(Session(4, 1, 100, 200), Session(5, 2, 300, 500, "thermo"), Session(6, 3, 600, 700)),
        nextId = 7,
    )
    val r1 = phone.sync()
    check("first phone sync uploads everything", r1.pushed == 6 && remote.projects.size == 3 && remote.sessions.size == 3)
    val r2 = pc.sync()
    check("pc gets everything", sameContent(phone.data, pc.data) && r2.pushed == 0)

    // Edits on both sides
    val (pcData, newP) = pc.data.addProject("Revit", 9, "🏗️", null); pc.data = pcData
    pc.data = pc.data.copy(sessions = pc.data.sessions + Session(IdGen.next(), newP, 1000, 2000))
    phone.data = phone.data.copy(projects = phone.data.projects.map { if (it.id == 3L) it.copy(archived = true) else it })
    phone.data = phone.data.copy(sessions = phone.data.sessions.filterNot { it.id == 4L })   // delete a session
    phone.sync(); pc.sync(); phone.sync()
    check("both edits merged", sameContent(phone.data, pc.data))
    check("archive arrived on pc", pc.data.project(3)?.archived == true)
    check("deletion arrived on pc", pc.data.sessions.none { it.id == 4L })
    check("new project arrived on phone", phone.data.project(newP)?.name == "Revit" && phone.data.sessions.any { it.projectId == newP })
    check("new ids are unique and big", newP > 1_000_000_000L)

    // Delete a project with its subproject + sessions on the PC
    pc.data = pc.data.deleteProject(1); pc.sync(); phone.sync()
    check("project deletion cascades to phone", phone.data.project(1) == null && phone.data.project(2) == null &&
        phone.data.sessions.none { it.projectId == 1L || it.projectId == 2L } && sameContent(phone.data, pc.data))

    // Timer started on phone shows on PC; stopped on PC becomes a session on phone
    phone.data = phone.data.copy(active = ActiveTimer(3, 50_000, "lifting"))
    phone.sync(); pc.sync()
    check("running timer shows on pc", pc.data.active?.projectId == 3L && pc.data.active?.start == 50_000L)
    pc.data = pc.data.closeActive(80_000); pc.sync(); phone.sync()
    check("timer stopped on pc -> phone timer cleared and session saved", phone.data.active == null &&
        phone.data.sessions.any { it.start == 50_000L && it.end == 80_000L } && sameContent(phone.data, pc.data))

    // Same record edited on both: last to sync wins, nothing lost elsewhere
    phone.data = phone.data.copy(projects = phone.data.projects.map { if (it.id == 3L) it.copy(name = "Gym A") else it })
    pc.data = pc.data.copy(projects = pc.data.projects.map { if (it.id == 3L) it.copy(name = "Gym B") else it })
    phone.sync(); pc.sync(); phone.sync()
    check("conflict: last sync wins on both", phone.data.project(3)?.name == "Gym B" && pc.data.project(3)?.name == "Gym B")

    // An edit made while a sync is running is kept and sent next time
    val r = phone.sync { d -> d.copy(sessions = d.sessions + Session(777, 3, 5, 9, "during")) }
    check("edit during sync kept locally", phone.data.sessions.any { it.id == 777L })
    phone.sync(); pc.sync()
    check("edit during sync sent next time", pc.data.sessions.any { it.id == 777L })

    // Nothing changed -> nothing pushed
    val quiet = phone.sync()
    check("idle sync pushes nothing", quiet.pushed == 0)

    // A third device (fresh install) gets the same data
    val tablet = Device(remote); tablet.sync()
    check("fresh device matches", sameContent(tablet.data, phone.data))

    // Reinstall with data restored (no sync state): no deletions, merges
    val re = Device(remote); re.data = phone.data.copy(sessions = phone.data.sessions + Session(888, 3, 1, 2)); re.sync(); phone.sync()
    check("reinstalled device merges without deleting", phone.data.sessions.any { it.id == 888L } && phone.data.sessions.size == re.data.sessions.size)

    assertEquals("sync checks failed", 0, failures)
    }
}
