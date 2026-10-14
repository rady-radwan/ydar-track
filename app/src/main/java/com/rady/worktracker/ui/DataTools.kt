@file:OptIn(ExperimentalMaterial3Api::class)

package com.rady.worktracker.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.rady.worktracker.TrackerViewModel
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.CsvReader
import com.rady.worktracker.data.ExcelExport
import com.rady.worktracker.data.ExcelImport
import com.rady.worktracker.data.ImportPlan
import com.rady.worktracker.data.RSheet
import com.rady.worktracker.data.Repo
import com.rady.worktracker.data.XlsxReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Excel actions, usable from any screen. Call [rememberDataActions] once per screen. */
class DataActions(
    val export: () -> Unit,
    val share: () -> Unit,
    val import: () -> Unit,
    val template: () -> Unit,
)

@Composable
fun rememberDataActions(vm: TrackerViewModel): DataActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    var pending by remember { mutableStateOf<Pair<List<RSheet>, ImportPlan>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ExcelExport.workbook(Repo.current, zone)
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                }.isSuccess
            }
            toast(if (ok) "Exported ${Repo.current.sessions.size} sessions to Excel" else "Couldn't save the file. Try another folder.")
        }
    }

    val saveTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(ExcelExport.template()) } }.isSuccess
            }
            toast(if (ok) "Template saved. Fill the Sessions sheet, then import it." else "Couldn't save the file. Try another folder.")
        }
    }

    val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val sheets = readSheets(context, uri)
                    sheets to ExcelImport.plan(sheets, Repo.current, zone)
                }
            }
            busy = false
            result.onSuccess { pending = it }
                .onFailure { toast(it.message ?: "Couldn't read that file. Use an .xlsx or .csv file.") }
        }
    }

    if (busy) {
        AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Reading file…") })
    }

    pending?.let { (sheets, plan) ->
        ImportDialog(
            plan = plan,
            onDismiss = { pending = null },
            onConfirm = {
                // Re-plan against the latest data so nothing changed meanwhile is lost.
                val fresh = ExcelImport.plan(sheets, Repo.current, zone)
                vm.replaceAll(fresh.result)
                pending = null
                toast("Imported ${fresh.sessionsAdded} session${if (fresh.sessionsAdded == 1) "" else "s"}")
            },
        )
    }

    return DataActions(
        export = { saveExport.launch("Ydar Track ${LocalDate.now()}.xlsx") },
        share = { shareExport(context, zone) },
        import = { pickImport.launch(arrayOf(XLSX, "text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/octet-stream")) },
        template = { saveTemplate.launch("Ydar Track import template.xlsx") },
    )
}

/** "⋮" button with Excel export / import, shown in the History and Projects headers. */
@Composable
fun DataMenu(data: AppData, vm: TrackerViewModel) {
    val actions = rememberDataActions(vm)
    var open by remember { mutableStateOf(false) }
    val hasData = data.sessions.isNotEmpty() || data.projects.isNotEmpty()
    Box {
        IconButton(onClick = { open = true }) { Icon(MoreIcon, contentDescription = "Import and export") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Export to Excel") }, enabled = hasData, onClick = { open = false; actions.export() })
            DropdownMenuItem(text = { Text("Share Excel file…") }, enabled = hasData, onClick = { open = false; actions.share() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Import from Excel or CSV") }, onClick = { open = false; actions.import() })
            DropdownMenuItem(text = { Text("Get a blank import template") }, onClick = { open = false; actions.template() })
        }
    }
}

@Composable
private fun ImportDialog(plan: ImportPlan, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.slab,
        title = { Text(if (plan.hasChanges) "Import this file?" else "Nothing new to import") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From sheet \"${plan.sheetName}\"", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                Line("${plan.sessionsAdded}", "new session${if (plan.sessionsAdded == 1) "" else "s"} will be added")
                if (plan.duplicatesSkipped > 0) Line("${plan.duplicatesSkipped}", "already in the app — skipped")
                if (plan.projectsCreated.isNotEmpty()) {
                    Line("${plan.projectsCreated.size}", "new project${if (plan.projectsCreated.size == 1) "" else "s"}: " +
                        plan.projectsCreated.take(6).joinToString(", ") + if (plan.projectsCreated.size > 6) "…" else "")
                }
                if (plan.issues.isNotEmpty()) {
                    Text("${plan.issues.size} row${if (plan.issues.size == 1) "" else "s"} couldn't be read:",
                        style = MaterialTheme.typography.titleSmall, color = P.danger)
                    plan.issues.take(8).forEach {
                        Text(if (it.row > 0) "Row ${it.row}: ${it.message}" else it.message, style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                    }
                    if (plan.issues.size > 8) Text("…and ${plan.issues.size - 8} more", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                }
            }
        },
        confirmButton = {
            if (plan.hasChanges) TextButton(onClick = onConfirm) { Text("Import") }
            else TextButton(onClick = onDismiss) { Text("OK") }
        },
        dismissButton = { if (plan.hasChanges) TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Line(number: String, text: String) {
    Row {
        Text(number, style = MaterialTheme.typography.titleMedium, color = P.ink, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = P.ink)
    }
}

private fun readSheets(context: Context, uri: Uri): List<RSheet> {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }.orEmpty().lowercase()
    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
    if (name.endsWith(".xls") && !isZip) throw IllegalArgumentException("Old .xls files aren't supported. In Excel, use Save As → .xlsx, then import that.")
    return if (isZip) XlsxReader.read(bytes.inputStream())
    else listOf(RSheet(name.ifEmpty { "CSV" }, CsvReader.read(String(bytes, Charsets.UTF_8))))
}

private fun shareExport(context: Context, zone: ZoneId) {
    try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "Ydar Track ${LocalDate.now()}.xlsx")
        file.writeBytes(ExcelExport.workbook(Repo.current, zone))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = XLSX
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ydar Track data")
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share Excel file"))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't share: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
