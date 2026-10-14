@file:OptIn(ExperimentalLayoutApi::class)

package com.rady.worktracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rady.worktracker.TrackerViewModel
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.AppFont
import com.rady.worktracker.data.Settings
import com.rady.worktracker.data.ThemeMode
import com.rady.worktracker.data.TimerEvent
import com.rady.worktracker.data.WeekRank
import com.rady.worktracker.timer.Sounds

@Composable
fun SettingsScreen(data: AppData, settings: Settings, week: WeekRank, vm: TrackerViewModel) {
    val context = LocalContext.current
    val actions = rememberDataActions(vm)
    val hasData = data.sessions.isNotEmpty() || data.projects.isNotEmpty()
    fun set(t: (Settings) -> Settings) = vm.updateSettings(t)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Settings") }

        item {
            Slab {
                SectionTitle("Appearance")
                Spacer(Modifier.height(10.dp))
                Segmented(
                    options = ThemeMode.entries.map { it to it.label },
                    selected = settings.theme, onSelect = { m -> set { it.copy(theme = m) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text("Auto follows your phone's dark mode.", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                Spacer(Modifier.height(14.dp))
                Text("FONT", style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFont.entries.forEach { f ->
                        val on = settings.font == f
                        Surface(
                            onClick = { set { it.copy(font = f) } }, shape = RoundedCornerShape(12.dp),
                            color = if (on) P.accent else P.slabRaised, contentColor = if (on) P.onAccent else P.ink,
                        ) {
                            Text(
                                f.label, Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = fontFamilyFor(f)),
                            )
                        }
                    }
                }
            }
        }

        item { DriveBackupCard(settings, vm) }

        item {
            Slab {
                SectionTitle("Daily goal")
                Text("Fills the ring on the home screen.", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundButton("−") { set { it.copy(dailyGoalMin = (it.dailyGoalMin - 15).coerceAtLeast(15)) } }
                    Text(minutesLabel(settings.dailyGoalMin), style = MaterialTheme.typography.displaySmall, color = P.accent,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    RoundButton("+") { set { it.copy(dailyGoalMin = (it.dailyGoalMin + 15).coerceAtMost(16 * 60)) } }
                }
            }
        }

        item {
            Slab {
                SectionTitle("Timer sounds")
                Text("Each alert has its own sound. Tap the speaker to hear it.", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                Spacer(Modifier.height(6.dp))
                SoundRow("Lap chime", "Open timer: plays every lap", settings.lapOn, { v -> set { it.copy(lapOn = v) } }) {
                    Sounds.play(context, Sounds.forEvent(TimerEvent.LAP))
                }
                if (settings.lapOn) {
                    FlowRow(Modifier.padding(start = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 20, 25, 30, 45, 60, 90).forEach { m ->
                            val on = settings.lapMin == m
                            Surface(onClick = { set { it.copy(lapMin = m) } }, shape = RoundedCornerShape(50),
                                color = if (on) P.accent else P.slabRaised, contentColor = if (on) P.onAccent else P.ink) {
                                Text("Every ${minutesLabel(m)}", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                HorizontalDivider(color = P.rule.copy(alpha = 0.6f))
                SoundRow("Halfway", "Countdown: at the middle", settings.halfOn, { v -> set { it.copy(halfOn = v) } }) {
                    Sounds.play(context, Sounds.forEvent(TimerEvent.HALF))
                }
                SoundRow("5 minutes left", "Countdown: 5 minutes before the end", settings.fiveOn, { v -> set { it.copy(fiveOn = v) } }) {
                    Sounds.play(context, Sounds.forEvent(TimerEvent.FIVE_LEFT))
                }
                SoundRow("Time's up", "Countdown: at the end (the session is saved either way)", settings.endOn, { v -> set { it.copy(endOn = v) } }) {
                    Sounds.play(context, Sounds.forEvent(TimerEvent.END))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Alerts come as notifications, so they work with the app closed. If you don't hear them, check that notifications for Ydar Track are allowed.",
                    style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
                )
            }
        }

        item {
            Slab {
                SectionTitle("Weekly rank")
                Text("10 ranks, based on your average per day this week. Resets every Monday. Your legacy never resets.", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                Spacer(Modifier.height(6.dp))
                ToggleRow("Show my rank when the app opens", settings.rankPopup) { v -> set { it.copy(rankPopup = v) } }
                SoundRow("Rank sound", "A different sound for every rank", settings.rankSound, { v -> set { it.copy(rankSound = v) } }) {
                    Sounds.play(context, Sounds.forRank(week.rank.level))
                }
                Spacer(Modifier.height(8.dp))
                RankLadder(week)
            }
        }

        item {
            Slab {
                SectionTitle("Excel")
                Spacer(Modifier.height(4.dp))
                ActionRow("Export to Excel", "Sessions, summary, projects and weekly ranks", enabled = hasData, onClick = actions.export)
                ActionRow("Share Excel file…", "Send it by email, WhatsApp or Drive", enabled = hasData, onClick = actions.share)
                ActionRow("Import from Excel or CSV", "Adds sessions and projects; duplicates are skipped", onClick = actions.import)
                ActionRow("Get a blank import template", "Columns ready to fill in", onClick = actions.template)
            }
        }

        item {
            Slab(padding = 20.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    LogoMark(96.dp)
                    Spacer(Modifier.height(8.dp))
                    Wordmark(30.sp)
                    Text(SLOGAN, style = MaterialTheme.typography.bodyMedium, color = P.inkMuted)
                    Text("You are your greatest project. Keep building.", style = MaterialTheme.typography.bodySmall, color = P.accent)
                    Spacer(Modifier.height(8.dp))
                    Text("Version ${com.rady.worktracker.BuildConfig.VERSION_NAME} · Your data stays on this phone unless you back it up to Drive.", style = MaterialTheme.typography.labelSmall, color = P.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun RoundButton(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = P.slabRaised) {
        Text(label, Modifier.padding(horizontal = 18.dp, vertical = 6.dp), style = MaterialTheme.typography.headlineMedium, color = P.ink)
    }
}

@Composable
private fun SoundRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit, onPreview: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPreview) { Icon(SpeakerIcon, contentDescription = "Play $title", tint = P.accent) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = P.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChecked, colors = switchColors())
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = P.ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked, colors = switchColors())
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = P.onAccent, checkedTrackColor = P.accent,
    uncheckedThumbColor = P.inkMuted, uncheckedTrackColor = P.slabRaised, uncheckedBorderColor = P.rule,
)

@Composable
private fun ActionRow(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) P.ink else P.inkMuted)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
        }
        Icon(ChevronIcon, contentDescription = null, tint = P.inkMuted, modifier = Modifier.padding(start = 8.dp).rotate(-90f))
    }
}
