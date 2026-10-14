package com.rady.worktracker.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rady.worktracker.data.AppSettings
import com.rady.worktracker.data.Repo
import com.rady.worktracker.data.TimerEvent

/** Handles the notification's stop button, timer alerts, and phone restarts. */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        AppSettings.init(context)
        val now = System.currentTimeMillis()
        when (intent.action) {
            ACTION_STOP -> Repo.updateNow { it.closeActive(now) }

            ACTION_EVENT -> {
                val a = Repo.current.active
                val event = runCatching { TimerEvent.valueOf(intent.getStringExtra(TimerSync.EXTRA_EVENT) ?: "") }.getOrNull()
                val start = intent.getLongExtra(TimerSync.EXTRA_START, -1L)
                if (a != null && event != null && a.start == start) {
                    if (event == TimerEvent.END || (a.targetEnd != null && now >= a.targetEnd!!)) finishCountdown(context, now)
                    else TimerSync.notifyEvent(context, Repo.current, event, now - a.start)
                }
            }

            ACTION_DONE_V2, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> finishCountdown(context, now)
        }
        TimerSync.sync(context, Repo.current)
    }

    private fun finishCountdown(context: Context, now: Long) {
        val before = Repo.current.active ?: return
        val end = before.targetEnd ?: return
        if (now < end) return
        Repo.updateNow { it.finishCountdownIfDue(now) ?: it }
        TimerSync.notifyDone(context, Repo.current, before.projectId, before.targetMillis!!)
    }

    companion object {
        const val ACTION_STOP = "com.rady.worktracker.STOP"
        const val ACTION_EVENT = "com.rady.worktracker.TIMER_EVENT"
        /** Used by version 2 for the countdown end. */
        const val ACTION_DONE_V2 = "com.rady.worktracker.COUNTDOWN_DONE"
    }
}
