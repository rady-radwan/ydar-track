@file:OptIn(ExperimentalMaterial3Api::class)

package com.rady.worktracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rady.worktracker.TrackerViewModel
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.Project
import com.rady.worktracker.data.formatDuration
import com.rady.worktracker.data.goalProgress

private data class EditState(val project: Project?, val parentId: Long?)

@Composable
fun ProjectsScreen(data: AppData, vm: TrackerViewModel) {
    var editing by remember { mutableStateOf<EditState?>(null) }

    val totals = remember(data.sessions) {
        data.sessions.groupBy { it.projectId }.mapValues { (_, l) -> l.size to l.sumOf { it.durationMillis } }
    }
    val zone = remember { java.time.ZoneId.systemDefault() }
    val today = java.time.LocalDate.now(zone)
    val weekTotals = remember(data, today) {
        data.topLevel.associate { it.id to com.rady.worktracker.data.weekTotalFor(data, data.sessions, it.id, today, zone) }
    }
    val now = System.currentTimeMillis()
    fun totalOf(ids: List<Long>): Pair<Int, Long> =
        ids.sumOf { totals[it]?.first ?: 0 } to ids.sumOf { totals[it]?.second ?: 0L }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("Projects", subtitle = "${data.activeTopLevel.size} active, ${data.archivedTopLevel.size} archived, ${data.projects.count { it.isSub }} subprojects") {
                FilledTonalButton(onClick = { editing = EditState(null, null) }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New")
                }
                DataMenu(data, vm)
            }
        }

        if (data.projects.isEmpty()) {
            item {
                EmptyState(
                    "No projects yet",
                    "Add what you spend time on: a job site, a course, an exam, a skill. Split big ones into subprojects, like \"Part 107 › Airspace\" or \"BIM › Revit\".",
                ) { Button(onClick = { editing = EditState(null, null) }) { Text("Create a project") } }
            }
        }

        items(data.activeTopLevel, key = { it.id }) { p ->
            ProjectCard(
                data, p, totalOf = ::totalOf, totals = totals, weekMs = weekTotals[p.id] ?: 0L, now = now,
                onEdit = { editing = EditState(it, it.parentId) },
                onAddSub = { editing = EditState(null, p.id) },
                onArchive = { vm.setArchived(p.id, !p.archived) },
            )
        }

        val archived = data.archivedTopLevel
        if (archived.isNotEmpty()) {
            item(key = "archived-header") {
                Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
                    SectionTitle("Archived · finished")
                    Text(
                        "Hidden from the start list. Their hours still count in your stats and legacy.",
                        style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
                    )
                }
            }
            items(archived, key = { it.id }) { p ->
                Box(Modifier.alpha(0.5f)) {
                    ProjectCard(
                        data, p, totalOf = ::totalOf, totals = totals, weekMs = weekTotals[p.id] ?: 0L, now = now,
                        onEdit = { editing = EditState(it, it.parentId) },
                        onAddSub = { editing = EditState(null, p.id) },
                        onArchive = { vm.setArchived(p.id, !p.archived) },
                    )
                }
            }
        }

        if (data.projects.isNotEmpty()) {
            item {
                Text(
                    "Tip: Excel export and import are in the ⋮ menu here and in Settings.",
                    style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }

    editing?.let { st ->
        val p = st.project
        ProjectDialog(
            data = data,
            initial = p,
            presetParentId = st.parentId,
            defaultColor = vm.nextFreeColor(),
            onDismiss = { editing = null },
            onSave = { name, color, emoji, parent, goal, total, isArchived ->
                if (p == null) vm.addProject(name, color, emoji, parent, goal, total)
                else vm.updateProject(p.id, name, color, emoji, parent, goal, total, isArchived)
                editing = null
            },
            onDelete = p?.let { proj -> { vm.deleteProject(proj.id); editing = null } },
        )
    }
}

@Composable
private fun ProjectCard(
    data: AppData,
    p: Project,
    totalOf: (List<Long>) -> Pair<Int, Long>,
    totals: Map<Long, Pair<Int, Long>>,
    weekMs: Long,
    now: Long,
    onEdit: (Project) -> Unit,
    onAddSub: () -> Unit,
    onArchive: () -> Unit,
) {
    val children = data.children(p.id)
    val (count, total) = totalOf(listOf(p.id) + children.map { it.id })
    val goal = remember(data, p) { goalProgress(data, data.sessions, p.id, now) }
    Slab(padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().clickable { onEdit(p) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectBadge(data, p.id, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, color = P.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (p.archived) "Archived · " else "") + "$count session${if (count == 1) "" else "s"} · ${formatDuration(total)} total",
                    style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
                )
            }
            Text("Edit", style = MaterialTheme.typography.labelLarge, color = P.info)
        }
        if (goal != null) {
            TotalGoalBar(goal, Color(p.color), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        }
        if (p.weeklyGoalMin > 0 && !p.archived) {
            GoalBar(weekMs, p.weeklyGoalMin, Color(p.color), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
        }
        if (children.isNotEmpty()) {
            HorizontalDivider(color = P.rule.copy(alpha = 0.6f))
            Column(Modifier.padding(vertical = 4.dp)) {
                children.forEach { c ->
                    val (cc, ct) = totals[c.id] ?: (0 to 0L)
                    val cGoal = goalProgress(data, data.sessions, c.id, now)
                    Column(
                        Modifier.fillMaxWidth().alpha(if (c.archived && !p.archived) 0.5f else 1f).clickable { onEdit(c) }
                            .padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(2.dp).height(30.dp).background(Color(p.color).copy(alpha = 0.45f)))
                            Spacer(Modifier.width(12.dp))
                            ProjectBadge(data, c.id, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    (if (c.archived) "Archived · " else "") + "$cc session${if (cc == 1) "" else "s"} · ${formatDuration(ct)}",
                                    style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
                                )
                            }
                        }
                        if (cGoal != null) TotalGoalBar(cGoal, Color(c.color), Modifier.padding(start = 14.dp, top = 6.dp))
                    }
                }
            }
        }
        HorizontalDivider(color = P.rule.copy(alpha = 0.6f))
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!p.archived) {
                TextButton(onClick = onAddSub) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = P.info)
                    Spacer(Modifier.width(6.dp))
                    Text("Add subproject", color = P.info)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onArchive) {
                Text(if (p.archived) "Unarchive" else "Archive", color = if (p.archived) P.accent else P.inkMuted)
            }
        }
    }
}
