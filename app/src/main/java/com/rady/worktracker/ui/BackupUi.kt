package com.rady.worktracker.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.Identity
import com.rady.worktracker.TrackerViewModel
import com.rady.worktracker.backup.BackupFile
import com.rady.worktracker.backup.BackupScheduler
import com.rady.worktracker.backup.DriveAuth
import com.rady.worktracker.backup.DriveBackup
import com.rady.worktracker.backup.DriveClient
import com.rady.worktracker.backup.DriveFile
import com.rady.worktracker.data.AppSettings
import com.rady.worktracker.data.BackupFrequency
import com.rady.worktracker.data.Settings
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.util.Date

/** Settings card: connect Google Drive, choose daily/weekly backups, back up now, restore. */
@Composable
fun DriveBackupCard(settings: Settings, vm: TrackerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var afterAuth by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var driveList by remember { mutableStateOf<List<DriveFile>?>(null) }
    var pendingRestore by remember { mutableStateOf<DriveFile?>(null) }
    var token by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        try {
            val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data ?: Intent())
            val t = result.accessToken
            if (t != null) afterAuth?.invoke(t) else toast("Google Drive wasn't connected")
        } catch (e: Throwable) {
            toast("Google Drive wasn't connected")
        }
        afterAuth = null
    }

    /** Gets a Drive access token, showing Google's approval screen if needed. */
    fun withDrive(action: (String) -> Unit) {
        Identity.getAuthorizationClient(context).authorize(DriveAuth.request())
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) {
                    afterAuth = action
                    authLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else r.accessToken?.let(action)
            }
            .addOnFailureListener { e ->
                toast("Couldn't reach Google: ${e.message ?: "see the Drive setup steps in the README"}")
            }
    }

    fun work(label: String, block: suspend () -> Unit) {
        scope.launch {
            busy = label
            try { block() } catch (t: Throwable) { toast("${t.message?.take(160) ?: t.javaClass.simpleName}") } finally { busy = null }
        }
    }

    fun connect() = withDrive { t ->
        work("Connecting…") {
            val c = DriveClient(t)
            val email = c.email()
            c.folderId()
            AppSettings.update {
                it.copy(driveConnected = true, driveEmail = email,
                    backupFrequency = if (it.backupFrequency == BackupFrequency.OFF) BackupFrequency.WEEKLY else it.backupFrequency)
            }
            BackupScheduler.apply(context)
            toast("Connected${email?.let { " as $it" } ?: ""}")
        }
    }

    fun backupNow() = withDrive { t ->
        work("Backing up…") {
            try {
                DriveBackup.run(context, t, settings.keepBackups)
                toast("Backup saved to Google Drive ✔")
            } catch (e: Throwable) {
                AppSettings.update { it.copy(lastBackupStatus = "Failed: ${e.message?.take(120)}") }
                throw e
            }
        }
    }

    fun listBackups() = withDrive { t ->
        work("Loading backups…") {
            val c = DriveClient(t)
            driveList = c.list(c.folderId()).filter { it.name.startsWith(BackupFile.PREFIX) }
            token = t
        }
    }

    fun setFrequency(f: BackupFrequency) {
        AppSettings.update { it.copy(backupFrequency = f) }
        BackupScheduler.apply(context)
    }

    Slab {
        SectionTitle("Google Drive backup")
        Text(
            if (settings.driveConnected) "✅ Connected${settings.driveEmail?.let { " · $it" } ?: ""}" else "Not connected",
            style = MaterialTheme.typography.titleSmall, color = if (settings.driveConnected) P.ink else P.inkMuted,
        )
        Text(
            "Your projects, sessions and goals go to a “${DriveClient.FOLDER_NAME}” folder in your Drive. " +
                "Ydar Track can only see the files it makes there.",
            style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
        )
        Spacer(Modifier.height(12.dp))

        if (!settings.driveConnected) {
            Button(
                onClick = { connect() }, enabled = busy == null,
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = P.accent, contentColor = P.onAccent),
            ) { Text("Connect Google Drive", style = MaterialTheme.typography.titleSmall) }
        } else {
            Text("AUTOMATIC BACKUP", style = MaterialTheme.typography.labelMedium, color = P.inkMuted)
            Spacer(Modifier.height(6.dp))
            Segmented(
                options = BackupFrequency.entries.map { it to it.label },
                selected = settings.backupFrequency, onSelect = ::setFrequency,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().clickable {
                    AppSettings.update { it.copy(backupWifiOnly = !it.backupWifiOnly) }; BackupScheduler.apply(context)
                }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Only on Wi-Fi", style = MaterialTheme.typography.bodyMedium, color = P.ink, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.backupWifiOnly,
                    onCheckedChange = { v -> AppSettings.update { it.copy(backupWifiOnly = v) }; BackupScheduler.apply(context) },
                    colors = SwitchDefaults.colors(checkedThumbColor = P.onAccent, checkedTrackColor = P.accent),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep the last", style = MaterialTheme.typography.bodyMedium, color = P.ink, modifier = Modifier.weight(1f))
                listOf(5, 10, 30).forEach { n ->
                    val on = settings.keepBackups == n
                    TextButton(onClick = { AppSettings.update { it.copy(keepBackups = n) } }) {
                        Text("$n", color = if (on) P.accent else P.inkMuted, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            val last = settings.lastBackupAt
            Text(
                when {
                    last > 0 -> "Last backup: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last))}"
                    else -> "No backup yet"
                } + if (settings.lastBackupStatus.isNotBlank() && settings.lastBackupStatus != "OK") " · ${settings.lastBackupStatus}" else "",
                style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { backupNow() }, enabled = busy == null, modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = P.accent, contentColor = P.onAccent),
                ) { Text("Back up now", style = MaterialTheme.typography.titleSmall) }
                OutlinedButton(
                    onClick = { listBackups() }, enabled = busy == null, modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Restore…", style = MaterialTheme.typography.titleSmall, color = P.ink) }
            }
            TextButton(onClick = {
                AppSettings.update { it.copy(driveConnected = false, driveEmail = null) }
                BackupScheduler.apply(context)
            }) { Text("Disconnect", color = P.inkMuted) }
        }
        busy?.let {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = P.accent)
                Spacer(Modifier.width(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
            }
        }
    }

    driveList?.let { list ->
        AlertDialog(
            onDismissRequest = { driveList = null },
            containerColor = P.slab,
            title = { Text("Restore a backup") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Text("No backups in Google Drive yet.", color = P.inkMuted)
                    list.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(color = P.rule.copy(alpha = 0.5f))
                        Column(Modifier.fillMaxWidth().clickable { pendingRestore = f; driveList = null }.padding(vertical = 10.dp)) {
                            val created = runCatching { Date.from(Instant.parse(f.createdTime)) }.getOrNull()
                            Text(
                                created?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it) } ?: f.name,
                                style = MaterialTheme.typography.titleSmall, color = P.ink,
                            )
                            Text("${(f.size + 1023) / 1024} KB", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { driveList = null }) { Text("Close") } },
        )
    }

    pendingRestore?.let { f ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = P.slab,
            title = { Text("Restore this backup?") },
            text = { Text("Everything in the app now will be replaced by this backup. Tip: tap “Back up now” first if you want to keep today's data too.") },
            confirmButton = {
                TextButton(onClick = {
                    val t = token
                    pendingRestore = null
                    if (t != null) work("Restoring…") {
                        val data = BackupFile.read(DriveClient(t).download(f.id))
                        vm.replaceAll(data)
                        toast("Restored ✔ ${data.projects.size} projects, ${data.sessions.size} sessions")
                    }
                }) { Text("Restore", color = P.accent) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}
