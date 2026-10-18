package com.rady.worktracker.backup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rady.worktracker.R
import com.rady.worktracker.data.AppSettings
import com.rady.worktracker.data.BackupFrequency
import com.rady.worktracker.data.Repo
import java.util.concurrent.TimeUnit

/** Runs the daily / weekly Google Drive backup in the background. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        com.rady.worktracker.data.AndroidInit.init(applicationContext)
        val s = AppSettings.current
        if (!s.driveConnected || s.backupFrequency == BackupFrequency.OFF) return Result.success()
        return try {
            val token = DriveAuth.silentToken(applicationContext)
            if (token == null) {
                AppSettings.update { it.copy(lastBackupStatus = "Needs you to reconnect Google Drive") }
                notify(applicationContext, "Backup paused",
                    "Open Ydar Track › Settings and tap “Connect Google Drive” to continue automatic backups.")
                return Result.failure()
            }
            DriveBackup.run(applicationContext, token, s.keepBackups)
            Result.success()
        } catch (t: Throwable) {
            AppSettings.update { it.copy(lastBackupStatus = "Failed: ${t.message?.take(120) ?: t.javaClass.simpleName}") }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun notify(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("backup", "Backups", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val n = NotificationCompat.Builder(context, "backup")
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(2001, n) }
    }
}

object BackupScheduler {
    private const val NAME = "drive_backup"

    fun apply(context: Context) {
        val s = AppSettings.current
        val wm = WorkManager.getInstance(context)
        if (!s.driveConnected || s.backupFrequency == BackupFrequency.OFF) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val days = if (s.backupFrequency == BackupFrequency.DAILY) 1L else 7L
        val req = PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (s.backupWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setInitialDelay(if (s.backupFrequency == BackupFrequency.DAILY) 2 else 12, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
