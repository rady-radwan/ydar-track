package com.rady.worktracker.desktop

import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import com.rady.worktracker.PlatformHooks
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.AppSettings
import com.rady.worktracker.data.Repo
import com.rady.worktracker.data.TimerEvent
import com.rady.worktracker.data.formatDuration
import com.rady.worktracker.data.nextTimerEvent
import java.io.BufferedInputStream
import java.util.Timer
import java.util.TimerTask
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

/** Plays the app's sounds (WAV files bundled with the app). */
object DesktopSounds {
    fun play(name: String) {
        Thread {
            runCatching {
                val stream = DesktopSounds::class.java.getResourceAsStream("/sounds/$name.wav") ?: return@Thread
                val audio = AudioSystem.getAudioInputStream(BufferedInputStream(stream))
                val clip = AudioSystem.getClip()
                clip.addLineListener { if (it.type == LineEvent.Type.STOP) clip.close() }
                clip.open(audio)
                clip.start()
            }
        }.apply { isDaemon = true }.start()
    }

    fun event(e: TimerEvent) = play(
        when (e) {
            TimerEvent.LAP -> "lap"
            TimerEvent.HALF -> "halfway"
            TimerEvent.FIVE_LEFT -> "five_min"
            TimerEvent.END -> "finish"
        }
    )

    fun rank(level: Int) = play("rank_${level.coerceIn(0, 9)}")
}

/**
 * Windows: lap / halfway / 5-minutes-left / end sounds with a notification,
 * even while the window is hidden in the system tray.
 */
object DesktopHooks : PlatformHooks {
    @Volatile var tray: TrayState? = null
    private val timer = Timer("ydar-alerts", true)
    private var task: TimerTask? = null

    private fun notify(title: String, text: String) {
        runCatching { tray?.sendNotification(Notification(title, text, Notification.Type.Info)) }
    }

    @Synchronized
    override fun timerChanged(data: AppData) {
        task?.cancel()
        task = null
        val a = data.active ?: return
        val next = nextTimerEvent(a, AppSettings.current, System.currentTimeMillis()) ?: return
        val (at, event) = next
        task = object : TimerTask() {
            override fun run() {
                val now = Repo.current
                val cur = now.active
                if (cur == null || cur.start != a.start || cur.projectId != a.projectId) return
                val s = AppSettings.current
                val name = now.displayName(cur.projectId)
                when (event) {
                    TimerEvent.LAP -> if (s.lapOn) {
                        DesktopSounds.event(event)
                        notify("Lap · ${formatDuration(at - cur.start)}", "Still going on $name. Nice work!")
                    }
                    TimerEvent.HALF -> if (s.halfOn) {
                        DesktopSounds.event(event)
                        notify("Halfway there", "${formatDuration(cur.start + (cur.targetMillis ?: 0L) - at)} left on $name")
                    }
                    TimerEvent.FIVE_LEFT -> if (s.fiveOn) {
                        DesktopSounds.event(event)
                        notify("5 minutes left", "Wrap up $name — almost done")
                    }
                    TimerEvent.END -> {
                        val target = cur.targetMillis ?: 0L
                        Repo.update { it.finishCountdownIfDue(System.currentTimeMillis()) ?: it }
                        countdownFinished(Repo.current, cur.projectId, target)
                    }
                }
                timerChanged(Repo.current)
            }
        }
        timer.schedule(task, (at - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    override fun countdownFinished(data: AppData, projectId: Long, durationMs: Long) {
        if (AppSettings.current.endOn) DesktopSounds.event(TimerEvent.END)
        notify("Session complete 🎉", "${formatDuration(durationMs)} on ${data.displayName(projectId)} — saved")
    }
}
