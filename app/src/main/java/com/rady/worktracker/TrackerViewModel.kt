package com.rady.worktracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rady.worktracker.data.CloudSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rady.worktracker.data.ActiveTimer
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.AppSettings
import com.rady.worktracker.data.Settings
import com.rady.worktracker.data.ProjectPalette
import com.rady.worktracker.data.Repo
import com.rady.worktracker.data.Session
import com.rady.worktracker.timer.TimerSync
import kotlinx.coroutines.flow.StateFlow

/** What the sync is doing, for the Refresh button and Settings. */
data class CloudStatus(
    val signedIn: Boolean = false,
    val email: String? = null,
    val syncing: Boolean = false,
    val lastSyncMs: Long = 0L,
    val message: String? = null,
    val error: Boolean = false,
)

object Cloud {
    @Volatile private var instance: CloudSync? = null
    fun get(app: Application): CloudSync = instance ?: synchronized(this) {
        instance ?: CloudSync(app.filesDir).also { instance = it }
    }
}

@OptIn(FlowPreview::class)
class TrackerViewModel(private val app: Application) : AndroidViewModel(app) {

    init { Repo.init(app); AppSettings.init(app) }

    // ---- Sync between devices (Supabase) ----

    private val cloud = Cloud.get(app)
    private val _cloud = MutableStateFlow(CloudStatus(cloud.signedIn, cloud.email, lastSyncMs = cloud.lastSyncMs))
    val cloudStatus: StateFlow<CloudStatus> = _cloud.asStateFlow()

    init {
        // Send local changes a few seconds after you make them (when signed in).
        viewModelScope.launch {
            Repo.data.drop(1).debounce(8_000).collect { if (cloud.signedIn) syncNow(quiet = true) }
        }
    }

    fun syncNow(quiet: Boolean = false) {
        if (!cloud.signedIn || _cloud.value.syncing) return
        _cloud.value = _cloud.value.copy(syncing = true, message = if (quiet) _cloud.value.message else null, error = false)
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { cloud.syncNow({ Repo.current }) { t -> Repo.update(t) } }
                TimerSync.sync(app, Repo.current)
                _cloud.value = CloudStatus(true, cloud.email, false, cloud.lastSyncMs,
                    if (quiet) null else if (r.pushed + r.pulled == 0) "Everything is up to date" else "Synced: sent ${r.pushed}, received ${r.pulled}")
            } catch (t: Throwable) {
                _cloud.value = CloudStatus(cloud.signedIn, cloud.email, false, cloud.lastSyncMs,
                    if (quiet && t is java.io.IOException && t !is com.rady.worktracker.data.CloudException) null
                    else (t.message ?: t.javaClass.simpleName), error = true)
            }
        }
    }

    fun cloudSignIn(email: String, password: String, create: Boolean) {
        _cloud.value = _cloud.value.copy(syncing = true, message = null, error = false)
        viewModelScope.launch {
            try {
                val note = withContext(Dispatchers.IO) { cloud.signIn(email, password, create) }
                _cloud.value = CloudStatus(cloud.signedIn, cloud.email, false, cloud.lastSyncMs, note)
                if (cloud.signedIn) syncNow()
            } catch (t: Throwable) {
                _cloud.value = CloudStatus(cloud.signedIn, cloud.email, false, cloud.lastSyncMs, t.message ?: "Couldn't sign in", error = true)
            }
        }
    }

    fun cloudResetPassword(email: String) {
        viewModelScope.launch {
            val msg = try {
                withContext(Dispatchers.IO) { com.rady.worktracker.data.CloudAuth.resetPassword(email) }
                "Password reset email sent to $email"
            } catch (t: Throwable) { t.message ?: "Couldn't send the email" }
            _cloud.value = _cloud.value.copy(message = msg)
        }
    }

    fun cloudSignOut() {
        cloud.signOut()
        _cloud.value = CloudStatus()
    }

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
        d.copy(sessions = d.sessions + Session(com.rady.worktracker.data.IdGen.next(), projectId, start, end, note.trim()))
    }

    fun updateSession(session: Session) = change { d ->
        d.copy(sessions = d.sessions.map { if (it.id == session.id) session.copy(note = session.note.trim()) else it })
    }

    fun deleteSession(id: Long) = change { d -> d.copy(sessions = d.sessions.filterNot { it.id == id }) }

    // ---- Import ----

    fun replaceAll(newData: AppData) = changeTimer { newData }
}
