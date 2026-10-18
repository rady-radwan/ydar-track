package com.rady.worktracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * End-to-end check against the real Supabase project with a test account:
 * a "phone" and a "PC" sync through the online database.
 * Runs only on GitHub (YDAR_LIVE_TEST=1).
 */
class CloudLiveTest {
    private class Dev {
        val sync = CloudSync(Files.createTempDirectory("ydar").toFile())
        var data = AppData()
        fun run(): SyncResult = sync.syncNow({ data }) { t -> data = t(data) }
    }

    private fun same(a: AppData, b: AppData) =
        a.projects.sortedBy { it.id } == b.projects.sortedBy { it.id } &&
            a.sessions.sortedBy { it.id } == b.sessions.sortedBy { it.id } && a.active == b.active

    @Test fun phoneAndPcSync() {
        if (System.getenv("YDAR_LIVE_TEST") != "1") return
        val email = "ci-sync-test@ydartrack.invalid"
        val pw = "Ci-Test-7731-Sync"
        val phone = Dev(); val pc = Dev()
        assertNull(phone.sync.signIn(email, pw, create = false))
        assertNull(pc.sync.signIn(email, pw, create = false))

        // Start from whatever the account has, then add new data on the phone
        phone.run(); pc.run()
        val (d1, proj) = phone.data.addProject("CI Project ${System.currentTimeMillis()}", -18905, "🏗️", null, totalGoalMin = 6000)
        val (d2, sub) = d1.addProject("CI Sub", -18905, "", proj)
        phone.data = d2.copy(sessions = d2.sessions + Session(IdGen.next(), sub, 1_000_000, 4_600_000, "ci note ✓"))
        val r1 = phone.run()
        println("phone pushed ${r1.pushed}")
        val r2 = pc.run()
        println("pc pulled ${r2.pulled}")
        assertTrue("pc received the new project", pc.data.project(proj)?.totalGoalMin == 6000)
        assertTrue("pc received the session with note", pc.data.sessions.any { it.projectId == sub && it.note == "ci note ✓" })
        assertTrue("same data", same(phone.data, pc.data))

        // Timer on the PC shows on the phone; stopping it on the phone makes a session on the PC
        pc.data = pc.data.copy(active = ActiveTimer(sub, 9_000_000, "timer", targetMillis = 3_600_000))
        pc.run(); phone.run()
        assertEquals(sub, phone.data.active?.projectId)
        assertEquals(3_600_000L, phone.data.active?.targetMillis)
        phone.data = phone.data.closeActive(9_600_000); phone.run(); pc.run()
        assertNull(pc.data.active)
        assertTrue(pc.data.sessions.any { it.start == 9_000_000L && it.end == 9_600_000L })

        // Archive on phone, delete on PC
        phone.data = phone.data.copy(projects = phone.data.projects.map { if (it.id == proj) it.copy(archived = true) else it })
        phone.run(); pc.run()
        assertTrue(pc.data.project(proj)?.archived == true)
        pc.data = pc.data.deleteProject(proj); pc.run(); phone.run()
        assertNull(phone.data.project(proj))
        assertTrue(phone.data.sessions.none { it.projectId == proj || it.projectId == sub })
        assertTrue("same data at the end", same(phone.data, pc.data))

        // Idle sync sends nothing; a token refresh works
        assertEquals(0, phone.run().pushed)
        val s = phone.sync.session!!
        val refreshed = CloudAuth.refresh(s)
        assertTrue(refreshed.accessToken.isNotBlank())

        // Wrong password gives a friendly message
        val err = runCatching { CloudAuth.signIn(email, "wrong-password") }.exceptionOrNull()
        println("wrong password -> ${err?.message}")
        assertEquals("Wrong email or password.", err?.message)
        println("LIVE SYNC TEST PASSED")
    }
}
