@file:OptIn(ExperimentalMaterial3Api::class)

package com.rady.worktracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rady.worktracker.TrackerViewModel
import com.rady.worktracker.data.AppSettings
import com.rady.worktracker.data.QuoteContext
import com.rady.worktracker.data.Rank
import com.rady.worktracker.data.legacyOf
import com.rady.worktracker.data.pickQuote
import com.rady.worktracker.data.streakDays
import com.rady.worktracker.data.toLocalDate
import com.rady.worktracker.data.weekRank
import com.rady.worktracker.timer.Sounds
import java.time.Instant
import java.time.ZoneId

enum class AppTab(val label: String) {
    TRACK("Track"),
    STATS("Stats"),
    HISTORY("History"),
    PROJECTS("Projects"),
    SETTINGS("Settings"),
}

private fun AppTab.icon(): ImageVector = when (this) {
    AppTab.TRACK -> TimerIcon
    AppTab.STATS -> BarChartIcon
    AppTab.HISTORY -> Icons.Default.DateRange
    AppTab.PROJECTS -> FolderTreeIcon
    AppTab.SETTINGS -> GearIcon
}

/** The rank pop-up shows once each time the app is started (not on every screen rotation). */
private object AppSession { var rankShown = false }

@Composable
fun App(vm: TrackerViewModel = viewModel()) {
    val context = LocalContext.current
    val data by vm.data.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AppTab.TRACK) }
    val now by rememberNow()
    val zone = remember { ZoneId.systemDefault() }
    val today = toLocalDate(now, zone)
    val minute = now / 60_000L
    val live = remember(data, minute) { data.sessionsIncludingActive(now) }
    val week = remember(live, today) { weekRank(data, live, today, today, zone) }
    val legacy = remember(live) { legacyOf(live) }
    val streak = remember(live, today) { streakDays(live, today, zone) }

    var rankQuote by remember { mutableStateOf<String?>(null) }
    var rankUp by remember { mutableStateOf<Rank?>(null) }

    fun quote(): String {
        val hour = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).hour
        val close = week.next?.let { n -> week.neededToday(n) in 1..45 * 60_000L } ?: false
        return pickQuote(QuoteContext(today.dayOfWeek, hour, streak, close))
    }

    fun showRank(withSound: Boolean) {
        rankQuote = quote()
        if (withSound && settings.rankSound) Sounds.play(context, Sounds.forRank(week.rank.level))
    }

    /** Celebrates a new, higher rank once per week; the first run and each new week start quietly. */
    fun checkRankUp(): Boolean {
        val weekKey = week.weekStart.toEpochDay()
        val seen = AppSettings.rankSeen()
        if (seen == null || seen.first != weekKey) {
            AppSettings.setRankSeen(weekKey, week.rank.level)
            return false
        }
        if (week.rank.level > seen.second) {
            AppSettings.setRankSeen(weekKey, week.rank.level)
            if (settings.rankPopup) {
                rankUp = week.rank
                if (settings.rankSound) Sounds.play(context, Sounds.forRank(week.rank.level))
                return true
            }
        }
        return false
    }

    // Save a countdown the moment it reaches its goal while the app is open.
    LaunchedEffect(now) { vm.checkCountdown() }
    // Get the latest from your other devices whenever the app comes to the front.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.syncNow(quiet = true) }
    LaunchedEffect(Unit) {
        vm.refreshTimerNotification()
        if (!AppSession.rankShown) {
            AppSession.rankShown = true
            val celebrated = checkRankUp()
            if (!celebrated && settings.rankPopup && data.sessions.isNotEmpty()) showRank(withSound = true)
        }
    }
    // A rank reached while the app is open (e.g. when a session is saved).
    LaunchedEffect(week.rank.level, week.weekStart) {
        if (AppSession.rankShown && rankQuote == null) checkRankUp()
    }

    Scaffold(
        containerColor = P.ground,
        bottomBar = {
            NavigationBar(containerColor = P.slab, tonalElevation = 0.dp) {
                AppTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon(), contentDescription = null) },
                        label = { Text(t.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = P.accent,
                            selectedTextColor = P.ink,
                            indicatorColor = P.accent.copy(alpha = 0.16f),
                            unselectedIconColor = P.inkMuted,
                            unselectedTextColor = P.inkMuted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                AppTab.TRACK -> TrackScreen(data, settings, vm, onShowRank = { showRank(withSound = true) })
                AppTab.STATS -> StatsScreen(data, onShowRank = { showRank(withSound = true) })
                AppTab.HISTORY -> HistoryScreen(data, vm)
                AppTab.PROJECTS -> ProjectsScreen(data, vm)
                AppTab.SETTINGS -> SettingsScreen(data, settings, week, vm)
            }
        }
    }

    rankUp?.let { r -> RankUpDialog(r, onDismiss = { rankUp = null }) }
    rankQuote?.let { q -> RankDialog(week, legacy, q, onDismiss = { rankQuote = null }) }
}
