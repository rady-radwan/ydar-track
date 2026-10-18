package com.rady.worktracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rady.worktracker.data.AppSettings
import com.rady.worktracker.data.Repo
import com.rady.worktracker.data.ThemeMode
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rady.worktracker.data.AppData
import com.rady.worktracker.timer.TimerSync
import com.rady.worktracker.ui.App
import com.rady.worktracker.ui.WorkTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.rady.worktracker.data.AndroidInit.init(this)
        enableEdgeToEdge()
        setContent {
            val settings by AppSettings.flow.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status-bar icons follow the app's theme, not just the phone's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            val vm: TrackerViewModel = viewModel { TrackerViewModel(filesDir, AndroidHooks(application)) }
            WorkTrackerTheme(dark = dark, font = settings.font) {
                App(vm)
            }
        }
    }
}

/** Android: the running timer lives in a notification, with alarms for its sounds. */
class AndroidHooks(private val app: android.app.Application) : PlatformHooks {
    override fun timerChanged(data: AppData) = TimerSync.sync(app, data)
    override fun countdownFinished(data: AppData, projectId: Long, durationMs: Long) =
        TimerSync.notifyDone(app, data, projectId, durationMs)
}
