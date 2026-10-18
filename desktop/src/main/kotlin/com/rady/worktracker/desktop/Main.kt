package com.rady.worktracker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.rady.worktracker.TrackerViewModel
import com.rady.worktracker.data.AppSettings
import com.rady.worktracker.data.Repo
import com.rady.worktracker.data.ThemeMode
import com.rady.worktracker.ui.App
import com.rady.worktracker.ui.P
import com.rady.worktracker.ui.WorkTrackerTheme

fun main() {
    val dir = DesktopPaths.dataDir
    Repo.init(dir)
    AppSettings.init(PropertiesKeyValues(java.io.File(dir, "settings.properties")))

    application {
        val vm = remember { TrackerViewModel(dir, DesktopHooks) }
        var visible by remember { mutableStateOf(true) }
        val trayState = rememberTrayState()
        DesktopHooks.tray = trayState
        val icon = painterResource("icon.png")

        Tray(
            state = trayState,
            icon = painterResource("tray.png"),
            tooltip = "Ydar Track",
            onAction = { visible = true },
            menu = {
                Item("Open Ydar Track", onClick = { visible = true })
                Item("Quit", onClick = ::exitApplication)
            },
        )

        Window(
            onCloseRequest = {
                // With a timer running, keep going in the tray so its sounds still play.
                if (Repo.current.active != null) visible = false else exitApplication()
            },
            visible = visible,
            title = "Ydar Track",
            icon = icon,
            state = rememberWindowState(size = DpSize(560.dp, 940.dp)),
        ) {
            val settings by AppSettings.flow.collectAsState()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            WorkTrackerTheme(dark = dark, font = settings.font) {
                Box(Modifier.fillMaxSize().background(P.ground), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 760.dp).fillMaxHeight()) { App(vm) }
                }
            }
        }

        LaunchedEffect(Unit) { DesktopHooks.timerChanged(Repo.current) }
    }
}
