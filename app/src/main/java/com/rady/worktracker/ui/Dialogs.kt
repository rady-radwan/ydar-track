@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rady.worktracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.EmojiGroups
import com.rady.worktracker.data.LabelSuggestions
import com.rady.worktracker.data.Project
import com.rady.worktracker.data.ProjectPalette
import com.rady.worktracker.data.Session
import com.rady.worktracker.data.formatDuration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
}

@Composable
private fun FieldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = P.slabRaised, modifier = modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), color = P.ink, style = MaterialTheme.typography.bodyLarge)
    }
}

// ------------------------------------------------------------------ sessions

/** Add a past session, or edit/delete an existing one. */
@Composable
fun SessionDialog(
    data: AppData,
    initial: Session?,
    defaultProjectId: Long?,
    onDismiss: () -> Unit,
    onSave: (projectId: Long, start: Long, end: Long, note: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val timeFmt = rememberTimeFormat()
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy") }

    val startInit: LocalDateTime = remember(initial) {
        if (initial != null) Instant.ofEpochMilli(initial.start).atZone(zone).toLocalDateTime()
        else {
            val n = LocalDateTime.now(zone).withSecond(0).withNano(0)
            n.minusMinutes((n.minute % 5).toLong()).minusHours(1)
        }
    }
    val durationInit = initial?.durationMillis ?: 3_600_000L

    var projectId by remember { mutableStateOf(initial?.projectId ?: defaultProjectId ?: data.ordered.firstOrNull()?.id) }
    var date by remember { mutableStateOf(startInit.toLocalDate()) }
    var startTime by remember { mutableStateOf(startInit.toLocalTime()) }
    var endTime by remember { mutableStateOf(startInit.plusSeconds(durationInit / 1000L).toLocalTime()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var timesChanged by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val endsNextDay = endTime.isBefore(startTime)
    val computedStart = date.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
    val computedEnd = (if (endsNextDay) date.plusDays(1) else date).atTime(endTime).atZone(zone).toInstant().toEpochMilli()
    val keepOriginal = initial != null && !timesChanged
    val start = if (keepOriginal) initial!!.start else computedStart
    val end = if (keepOriginal) initial!!.end else computedEnd
    val duration = end - start

    fun fmt(t: LocalTime): String = timeFmt.formatMillis(date.atTime(t).atZone(zone).toInstant().toEpochMilli())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.slab,
        title = { Text(if (initial == null) "Add a past session" else "Edit session") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("Project")
                ProjectPicker(data, projectId, { projectId = it })

                FieldLabel("Date")
                FieldButton(date.format(dateFmt), { pickDate(context, date) { date = it; timesChanged = true } })

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("Start")
                        FieldButton(fmt(startTime), { pickTime(context, startTime) { startTime = it; timesChanged = true } })
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("End")
                        FieldButton(fmt(endTime), { pickTime(context, endTime) { endTime = it; timesChanged = true } })
                    }
                }
                Text(
                    "${formatDuration(duration)}" + if (endsNextDay && !keepOriginal) "  (ends the next day)" else "",
                    style = MaterialTheme.typography.titleLarge, color = P.ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30L to "30m", 60L to "1h", 120L to "2h", 240L to "4h").forEach { (minutes, label) ->
                        Surface(
                            onClick = { endTime = startTime.plusMinutes(minutes); timesChanged = true },
                            shape = RoundedCornerShape(50), color = P.slabRaised,
                        ) { Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge) }
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") }, maxLines = 3,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete this session", color = P.danger) }
                }
            }
        },
        confirmButton = {
            val pid = projectId
            TextButton(enabled = pid != null && duration > 0, onClick = { if (pid != null) onSave(pid, start, end, note) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            text = { Text("${formatDuration(duration)} will be removed from your history.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = P.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------------ projects

/** Create or edit a project or subproject: name, emoji/label, color, parent. */
@Composable
fun ProjectDialog(
    data: AppData,
    initial: Project?,
    presetParentId: Long?,
    defaultColor: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Int, emoji: String, parentId: Long?, weeklyGoalMin: Int, totalGoalMin: Int, archived: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var goalMin by remember { mutableStateOf(initial?.weeklyGoalMin ?: 0) }
    var totalGoalH by remember { mutableStateOf((initial?.totalGoalMin ?: 0) / 60) }
    var archived by remember { mutableStateOf(initial?.archived ?: false) }
    val hasChildren = initial != null && data.children(initial.id).isNotEmpty()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var parentId by remember { mutableStateOf(initial?.parentId ?: presetParentId) }
    var color by remember {
        mutableStateOf(initial?.color ?: presetParentId?.let { data.project(it)?.color } ?: defaultColor)
    }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "") }
    var group by remember { mutableStateOf(0) }
    var parentMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val parent = parentId?.let { data.project(it) }
    val title = when {
        initial != null && initial.isSub -> "Edit subproject"
        initial != null -> "Edit project"
        parentId != null -> "New subproject"
        else -> "New project"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.slab,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Live preview
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PreviewBadge(emoji, name, color)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        if (parent != null) Text(parent.name, style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
                        Text(name.ifBlank { "Project name" }, style = MaterialTheme.typography.titleLarge,
                            color = if (name.isBlank()) P.inkMuted else P.ink)
                    }
                }

                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(if (parentId != null) "Subproject name" else "Project name") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                )

                // Parent
                FieldLabel("Part of")
                Box {
                    FieldButton(
                        text = parent?.name ?: "Nothing — this is a main project",
                        onClick = { if (!hasChildren) parentMenu = true },
                    )
                    DropdownMenu(expanded = parentMenu, onDismissRequest = { parentMenu = false }) {
                        DropdownMenuItem(text = { Text("Nothing — main project") }, onClick = { parentId = null; parentMenu = false })
                        data.topLevel.filter { it.id != initial?.id && (!it.archived || it.id == parentId) }.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                leadingIcon = { ProjectBadge(data, p.id, 28.dp) },
                                onClick = { parentId = p.id; parentMenu = false },
                            )
                        }
                    }
                }
                if (hasChildren) {
                    Text("This project has subprojects, so it stays a main project.", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                }

                // Emoji or label
                FieldLabel("Icon — pick an emoji or type a short label")
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 8) emoji = it },
                    placeholder = { Text("e.g. 🏗️ or MEP") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (emoji.isNotEmpty()) TextButton(onClick = { emoji = "" }) { Text("Clear") }
                    },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabelSuggestions.forEach { l ->
                        Surface(onClick = { emoji = l }, shape = RoundedCornerShape(8.dp),
                            color = if (emoji == l) P.accent else P.slabRaised,
                            contentColor = if (emoji == l) P.onAccent else P.ink) {
                            Text(l, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EmojiGroups.forEachIndexed { i, (label, _) ->
                        val on = i == group
                        Surface(onClick = { group = i }, shape = RoundedCornerShape(50),
                            color = if (on) P.ink else Color.Transparent, contentColor = if (on) P.slab else P.inkMuted) {
                            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EmojiGroups[group].second.forEach { e ->
                        val on = e == emoji
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (on) Color(color).copy(alpha = 0.22f) else Color.Transparent)
                                .then(if (on) Modifier.border(2.dp, Color(color), RoundedCornerShape(10.dp)) else Modifier)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }

                // Weekly goal
                FieldLabel("Weekly goal (optional)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = { goalMin = (goalMin - 30).coerceAtLeast(0) }, shape = CircleShape, color = P.slabRaised) {
                        Text("−", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.headlineSmall, color = P.ink)
                    }
                    Text(
                        if (goalMin == 0) "No goal" else "${minutesLabel(goalMin)} a week",
                        style = MaterialTheme.typography.headlineSmall, color = if (goalMin == 0) P.inkMuted else P.ink,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    Surface(onClick = { goalMin = (goalMin + 30).coerceAtMost(80 * 60) }, shape = CircleShape, color = P.slabRaised) {
                        Text("+", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.headlineSmall, color = P.ink)
                    }
                }

                // Total goal
                FieldLabel("Total hours goal (optional) — e.g. finish a course in 100 h")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = { totalGoalH = (totalGoalH - 5).coerceAtLeast(0) }, shape = CircleShape, color = P.slabRaised) {
                        Text("−", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.headlineSmall, color = P.ink)
                    }
                    Text(
                        if (totalGoalH == 0) "No total goal" else "${totalGoalH} hours in total",
                        style = MaterialTheme.typography.headlineSmall, color = if (totalGoalH == 0) P.inkMuted else P.ink,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    Surface(onClick = { totalGoalH = (totalGoalH + 5).coerceAtMost(10_000) }, shape = CircleShape, color = P.slabRaised) {
                        Text("+", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.headlineSmall, color = P.ink)
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 10, 25, 50, 100, 200, 500, 1000).forEach { h ->
                        val on = h == totalGoalH
                        Surface(onClick = { totalGoalH = h }, shape = RoundedCornerShape(50),
                            color = if (on) P.accent else P.slabRaised, contentColor = if (on) P.onAccent else P.ink) {
                            Text(if (h == 0) "None" else "${h}h", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                // Color
                FieldLabel("Color")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProjectPalette.forEach { c ->
                        val on = c == color
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                .then(if (on) Modifier.border(3.dp, P.ink, CircleShape) else Modifier)
                                .clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (initial != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(P.slabRaised)
                            .clickable { archived = !archived }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Archived (finished)", style = MaterialTheme.typography.titleSmall, color = P.ink)
                            Text("Hidden from the start list. Its hours stay in your stats.",
                                style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                        }
                        Switch(
                            checked = archived, onCheckedChange = { archived = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = P.onAccent, checkedTrackColor = P.accent),
                        )
                    }
                }

                if (onDelete != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(if (initial?.isSub == true) "Delete subproject" else "Delete project", color = P.danger)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, color, emoji, if (hasChildren) null else parentId, goalMin, totalGoalH * 60, archived) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete && onDelete != null && initial != null) {
        val subs = data.children(initial.id)
        val ids = setOf(initial.id) + subs.map { it.id }
        val count = data.sessions.count { it.projectId in ids }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${initial.name}\"?") },
            text = {
                Text(buildString {
                    if (subs.isNotEmpty()) append("Its ${subs.size} subproject${if (subs.size == 1) "" else "s"} will be deleted too. ")
                    append(if (count == 0) "There are no sessions to lose." else "$count session${if (count == 1) "" else "s"} will be deleted. This can't be undone.")
                })
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = P.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PreviewBadge(emoji: String, name: String, color: Int) {
    val c = Color(color)
    val label = emoji.ifBlank { name.firstOrNull()?.uppercase() ?: "?" }
    val isEmoji = emoji.isNotBlank() && emoji.any { Character.isSurrogate(it) || it.code > 0x2000 }
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(17.dp)).background(c.copy(alpha = 0.16f))
            .border(1.dp, c.copy(alpha = 0.35f), RoundedCornerShape(17.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = if (isEmoji) 28.sp else if (label.length <= 2) 22.sp else if (label.length == 3) 17.sp else 13.sp,
            fontWeight = FontWeight.Bold, color = if (isEmoji) Color.Unspecified else c, textAlign = TextAlign.Center, maxLines = 1,
        )
    }
}
