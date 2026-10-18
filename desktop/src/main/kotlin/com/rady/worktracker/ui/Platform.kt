@file:OptIn(ExperimentalMaterial3Api::class)

package com.rady.worktracker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rady.worktracker.TrackerViewModel
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.AppFont
import com.rady.worktracker.data.Repo
import com.rady.worktracker.data.Settings
import com.rady.worktracker.data.Store
import com.rady.worktracker.data.TimerEvent
import com.rady.worktracker.desktop.DesktopSounds
import org.json.JSONObject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// Windows versions of the few things that differ between the phone and Windows apps.

fun appVersion(): String = System.getProperty("jpackage.app-version") ?: "dev"

@Composable
fun LogoMark(size: Dp) {
    Image(painterResource("logo_mark.png"), contentDescription = "Ydar Track logo",
        modifier = Modifier.height(size).aspectRatio(520f / 631f))
}

private fun family(name: String) = FontFamily(
    Font("fonts/${name}_400.ttf", FontWeight.Normal),
    Font("fonts/${name}_500.ttf", FontWeight.Medium),
    Font("fonts/${name}_600.ttf", FontWeight.SemiBold),
    Font("fonts/${name}_700.ttf", FontWeight.Bold),
)

private val families = HashMap<AppFont, FontFamily>()

fun fontFamilyFor(font: AppFont): FontFamily = families.getOrPut(font) {
    family(
        when (font) {
            AppFont.GOOGLE_SANS -> "googlesans"
            AppFont.INTER -> "inter"
            AppFont.ROBOTO -> "roboto"
            AppFont.MONTSERRAT -> "montserrat"
            AppFont.OPEN_SANS -> "opensans"
            AppFont.WORK_SANS -> "worksans"
            AppFont.LATO -> "lato"
            AppFont.JOSEFIN_SANS -> "josefinsans"
        }
    )
}

@Composable
fun rememberTimeFormat(): java.text.DateFormat = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }

/** Date and time pickers shown as in-app dialogs. */
class Pickers {
    internal var pendingDate by mutableStateOf<Pair<LocalDate, (LocalDate) -> Unit>?>(null)
    internal var pendingTime by mutableStateOf<Pair<LocalTime, (LocalTime) -> Unit>?>(null)
    fun date(initial: LocalDate, onPicked: (LocalDate) -> Unit) { pendingDate = initial to onPicked }
    fun time(initial: LocalTime, onPicked: (LocalTime) -> Unit) { pendingTime = initial to onPicked }
}

@Composable
fun rememberPickers(): Pickers {
    val p = remember { Pickers() }
    p.pendingDate?.let { (initial, cb) ->
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { p.pendingDate = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { cb(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    p.pendingDate = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { p.pendingDate = null }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    p.pendingTime?.let { (initial, cb) ->
        val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { p.pendingTime = null },
            containerColor = P.slab,
            title = { Text("Choose a time") },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { cb(LocalTime.of(state.hour, state.minute)); p.pendingTime = null }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { p.pendingTime = null }) { Text("Cancel") } },
        )
    }
    return p
}

class SoundFx {
    fun event(e: TimerEvent) = DesktopSounds.event(e)
    fun rank(level: Int) = DesktopSounds.rank(level)
}

@Composable
fun rememberSoundFx(): SoundFx = remember { SoundFx() }

@Composable
fun rememberOnTimerStarted(vm: TrackerViewModel): () -> Unit = remember { {} }

/** Windows file dialog. Returns the chosen file, or null. */
fun chooseFile(title: String, save: Boolean, suggestedName: String? = null, extensions: List<String> = emptyList()): File? {
    val d = FileDialog(null as Frame?, title, if (save) FileDialog.SAVE else FileDialog.LOAD)
    if (suggestedName != null) d.file = suggestedName
    else if (extensions.isNotEmpty()) d.file = extensions.joinToString(";") { "*.$it" }
    d.isVisible = true
    val name = d.file ?: return null
    return File(d.directory, name)
}

/** Settings › backup on Windows: save or restore a backup file (the online sync keeps a copy too). */
@Composable
fun BackupSection(settings: Settings, vm: TrackerViewModel) {
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<AppData?>(null) }
    Slab {
        SectionTitle("Backup")
        Text(
            "When you're signed in above, your data is also kept online. You can save a backup file anywhere too (PC, USB, OneDrive).",
            style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = {
                val f = chooseFile("Save backup", save = true, suggestedName = "Ydar Track backup ${LocalDate.now()}.json")
                if (f != null) message = runCatching {
                    f.writeText(JSONObject().put("app", "Ydar Track").put("format", 1).put("data", Store.toJson(Repo.current)).toString())
                    "Backup saved to ${f.name}"
                }.getOrElse { "Couldn't save: ${it.message}" }
            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Save backup file…", color = P.ink) }
            OutlinedButton(onClick = {
                val f = chooseFile("Restore backup", save = false, extensions = listOf("json"))
                if (f != null) runCatching {
                    val o = JSONObject(f.readText())
                    pendingRestore = Store.fromJson(o.optJSONObject("data") ?: o)
                }.onFailure { message = "That isn't a Ydar Track backup file." }
            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Restore…", color = P.ink) }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = P.info) }
    }
    pendingRestore?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = P.slab,
            title = { Text("Restore this backup?") },
            text = { Text("Everything in the app now will be replaced by this backup (${d.projects.size} projects, ${d.sessions.size} sessions).") },
            confirmButton = { TextButton(onClick = { vm.replaceAll(d); pendingRestore = null; message = "Restored ✔" }) { Text("Restore", color = P.accent) } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}
