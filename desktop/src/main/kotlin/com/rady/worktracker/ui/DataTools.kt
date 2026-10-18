package com.rady.worktracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rady.worktracker.TrackerViewModel
import com.rady.worktracker.data.AppData
import com.rady.worktracker.data.CsvReader
import com.rady.worktracker.data.ExcelExport
import com.rady.worktracker.data.ExcelImport
import com.rady.worktracker.data.ImportPlan
import com.rady.worktracker.data.RSheet
import com.rady.worktracker.data.Repo
import com.rady.worktracker.data.XlsxReader
import java.awt.Desktop
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Excel actions, usable from any screen. Call [rememberDataActions] once per screen. */
class DataActions(
    val export: () -> Unit,
    val share: () -> Unit,
    val import: () -> Unit,
    val template: () -> Unit,
)

@Composable
fun rememberDataActions(vm: TrackerViewModel): DataActions {
    val zone = remember { ZoneId.systemDefault() }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pair<List<RSheet>, ImportPlan>?>(null) }

    message?.let { m ->
        AlertDialog(onDismissRequest = { message = null }, containerColor = P.slab, text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
    }
    pending?.let { (sheets, plan) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = P.slab,
            title = { Text(if (plan.hasChanges) "Import this file?" else "Nothing new to import") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("From sheet \"${plan.sheetName}\"", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                    Text("${plan.sessionsAdded} new session${if (plan.sessionsAdded == 1) "" else "s"} will be added", color = P.ink)
                    if (plan.duplicatesSkipped > 0) Text("${plan.duplicatesSkipped} already in the app — skipped", color = P.ink)
                    if (plan.projectsCreated.isNotEmpty()) Text("New projects: " + plan.projectsCreated.take(6).joinToString(", "), color = P.ink)
                    plan.issues.take(8).forEach {
                        Text(if (it.row > 0) "Row ${it.row}: ${it.message}" else it.message, style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
                    }
                }
            },
            confirmButton = {
                if (plan.hasChanges) TextButton(onClick = {
                    val fresh = ExcelImport.plan(sheets, Repo.current, zone)
                    vm.replaceAll(fresh.result)
                    pending = null
                    message = "Imported ${fresh.sessionsAdded} session${if (fresh.sessionsAdded == 1) "" else "s"}"
                }) { Text("Import") } else TextButton(onClick = { pending = null }) { Text("OK") }
            },
            dismissButton = { if (plan.hasChanges) TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }

    fun save(bytes: ByteArray, name: String, done: String) {
        val f = chooseFile("Save Excel file", save = true, suggestedName = name) ?: return
        val file = if (f.name.lowercase().endsWith(".xlsx")) f else File(f.parentFile, f.name + ".xlsx")
        message = runCatching { file.writeBytes(bytes); done }.getOrElse { "Couldn't save the file: ${it.message}" }
    }

    return DataActions(
        export = { save(ExcelExport.workbook(Repo.current, zone), "Ydar Track ${LocalDate.now()}.xlsx", "Exported ${Repo.current.sessions.size} sessions to Excel") },
        share = {
            // On Windows: open the export straight in Excel.
            runCatching {
                val f = File(System.getProperty("java.io.tmpdir"), "Ydar Track ${LocalDate.now()}.xlsx")
                f.writeBytes(ExcelExport.workbook(Repo.current, zone))
                Desktop.getDesktop().open(f)
            }.onFailure { message = "Couldn't open Excel: ${it.message}" }
        },
        import = {
            val f = chooseFile("Import from Excel or CSV", save = false, extensions = listOf("xlsx", "csv"))
            if (f != null) runCatching {
                val bytes = f.readBytes()
                val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
                val sheets = if (isZip) XlsxReader.read(bytes.inputStream())
                else listOf(RSheet(f.name, CsvReader.read(String(bytes, Charsets.UTF_8))))
                pending = sheets to ExcelImport.plan(sheets, Repo.current, zone)
            }.onFailure { message = it.message ?: "Couldn't read that file. Use an .xlsx or .csv file." }
        },
        template = { save(ExcelExport.template(), "Ydar Track import template.xlsx", "Template saved. Fill the Sessions sheet, then import it.") },
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
            DropdownMenuItem(text = { Text("Open in Excel") }, enabled = hasData, onClick = { open = false; actions.share() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Import from Excel or CSV") }, onClick = { open = false; actions.import() })
            DropdownMenuItem(text = { Text("Get a blank import template") }, onClick = { open = false; actions.template() })
        }
    }
}
