package com.rady.worktracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.rady.worktracker.data.ActiveTimer
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.AppSettings
import com.rady.worktracker.data.Settings
import com.rady.worktracker.data.ProjectPalette
import com.rady.worktracker.data.Repo
import com.rady.worktracker.data.Session
import com.rady.worktracker.timer.TimerSync
import kotlinx.coroutines.flow.StateFlow

class TrackerViewModel(private val app: Application) : AndroidViewModel(app) {

    init { Repo.init(app); AppSettings.init(app) }

    val data: StateFlow<AppData> = Repo.data

    private fun change(transform: (AppData) -> AppData) = Repo.update(transform)

    /** Changes that affect the running timer also refresh its notification and alarm. */
    private fun changeTimer(transform: (AppData) -> AppData) {
        Repo.update(transform)
        TimerSync.sync(app, Repo.current)
    }

    // ---- Projects ----

    fun nextFreeColor(): Int {
        val used = Repo.current.topLevel.map { it.color }.toSet()
        return ProjectPalette.firstOrNull { it !in used }
            ?: ProjectPalette[Repo.current.projects.size % ProjectPalette.size]
    }

    fun addProject(name: String, color: Int, emoji: String, parentId: Long?, weeklyGoalMin: Int, totalGoalMin: Int = 0) =
        change { d -> d.addProject(name, color, emoji, parentId, weeklyGoalMin, totalGoalMin).first }

    fun updateProject(
        id: Long, name: String, color: Int, emoji: String, parentId: Long?,
        weeklyGoalMin: Int, totalGoalMin: Int, archived: Boolean,
    ) = changeTimer { d ->
        d.copy(projects = d.projects.map {
            if (it.id == id) it.copy(
                name = name.trim(), color = color, emoji = emoji.trim(), parentId = parentId,
                weeklyGoalMin = weeklyGoalMin, totalGoalMin = totalGoalMin, archived = archived,
            ) else it
        })
    }

    /** Archived projects are kept (with all their hours) but hidden from the start list. */
    fun setArchived(id: Long, archived: Boolean) = change { d ->
        d.copy(projects = d.projects.map { if (it.id == id) it.copy(archived = archived) else it })
    }

    // ---- Settings ----

    val settings: StateFlow<Settings> = AppSettings.flow

    /** Saves a settings change and re-plans the running timer's alerts. */
    fun updateSettings(transform: (Settings) -> Settings) {
        AppSettings.update(transform)
        TimerSync.sync(app, Repo.current)
    }

    /** Deletes the project, its subprojects and all their sessions. */
    fun deleteProject(id: Long) = changeTimer { d -> d.deleteProject(id) }

    // ---- Timer ----

    /** Starts a timer ([targetMillis] = countdown length, null = open stopwatch).
     *  If another one is running, it is saved first (switching projects). */
    fun startTimer(projectId: Long, targetMillis: Long?) = changeTimer { d ->
        val now = System.currentTimeMillis()
        d.closeActive(now).copy(active = ActiveTimer(projectId, now, targetMillis = targetMillis))
    }

    fun stopTimer() = changeTimer { d -> d.closeActive(System.currentTimeMillis()) }

    fun discardTimer() = changeTimer { d -> d.copy(active = null) }

    fun setActiveNote(note: String) = change { d -> d.copy(active = d.active?.copy(note = note)) }

    /** For when you forgot to press start. */
    fun setActiveStart(start: Long) = changeTimer { d ->
        val now = System.currentTimeMillis()
        d.copy(active = d.active?.copy(start = start.coerceAtMost(now)))
    }

    /** Adds time to a running countdown (e.g. "+15 min"). */
    fun extendCountdown(extraMs: Long) = changeTimer { d ->
        val a = d.active ?: return@changeTimer d
        val t = a.targetMillis ?: return@changeTimer d
        d.copy(active = a.copy(targetMillis = t + extraMs))
    }

    /** Called every second while the app is open: saves a countdown that has reached its goal. */
    fun checkCountdown() {
        val before = Repo.current.active ?: return
        val end = before.targetEnd ?: return
        if (System.currentTimeMillis() < end) return
        Repo.update { it.finishCountdownIfDue(System.currentTimeMillis()) ?: it }
        TimerSync.notifyDone(app, Repo.current, before.projectId, before.targetMillis!!)
        TimerSync.sync(app, Repo.current)
    }

    fun refreshTimerNotification() = TimerSync.sync(app, Repo.current)

    // ---- Sessions ----

    fun addSession(projectId: Long, start: Long, end: Long, note: String) = change { d ->
        d.copy(sessions = d.sessions + Session(d.nextId, projectId, start, end, note.trim()), nextId = d.nextId + 1)
    }

    fun updateSession(session: Session) = change { d ->
        d.copy(sessions = d.sessions.map { if (it.id == session.id) session.copy(note = session.note.trim()) else it })
    }

    fun deleteSession(id: Long) = change { d -> d.copy(sessions = d.sessions.filterNot { it.id == id }) }

    // ---- Import ----

    fun replaceAll(newData: AppData) = changeTimer { newData }
}
