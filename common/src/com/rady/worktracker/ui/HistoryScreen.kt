@file:OptIn(ExperimentalMaterial3Api::class)

package com.rady.worktracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rady.worktracker.TrackerViewModel
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.Session
import com.rady.worktracker.data.dayHeader
import com.rady.worktracker.data.formatDuration
import com.rady.worktracker.data.toLocalDate
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen(data: AppData, vm: TrackerViewModel) {
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val timeFmt = rememberTimeFormat()

    val groups: Map<LocalDate, List<Session>> = remember(data.sessions) {
        data.sessions.sortedByDescending { it.start }.groupBy { toLocalDate(it.start, zone) }
    }

    var editing by remember { mutableStateOf<Session?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader("History", subtitle = "${data.sessions.size} sessions") {
                IconButton(onClick = { adding = true }, enabled = data.projects.isNotEmpty()) {
                    Icon(Icons.Default.Add, contentDescription = "Add a past session")
                }
                DataMenu(data, vm)
            }
        }

        if (groups.isEmpty()) {
            item {
                EmptyState(
                    "No sessions yet",
                    "Finished timers and sessions you add by hand show up here, grouped by day. You can also bring in past records from Excel with the ⋮ menu.",
                )
            }
        }

        groups.forEach { (date, list) ->
            item(key = "day-$date") {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(dayHeader(date, today), style = MaterialTheme.typography.titleMedium, color = P.ink, modifier = Modifier.weight(1f))
                    Text(formatDuration(list.sumOf { it.durationMillis }), style = MaterialTheme.typography.titleMedium, color = P.accent)
                }
            }
            items(list, key = { it.id }) { s ->
                Slab(onClick = { editing = s }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProjectBadge(data, s.projectId, 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(data.displayName(s.projectId), style = MaterialTheme.typography.titleSmall, color = P.ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${timeFmt.formatMillis(s.start)} – ${timeFmt.formatMillis(s.end)}",
                                style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                            if (s.note.isNotBlank()) {
                                Text(s.note, style = MaterialTheme.typography.bodySmall, color = P.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(formatDuration(s.durationMillis), style = MaterialTheme.typography.titleMedium, color = P.ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    editing?.let { s ->
        SessionDialog(
            data = data, initial = s, defaultProjectId = s.projectId,
            onDismiss = { editing = null },
            onSave = { pid, start, end, note -> vm.updateSession(s.copy(projectId = pid, start = start, end = end, note = note)); editing = null },
            onDelete = { vm.deleteSession(s.id); editing = null },
        )
    }
    if (adding) {
        SessionDialog(
            data = data, initial = null, defaultProjectId = data.sessions.maxByOrNull { it.start }?.projectId,
            onDismiss = { adding = false },
            onSave = { pid, start, end, note -> vm.addSession(pid, start, end, note); adding = false },
            onDelete = null,
        )
    }
}
