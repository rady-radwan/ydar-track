@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rady.worktracker.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rady.worktracker.TrackerViewModel
import com.rady.worktracker.data.ActiveTimer
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.Project
import com.rady.worktracker.data.Settings
import com.rady.worktracker.data.countdownMilestones
import com.rady.worktracker.data.formatClock
import com.rady.worktracker.data.formatDuration
import com.rady.worktracker.data.GoalProgress
import com.rady.worktracker.data.goalProgress
import com.rady.worktracker.data.greeting
import com.rady.worktracker.data.legacyOf
import com.rady.worktracker.data.quoteOfTheDay
import com.rady.worktracker.data.streakDays
import com.rady.worktracker.data.toLocalDate
import com.rady.worktracker.data.totalsForDay
import com.rady.worktracker.data.weekRank
import com.rady.worktracker.data.weekTotalFor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private enum class TimerMode { OPEN, COUNTDOWN }

private val Presets = listOf(25, 45, 60, 90, 120, 180, 240)
private val LapChoices = listOf(15, 25, 30, 45, 60)

fun minutesLabel(min: Int): String = when {
    min < 60 -> "${min}m"
    min % 60 == 0 -> "${min / 60}h"
    else -> "${min / 60}h ${min % 60}m"
}

@Composable
fun TrackScreen(data: AppData, settings: Settings, vm: TrackerViewModel, onShowRank: () -> Unit) {
    val cloud by vm.cloudStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("track", Context.MODE_PRIVATE) }
    val zone = remember { ZoneId.systemDefault() }
    val now by rememberNow()
    val timeFmt = rememberTimeFormat()
    val today = toLocalDate(now, zone)
    val minute = now / 60_000L
    val live = remember(data, minute) { data.sessionsIncludingActive(now) }
    val todayTotals = remember(live, today) { totalsForDay(live, today, zone) }
    val yesterdayTotal = remember(live, today) { totalsForDay(live, today.minusDays(1), zone).values.sum() }
    val todayByRoot = remember(todayTotals) {
        val m = HashMap<Long, Long>()
        todayTotals.forEach { (pid, ms) -> val r = data.rootIdOf(pid); m[r] = (m[r] ?: 0L) + ms }
        m
    }
    val week = remember(live, today) { weekRank(data, live, today, today, zone) }
    val streak = remember(live, today) { streakDays(live, today, zone) }
    val legacy = remember(live) { legacyOf(live) }
    val hour = java.time.Instant.ofEpochMilli(now).atZone(zone).hour
    var showArchived by rememberSaveable { mutableStateOf(false) }

    var mode by rememberSaveable { mutableStateOf(if (prefs.getBoolean("countdown", false)) TimerMode.COUNTDOWN else TimerMode.OPEN) }
    var minutes by rememberSaveable { mutableIntStateOf(prefs.getInt("minutes", 60)) }
    var showCustom by remember { mutableStateOf(false) }
    var showAddSession by remember { mutableStateOf(false) }
    var showNewProject by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshTimerNotification()
    }

    fun start(projectId: Long) {
        prefs.edit().putBoolean("countdown", mode == TimerMode.COUNTDOWN).putInt("minutes", minutes).apply()
        vm.startTimer(projectId, if (mode == TimerMode.COUNTDOWN) minutes * 60_000L else null)
        scope.launch { listState.animateScrollToItem(0) }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("asked_notif", false)
        ) {
            prefs.edit().putBoolean("asked_notif", true).apply()
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val active = data.active
    val activeProject = active?.let { data.project(it.projectId) }
    val todayTotal = todayTotals.values.sum()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "banner") {
            HomeBanner(
                greeting = greeting(hour),
                quote = quoteOfTheDay(today),
                strip = when {
                    active != null && activeProject != null -> "● ${data.displayName(active.projectId)}"
                    data.projects.isEmpty() -> "Create a project to begin!"
                    else -> "Tap a project to begin!"
                },
                onStripClick = if (active == null && data.projects.isNotEmpty()) {
                    { scope.launch { listState.animateScrollToItem(3) } }
                } else null,
                syncLabel = if (cloud.signedIn) syncAgo(cloud.lastSyncMs, now, cloud.error) else null,
                syncing = cloud.syncing,
                onSync = { vm.syncNow() },
            )
        }

        if (active != null && activeProject != null) {
            item(key = "active") {
                ActiveCard(
                    data = data, project = activeProject, active = active, now = now, timeFmt = timeFmt, settings = settings,
                    onNoteChange = vm::setActiveNote,
                    onAdjustStart = {
                        val s = Instant.ofEpochMilli(active.start).atZone(zone)
                        pickTime(context, s.toLocalTime()) { t ->
                            var picked = s.toLocalDate().atTime(t).atZone(zone).toInstant().toEpochMilli()
                            if (picked > System.currentTimeMillis()) picked -= 24L * 3_600_000L
                            vm.setActiveStart(picked)
                        }
                    },
                    onExtend = { vm.extendCountdown(15 * 60_000L) },
                    onStop = vm::stopTimer,
                    onDiscard = { confirmDiscard = true },
                )
            }
        }

        // Dashboard tiles: four equal, compact tiles
        item(key = "tiles") {
            val goalMs = (settings.dailyGoalMin * 60_000f).coerceAtLeast(1f)
            val diff = todayTotal - yesterdayTotal
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "Today", value = formatDuration(todayTotal),
                        sub = when {
                            yesterdayTotal == 0L && todayTotal == 0L -> "of ${minutesLabel(settings.dailyGoalMin)} goal"
                            diff >= 0 -> "▲ ${formatDuration(diff)} vs yesterday"
                            else -> "▼ ${formatDuration(-diff)} vs yesterday"
                        },
                        modifier = Modifier.weight(1f),
                    ) { GoalRing(todayTotal / goalMs, Modifier.size(44.dp)) }
                    StatTile(
                        label = "This week", value = formatDuration(week.avgPerDay),
                        sub = "a day · ${formatDuration(week.total)} total", modifier = Modifier.weight(1f),
                    ) { TileIcon(BarChartIcon, P.info) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "Streak", value = "$streak",
                        sub = if (streak == 1) "day in a row" else "days in a row", modifier = Modifier.weight(1f),
                    ) { TileIcon(FlameIcon, if (streak > 0) P.alert else P.inkMuted) }
                    StatTile(
                        label = "Legacy", value = "${legacy.floors}",
                        sub = "floor${if (legacy.floors == 1) "" else "s"} · ${formatDuration(legacy.totalMs)} built",
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(P.slabRaised), contentAlignment = Alignment.Center) {
                            LegacyTower(legacy, Modifier.size(28.dp, 34.dp))
                        }
                    }
                }
            }
        }

        item(key = "rank") { RankBar(week, onClick = onShowRank) }

        if (data.projects.isEmpty()) {
            item {
                EmptyState(
                    title = "Set up your first project",
                    body = "Projects are what you spend time on — \"Newcastle Medical Center\", \"Part 107 study\", \"Gym\". Give each one an emoji, then time it here.",
                ) { Button(onClick = { showNewProject = true }) { Text("Create a project") } }
            }
        } else {
            item(key = "mode") {
                Slab(padding = 14.dp) {
                    Text(
                        if (active == null) "START A SESSION" else "SWITCH PROJECT",
                        style = MaterialTheme.typography.headlineSmall, color = P.ink,
                    )
                    Spacer(Modifier.height(10.dp))
                    Segmented(
                        options = listOf(TimerMode.OPEN to "Open timer", TimerMode.COUNTDOWN to "Countdown"),
                        selected = mode, onSelect = { mode = it }, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    if (mode == TimerMode.COUNTDOWN) {
                        val labels = Presets.map { minutesLabel(it) } + (if (minutes !in Presets) minutesLabel(minutes) else "Custom")
                        val sel = Presets.indexOf(minutes).let { if (it >= 0) it else Presets.size }
                        ChoiceGrid(labels, sel, perRow = 4, onSelect = { i -> if (i < Presets.size) minutes = Presets[i] else showCustom = true })
                        Spacer(Modifier.height(8.dp))
                        val alerts = buildList {
                            if (settings.halfOn && minutes >= 4) add("halfway")
                            if (settings.fiveOn && minutes > 10) add("5 min left")
                            if (settings.endOn) add("the end")
                        }
                        Text(
                            "Saves the session when time is up." + if (alerts.isNotEmpty()) " Sounds at ${alerts.joinToString(", ")}." else "",
                            style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("LAP CHIME", style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
                        Spacer(Modifier.height(6.dp))
                        val labels = listOf("Off") + LapChoices.map { minutesLabel(it) }
                        val sel = if (!settings.lapOn) 0 else LapChoices.indexOf(settings.lapMin).let { if (it >= 0) it + 1 else -1 }
                        ChoiceGrid(labels, sel, perRow = labels.size, onSelect = { i ->
                            if (i == 0) vm.updateSettings { it.copy(lapOn = false) }
                            else vm.updateSettings { it.copy(lapOn = true, lapMin = LapChoices[i - 1]) }
                        })
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Counts up until you stop it." + if (settings.lapOn) " Chime every ${minutesLabel(settings.lapMin)}." else "",
                            style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            data.activeTopLevel.forEach { p ->
                item(key = "p${p.id}") {
                    ProjectStartCard(
                        data = data, project = p, children = data.activeChildren(p.id), expanded = p.id in expanded,
                        goal = goalProgress(data, live, p.id, now),
                        todayMs = todayByRoot[p.id] ?: 0L, todayOf = { todayTotals[it] ?: 0L },
                        weekMs = weekTotalFor(data, live, p.id, today, zone),
                        activeId = active?.projectId, countdown = mode == TimerMode.COUNTDOWN,
                        onToggle = { expanded = if (p.id in expanded) expanded - p.id else expanded + p.id },
                        onStart = ::start,
                    )
                }
            }

            val archivedList = data.archivedTopLevel
            if (archivedList.isNotEmpty()) {
                item(key = "archived-toggle") {
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Text(
                            (if (showArchived) "Hide" else "Show") + " ${archivedList.size} archived project${if (archivedList.size == 1) "" else "s"}",
                            color = P.inkMuted,
                        )
                    }
                }
                if (showArchived) {
                    archivedList.forEach { p ->
                        item(key = "a${p.id}") {
                            Box(Modifier.alpha(0.45f)) {
                                ProjectStartCard(
                                    data = data, project = p, children = data.children(p.id), expanded = p.id in expanded,
                                    goal = goalProgress(data, live, p.id, now),
                                    todayMs = todayByRoot[p.id] ?: 0L, todayOf = { todayTotals[it] ?: 0L },
                                    weekMs = weekTotalFor(data, live, p.id, today, zone),
                                    activeId = active?.projectId, countdown = mode == TimerMode.COUNTDOWN,
                                    onToggle = { expanded = if (p.id in expanded) expanded - p.id else expanded + p.id },
                                    onStart = ::start,
                                    archived = true,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showAddSession = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add past session")
                    }
                    TextButton(onClick = { showNewProject = true }) { Text("New project", color = P.info) }
                }
            }
        }

        if (todayTotals.isNotEmpty()) item { TodayCard(data, todayByRoot) }
    }

    if (showCustom) {
        CustomDurationDialog(initialMinutes = minutes, onDismiss = { showCustom = false }, onSet = { minutes = it; showCustom = false })
    }
    if (showAddSession) {
        SessionDialog(
            data = data, initial = null,
            defaultProjectId = data.active?.projectId ?: data.sessions.maxByOrNull { it.start }?.projectId,
            onDismiss = { showAddSession = false },
            onSave = { pid, s, e, note -> vm.addSession(pid, s, e, note); showAddSession = false },
            onDelete = null,
        )
    }
    if (showNewProject) {
        ProjectDialog(
            data = data, initial = null, presetParentId = null, defaultColor = vm.nextFreeColor(),
            onDismiss = { showNewProject = false },
            onSave = { name, color, emoji, parent, goal, total, _ -> vm.addProject(name, color, emoji, parent, goal, total); showNewProject = false },
            onDelete = null,
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this session?") },
            text = { Text("The time on the running timer won't be saved.") },
            confirmButton = { TextButton(onClick = { vm.discardTimer(); confirmDiscard = false }) { Text("Discard", color = P.danger) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep timing") } },
        )
    }
}

/** A compact dashboard tile: picture on the left, label / big value / one line of detail on the right. */
@Composable
fun StatTile(label: String, value: String, sub: String, modifier: Modifier = Modifier, visual: @Composable () -> Unit) {
    Slab(modifier = modifier.height(92.dp), padding = 12.dp) {
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            visual()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = P.inkMuted, maxLines = 1)
                Text(value, style = MaterialTheme.typography.displaySmall, color = P.accent, maxLines = 1)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = P.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TileIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun GoalRing(fraction: Float, modifier: Modifier = Modifier) {
    val track = P.slabRaised
    val accent = P.accent
    val done = P.alert
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.minDimension * 0.14f
            val d = size.minDimension - w
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(track, -90f, 360f, false, tl, Size(d, d), style = Stroke(w))
            val f = fraction.coerceIn(0f, 1f)
            if (f > 0f) drawArc(if (fraction >= 1f) done else accent, -90f, 360f * f, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("${(fraction * 100).toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.labelMedium, color = P.ink)
    }
}

@Composable
private fun DurationChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) P.accent else P.slabRaised,
        contentColor = if (selected) P.onAccent else P.ink,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ActiveCard(
    data: AppData,
    project: Project,
    active: ActiveTimer,
    now: Long,
    timeFmt: java.text.DateFormat,
    settings: Settings,
    onNoteChange: (String) -> Unit,
    onAdjustStart: () -> Unit,
    onExtend: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
) {
    val accent = P.accent
    var note by remember(active.projectId, active.start) { mutableStateOf(active.note) }
    val elapsed = (now - active.start).coerceAtLeast(0L)
    val target = active.targetMillis
    val lapMs = (if (settings.lapOn) settings.lapMin else 60) * 60_000L
    val progress: Float
    val big: String
    val small: String
    if (target != null) {
        val left = (target - elapsed).coerceAtLeast(0L)
        progress = (elapsed.toFloat() / target.toFloat()).coerceIn(0f, 1f)
        big = formatClock(left)
        small = "left of ${formatDuration(target)}"
    } else {
        progress = ((elapsed % lapMs).toFloat() / lapMs)
        big = formatClock(elapsed)
        small = if (settings.lapOn) "lap ${elapsed / lapMs + 1} · every ${minutesLabel(settings.lapMin)}" else "open timer"
    }
    val markers = if (target != null) {
        countdownMilestones(active, settings).filter { it.second != com.rady.worktracker.data.TimerEvent.END }
            .map { ((it.first - active.start).toFloat() / target) }
    } else emptyList()

    Slab(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProjectBadge(data, project.id, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (target != null) "COUNTDOWN" else "WORKING ON", style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
                Text(data.displayName(project.id), style = MaterialTheme.typography.titleLarge, color = P.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            LiveDot()
        }
        Spacer(Modifier.height(16.dp))
        TimerDial(
            progress = progress, accent = accent, markers = markers,
            completedLaps = if (target == null) (elapsed / lapMs).toInt() else 0,
            modifier = Modifier.fillMaxWidth().height(270.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(big, style = MaterialTheme.typography.displayMedium, color = P.ink)
                Text(small, style = MaterialTheme.typography.bodyMedium, color = P.inkMuted)
                if (target != null) {
                    Spacer(Modifier.height(2.dp))
                    Text("ends ${timeFmt.formatMillis(active.start + target)}", style = MaterialTheme.typography.labelMedium, color = P.info)
                }
            }
        }
        if (markers.isNotEmpty()) {
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(P.alert))
                Spacer(Modifier.width(6.dp))
                Text("= alert sound", style = MaterialTheme.typography.labelSmall, color = P.inkMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAdjustStart, contentPadding = PaddingValues(0.dp)) {
            Text("Started at ${timeFmt.formatMillis(active.start)} · change", color = P.info)
        }
        OutlinedTextField(
            value = note, onValueChange = { note = it; onNoteChange(it) },
            label = { Text("What are you working on? (optional)") }, maxLines = 3,
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = P.onAccent),
            ) {
                Icon(StopIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (target != null) "END NOW · SAVE ${formatDuration(elapsed).uppercase()}" else "STOP & SAVE",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
            if (target != null) {
                OutlinedButton(onClick = onExtend, modifier = Modifier.height(54.dp), shape = RoundedCornerShape(14.dp)) { Text("+15m") }
            }
        }
        if (target != null) {
            Text(
                "Not going to finish? End now and the ${formatDuration(elapsed)} you've worked is added to ${project.name}.",
                style = MaterialTheme.typography.bodySmall, color = P.inkMuted, modifier = Modifier.padding(top = 8.dp),
            )
        }
        TextButton(onClick = onDiscard, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Discard this session", color = P.inkMuted)
        }
    }
}

@Composable
private fun LiveDot() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(P.alert))
        Spacer(Modifier.width(6.dp))
        Text("LIVE", style = MaterialTheme.typography.labelMedium, color = P.alert)
    }
}

@Composable
private fun ProjectStartCard(
    data: AppData,
    project: Project,
    children: List<Project>,
    expanded: Boolean,
    goal: GoalProgress?,
    todayMs: Long,
    todayOf: (Long) -> Long,
    weekMs: Long,
    activeId: Long?,
    countdown: Boolean,
    onToggle: () -> Unit,
    onStart: (Long) -> Unit,
    archived: Boolean = false,
) {
    Slab(padding = 0.dp) {
        Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProjectBadge(data, project.id, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildString {
                    if (archived) append("Archived · ")
                    append(if (todayMs > 0) "Today ${formatDuration(todayMs)}" else "Not yet today")
                    if (children.isNotEmpty()) append("  ·  ${children.size} sub")
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
            }
            if (children.isNotEmpty()) {
                IconButton(onClick = onToggle) {
                    Icon(ChevronIcon, contentDescription = if (expanded) "Hide subprojects" else "Show subprojects",
                        tint = P.inkMuted, modifier = Modifier.rotate(if (expanded) 180f else 0f))
                }
            }
            StartButton(project.color, project.id == activeId, countdown) { onStart(project.id) }
        }
        if (project.weeklyGoalMin > 0) {
            GoalBar(weekMs, project.weeklyGoalMin, Color(project.color), Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
        }
        if (goal != null) {
            TotalGoalBar(goal, Color(project.color), Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
        }
        AnimatedVisibility(expanded && children.isNotEmpty()) {
            Column(Modifier.padding(start = 34.dp, end = 10.dp, bottom = 10.dp)) {
                children.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Box(Modifier.width(2.dp).height(34.dp).background(Color(project.color).copy(alpha = 0.4f)))
                        Spacer(Modifier.width(12.dp))
                        ProjectBadge(data, c.id, 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val t = todayOf(c.id)
                            if (t > 0) Text("Today ${formatDuration(t)}", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                        }
                        StartButton(c.color, c.id == activeId, countdown, small = true) { onStart(c.id) }
                    }
                }
            }
        }
    }
}

/** "3h 10m of 5h this week" with a bar. */
@Composable
fun GoalBar(doneMs: Long, goalMin: Int, color: Color, modifier: Modifier = Modifier) {
    val goalMs = goalMin * 60_000L
    val f = (doneMs.toFloat() / goalMs).coerceIn(0f, 1f)
    Column(modifier) {
        Row {
            Text("Weekly goal", style = MaterialTheme.typography.labelSmall, color = P.inkMuted, modifier = Modifier.weight(1f))
            Text(
                "${formatDuration(doneMs)} / ${minutesLabel(goalMin)}" + if (doneMs >= goalMs) "  ✓" else "",
                style = MaterialTheme.typography.labelSmall, color = if (doneMs >= goalMs) P.accent else P.inkMuted,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(P.slabRaised)) {
            Box(Modifier.fillMaxWidth(f.coerceAtLeast(0.01f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
        }
    }
}

@Composable
private fun StartButton(color: Int, running: Boolean, countdown: Boolean, small: Boolean = false, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick, enabled = !running,
        modifier = Modifier.size(if (small) 38.dp else 46.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color(color), contentColor = Color.White,
            disabledContainerColor = Color(color).copy(alpha = 0.25f), disabledContentColor = Color.White,
        ),
    ) {
        Icon(
            if (countdown) TimerIcon else Icons.Default.PlayArrow,
            contentDescription = if (running) "Running" else if (countdown) "Start countdown" else "Start timer",
            modifier = Modifier.size(if (small) 20.dp else 24.dp),
        )
    }
}

@Composable
private fun TodayCard(data: AppData, byRoot: Map<Long, Long>) {
    val total = byRoot.values.sum()
    val max = byRoot.values.maxOrNull() ?: 1L
    Slab {
        Row(verticalAlignment = Alignment.Bottom) {
            SectionTitle("Today", Modifier.weight(1f))
            Text(formatDuration(total), style = MaterialTheme.typography.headlineMedium, color = P.accent)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            byRoot.entries.sortedByDescending { it.value }.forEach { (pid, ms) ->
                val p = data.project(pid)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProjectBadge(data, pid, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(p?.name ?: "Unknown", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium, color = P.ink)
                            Text(formatDuration(ms), style = MaterialTheme.typography.bodyMedium, color = P.inkMuted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(P.slabRaised)) {
                            Box(
                                Modifier.fillMaxWidth((ms.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp)).background(Color(p?.color ?: 0xFF9E9E9E.toInt()))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomDurationDialog(initialMinutes: Int, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var h by remember { mutableIntStateOf(initialMinutes / 60) }
    var m by remember { mutableIntStateOf((initialMinutes % 60) / 5 * 5) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.slab,
        title = { Text("Countdown length") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Stepper("hours", h, onMinus = { if (h > 0) h-- }, onPlus = { if (h < 12) h++ }, modifier = Modifier.weight(1f))
                Stepper("minutes", m, onMinus = { m = (m - 5 + 60) % 60 }, onPlus = { m = (m + 5) % 60 }, modifier = Modifier.weight(1f))
            }
        },
        confirmButton = { TextButton(enabled = h * 60 + m > 0, onClick = { onSet(h * 60 + m) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun Stepper(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, display: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onPlus) { Icon(ChevronIcon, contentDescription = "More $label", modifier = Modifier.rotate(180f)) }
        Text(display ?: value.toString().padStart(2, '0'), style = MaterialTheme.typography.displaySmall, color = P.ink)
        Text(label, style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
        IconButton(onClick = onMinus) { Icon(ChevronIcon, contentDescription = "Less $label") }
    }
}

/** "Synced now" / "Synced 5m ago" / "Sync failed" for the Refresh button. */
fun syncAgo(lastMs: Long, now: Long, error: Boolean): String {
    if (error) return "Retry sync"
    if (lastMs <= 0L) return "Sync"
    val min = (now - lastMs) / 60_000L
    return when {
        min < 1 -> "Synced"
        min < 60 -> "${min}m ago"
        min < 24 * 60 -> "${min / 60}h ago"
        else -> "${min / (24 * 60)}d ago"
    }
}
