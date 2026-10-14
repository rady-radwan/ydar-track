package com.rady.worktracker.timer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rady.worktracker.MainActivity
import com.rady.worktracker.R
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.AppSettings
import com.rady.worktracker.data.TimerEvent
import com.rady.worktracker.data.formatDuration
import com.rady.worktracker.data.nextTimerEvent

/**
 * Keeps the phone in step with the running timer:
 *  - an ongoing notification with a live clock and a "Stop & save" button
 *  - an alarm for the next alert (lap chime, halfway, 5 minutes left, end),
 *    each with its own sound, which works even when the app is closed.
 */
object TimerSync {
    const val CH_RUNNING = "timer_running"
    private const val CH_LAP = "alert_lap_v3"
    private const val CH_HALF = "alert_half_v3"
    private const val CH_FIVE = "alert_five_v3"
    private const val CH_END = "alert_end_v3"
    private const val CH_END_QUIET = "alert_end_quiet_v3"

    private const val ID_RUNNING = 1001
    private const val ID_ALERT = 1002

    const val EXTRA_EVENT = "event"
    const val EXTRA_START = "start"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        runCatching { nm.deleteNotificationChannel("timer_done") } // replaced by the channels below
        nm.createNotificationChannel(
            NotificationChannel(CH_RUNNING, "Running timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the session you're timing"
                setShowBadge(false)
            }
        )
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        fun sound(id: String, name: String, desc: String, res: Int) {
            nm.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = desc
                    setSound(Sounds.uri(context, res), attrs)
                    enableVibration(true)
                }
            )
        }
        sound(CH_LAP, "Lap chime", "Open timer: chime every lap", R.raw.lap)
        sound(CH_HALF, "Halfway", "Countdown: halfway point", R.raw.halfway)
        sound(CH_FIVE, "5 minutes left", "Countdown: five minutes before the end", R.raw.five_min)
        sound(CH_END, "Countdown finished", "Countdown: time is up and the session is saved", R.raw.finish)
        nm.createNotificationChannel(
            NotificationChannel(CH_END_QUIET, "Countdown finished (silent)", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
            }
        )
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun stopIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 1,
            Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun eventIntent(context: Context, event: TimerEvent? = null, start: Long = 0L): PendingIntent =
        PendingIntent.getBroadcast(
            context, 2,
            Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_EVENT).apply {
                if (event != null) { putExtra(EXTRA_EVENT, event.name); putExtra(EXTRA_START, start) }
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun title(data: AppData, projectId: Long): String {
        val emoji = data.emojiOf(projectId)
        return (if (emoji.isNotBlank()) "$emoji  " else "") + data.displayName(projectId)
    }

    /** Call after anything that may have started, changed or stopped the timer. */
    fun sync(context: Context, data: AppData) {
        ensureChannels(context)
        val a = data.active
        val nm = NotificationManagerCompat.from(context)
        if (a == null) {
            nm.cancel(ID_RUNNING)
            cancelAlarm(context)
            return
        }
        val text = if (a.targetMillis != null) "${formatDuration(a.targetMillis)} countdown" else "Open timer"
        if (canNotify(context)) {
            val b = NotificationCompat.Builder(context, CH_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(title(data, a.projectId))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setColor(data.project(a.projectId)?.color ?: 0xFFFFB627.toInt())
                .setContentIntent(openAppIntent(context))
                .addAction(0, if (a.targetMillis != null) "End now & save" else "Stop & save", stopIntent(context))
                .setUsesChronometer(true)
            if (a.targetEnd != null) {
                b.setWhen(a.targetEnd!!)
                b.setChronometerCountDown(true)
            } else {
                b.setWhen(a.start)
            }
            runCatching { nm.notify(ID_RUNNING, b.build()) }
        }
        val next = nextTimerEvent(a, AppSettings.current, System.currentTimeMillis())
        if (next != null) scheduleAlarm(context, next.first, eventIntent(context, next.second, a.start))
        else cancelAlarm(context)
    }

    /** Posts the alert for a lap / halfway / 5-minutes-left event (each channel has its own sound). */
    fun notifyEvent(context: Context, data: AppData, event: TimerEvent, elapsedMs: Long) {
        ensureChannels(context)
        if (!canNotify(context)) return
        val a = data.active ?: return
        val s = AppSettings.current
        val enabled = when (event) {
            TimerEvent.LAP -> s.lapOn
            TimerEvent.HALF -> s.halfOn
            TimerEvent.FIVE_LEFT -> s.fiveOn
            TimerEvent.END -> s.endOn
        }
        if (!enabled) return
        val left = (a.targetMillis ?: 0L) - elapsedMs
        val (channel, title, text) = when (event) {
            TimerEvent.LAP -> Triple(CH_LAP, "Lap · ${formatDuration(elapsedMs)}", "Still going on ${data.displayName(a.projectId)}. Nice work!")
            TimerEvent.HALF -> Triple(CH_HALF, "Halfway there", "${formatDuration(left)} left on ${data.displayName(a.projectId)}")
            TimerEvent.FIVE_LEFT -> Triple(CH_FIVE, "5 minutes left", "Wrap up ${data.displayName(a.projectId)} — almost done")
            TimerEvent.END -> return
        }
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .setContentIntent(openAppIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_ALERT, n) }
    }

    fun notifyDone(context: Context, data: AppData, projectId: Long, durationMs: Long) {
        ensureChannels(context)
        NotificationManagerCompat.from(context).cancel(ID_RUNNING)
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, if (AppSettings.current.endOn) CH_END else CH_END_QUIET)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Session complete 🎉")
            .setContentText("${formatDuration(durationMs)} on ${title(data, projectId)} — saved")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_ALERT, n) }
    }

    private fun scheduleAlarm(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(eventIntent(context))
        // Alarm from version 2 of the app, if one is still pending.
        am.cancel(
            PendingIntent.getBroadcast(
                context, 2, Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_DONE_V2),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
    }
}
