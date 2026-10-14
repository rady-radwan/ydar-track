@file:OptIn(ExperimentalMaterial3Api::class)

package com.rady.worktracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rady.worktracker.data.AppData
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.util.Date

// ---- Icons not in the small built-in set ----

val BarChartIcon: ImageVector = materialIcon(name = "Custom.BarChart") {
    materialPath {
        moveTo(4f, 10f); horizontalLineTo(7.5f); verticalLineTo(20f); horizontalLineTo(4f); close()
        moveTo(10.25f, 4f); horizontalLineTo(13.75f); verticalLineTo(20f); horizontalLineTo(10.25f); close()
        moveTo(16.5f, 13f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(16.5f); close()
    }
}

val StopIcon: ImageVector = materialIcon(name = "Custom.Stop") {
    materialPath { moveTo(6f, 6f); horizontalLineTo(18f); verticalLineTo(18f); horizontalLineTo(6f); close() }
}

val TimerIcon: ImageVector = materialIcon(name = "Custom.Timer") {
    materialPath {
        moveTo(9f, 1f); horizontalLineTo(15f); verticalLineTo(3f); horizontalLineTo(9f); close()
        moveTo(12f, 4f)
        arcTo(9f, 9f, 0f, true, false, 21f, 13f)
        arcTo(9f, 9f, 0f, false, false, 12f, 4f); close()
        moveTo(12f, 6f)
        arcTo(7f, 7f, 0f, true, true, 5f, 13f)
        arcTo(7f, 7f, 0f, false, true, 12f, 6f); close()
        moveTo(11f, 8f); horizontalLineTo(13f); verticalLineTo(14f); horizontalLineTo(11f); close()
    }
}

val FolderTreeIcon: ImageVector = materialIcon(name = "Custom.FolderTree") {
    materialPath {
        moveTo(3f, 4f); horizontalLineTo(10f); lineTo(12f, 6f); horizontalLineTo(21f); verticalLineTo(9f); horizontalLineTo(3f); close()
        moveTo(3f, 10.5f); horizontalLineTo(21f); verticalLineTo(19f); horizontalLineTo(3f); close()
    }
}

val MoreIcon: ImageVector = materialIcon(name = "Custom.More") {
    materialPath {
        moveTo(12f, 6.5f); arcTo(1.8f, 1.8f, 0f, true, true, 12f, 2.9f); arcTo(1.8f, 1.8f, 0f, true, true, 12f, 6.5f); close()
        moveTo(12f, 13.8f); arcTo(1.8f, 1.8f, 0f, true, true, 12f, 10.2f); arcTo(1.8f, 1.8f, 0f, true, true, 12f, 13.8f); close()
        moveTo(12f, 21.1f); arcTo(1.8f, 1.8f, 0f, true, true, 12f, 17.5f); arcTo(1.8f, 1.8f, 0f, true, true, 12f, 21.1f); close()
    }
}

val ChevronIcon: ImageVector = materialIcon(name = "Custom.Chevron") {
    materialPath {
        moveTo(7.4f, 8.6f); lineTo(12f, 13.2f); lineTo(16.6f, 8.6f); lineTo(18f, 10f); lineTo(12f, 16f); lineTo(6f, 10f); close()
    }
}

val GearIcon: ImageVector get() = Icons.Default.Settings

val SpeakerIcon: ImageVector = materialIcon(name = "Custom.Speaker") {
    materialPath {
        moveTo(3f, 9f); verticalLineTo(15f); horizontalLineTo(7f); lineTo(12f, 20f); verticalLineTo(4f); lineTo(7f, 9f); close()
        moveTo(16.5f, 12f); curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f); verticalLineTo(16.02f); curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f); close()
    }
}

val FlameIcon: ImageVector = materialIcon(name = "Custom.Flame") {
    materialPath {
        moveTo(13.5f, 0.67f); curveTo(13.5f, 0.67f, 14.24f, 3.32f, 14.24f, 5.47f); curveTo(14.24f, 7.53f, 12.89f, 9.2f, 10.83f, 9.2f)
        curveTo(8.76f, 9.2f, 7.2f, 7.53f, 7.2f, 5.47f); lineTo(7.23f, 5.11f); curveTo(5.21f, 7.51f, 4f, 10.62f, 4f, 14f)
        curveTo(4f, 18.42f, 7.58f, 22f, 12f, 22f); curveTo(16.42f, 22f, 20f, 18.42f, 20f, 14f); curveTo(20f, 8.61f, 17.41f, 3.8f, 13.5f, 0.67f); close()
    }
}

// ---- Time helpers ----

/** Current time that updates every [intervalMs] while the screen is visible. */
@Composable
fun rememberNow(intervalMs: Long = 1_000L): State<Long> =
    produceState(initialValue = System.currentTimeMillis(), key1 = intervalMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(intervalMs - (System.currentTimeMillis() % intervalMs).coerceAtMost(intervalMs - 1))
        }
    }

/** Time formatter that follows the phone's 12/24-hour setting. */
@Composable
fun rememberTimeFormat(): java.text.DateFormat {
    val context = LocalContext.current
    return remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
}

fun java.text.DateFormat.formatMillis(ms: Long): String = format(Date(ms))

// ---- Project visuals ----

/**
 * The project's badge: its emoji (or short label like "MEP") on a tile tinted
 * with the project color. Falls back to the first letter of the name.
 */
@Composable
fun ProjectBadge(data: AppData, projectId: Long, size: Dp = 40.dp) {
    val p = data.project(projectId)
    val color = Color(p?.color ?: 0xFF5C6B78.toInt())
    val emoji = data.emojiOf(projectId)
    val label = emoji.ifBlank { p?.name?.firstOrNull()?.uppercase() ?: "?" }
    val isEmoji = emoji.isNotBlank() && emoji.any { Character.isSurrogate(it) || it.code > 0x2000 }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = when {
            isEmoji -> (size.value * 0.5f).sp
            label.length <= 1 -> (size.value * 0.45f).sp
            label.length == 2 -> (size.value * 0.36f).sp
            label.length == 3 -> (size.value * 0.30f).sp
            else -> (size.value * 0.24f).sp
        }
        Text(
            label,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = if (isEmoji) Color.Unspecified else color,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ColorDot(color: Int, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(color)))
}

// ---- Layout pieces ----

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = MaterialTheme.typography.headlineLarge, color = P.ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = P.inkMuted)
            }
        }
        actions()
    }
}

/** The app's one card style: a flat tile with a thin outline, like the Work app. */
@Composable
fun Slab(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val border = androidx.compose.foundation.BorderStroke(1.dp, P.border)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = P.slab, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = P.slab, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.headlineSmall, color = P.ink, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun EmptyState(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Slab(padding = 20.dp) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = P.ink)
        Spacer(Modifier.size(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = P.inkMuted)
        if (action != null) {
            Spacer(Modifier.size(14.dp))
            action()
        }
    }
}

/** Dropdown listing projects with their subprojects indented below. */
@Composable
fun ProjectPicker(
    data: AppData,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(12.dp),
            color = P.slabRaised,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectedId != null && data.project(selectedId) != null) {
                    ProjectBadge(data, selectedId, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(data.displayName(selectedId), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = P.ink)
                } else {
                    Text("Choose a project", modifier = Modifier.weight(1f), color = P.inkMuted)
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = P.inkMuted)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            data.ordered.filter { !data.isArchived(it.id) || it.id == selectedId }.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name, fontWeight = if (p.isSub) FontWeight.Normal else FontWeight.SemiBold) },
                    leadingIcon = {
                        Row {
                            if (p.isSub) Spacer(Modifier.width(18.dp))
                            ProjectBadge(data, p.id, if (p.isSub) 26.dp else 30.dp)
                        }
                    },
                    onClick = { onSelect(p.id); open = false },
                )
            }
        }
    }
}

/** Pill-shaped two- or three-way switch. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(P.slabRaised)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Surface(
                onClick = { onSelect(value) },
                shape = RoundedCornerShape(50),
                color = if (on) P.accent else Color.Transparent,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) P.onAccent else P.inkMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---- Android's own date and time pickers ----

fun pickDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth,
    ).show()
}

fun pickTime(context: Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        initial.hour, initial.minute,
        android.text.format.DateFormat.is24HourFormat(context),
    ).show()
}

/** "37h of 100h · 63h to go · 37%" with a bar — progress toward a project's total-hours goal. */
@Composable
fun TotalGoalBar(goal: com.rady.worktracker.data.GoalProgress, color: Color, modifier: Modifier = Modifier) {
    val pct = (goal.fraction * 100).toInt()
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("TOTAL GOAL", style = MaterialTheme.typography.labelSmall, color = P.inkMuted, modifier = Modifier.weight(1f))
            Text(
                if (goal.done) "🏁 ${com.rady.worktracker.data.formatDuration(goal.doneMs)} — goal reached!" else "$pct%",
                style = MaterialTheme.typography.labelMedium, color = P.accent, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(P.slabRaised)) {
            Box(
                Modifier.fillMaxWidth(goal.fraction.coerceAtLeast(0.015f)).fillMaxHeight().clip(RoundedCornerShape(50))
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color)))
            )
        }
        Spacer(Modifier.height(4.dp))
        val goalH = goal.goalMs / 3_600_000L
        Text(
            buildString {
                append("${com.rady.worktracker.data.formatDuration(goal.doneMs)} of ${goalH}h")
                if (!goal.done) append(" · ${com.rady.worktracker.data.formatDuration(goal.leftMs)} to go")
                goal.weeksToGo?.let { w ->
                    append(" · ")
                    append(if (w < 1.0) "under a week at your pace" else "≈ ${Math.ceil(w).toInt()} week${if (Math.ceil(w).toInt() == 1) "" else "s"} at your pace")
                }
            },
            style = MaterialTheme.typography.labelSmall, color = P.inkMuted,
        )
    }
}

/** Equal-width choice buttons in centred rows of [perRow] — tidy grids for durations. */
@Composable
fun ChoiceGrid(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, perRow: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.indices.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    val on = i == selected
                    Surface(
                        onClick = { onSelect(i) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (on) P.accent else P.slabRaised,
                        contentColor = if (on) P.onAccent else P.ink,
                        modifier = Modifier.weight(1f).height(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(labels[i], style = MaterialTheme.typography.labelLarge, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
                // keep the last row's buttons the same width as the others
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
