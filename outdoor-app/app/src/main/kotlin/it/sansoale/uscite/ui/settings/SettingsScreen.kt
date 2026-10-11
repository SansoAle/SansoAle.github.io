package it.sansoale.uscite.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.sansoale.uscite.BuildConfig
import it.sansoale.uscite.data.backup.BackupManager
import it.sansoale.uscite.data.settings.MapStyle
import it.sansoale.uscite.data.settings.ThemeMode
import it.sansoale.uscite.data.settings.UnitSystem
import it.sansoale.uscite.ui.common.SectionTitle
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    var confirmRestore by rememberSaveable { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri.toString()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Impostazioni") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionTitle("Unità di misura")
            UnitSystem.entries.forEach { option ->
                RadioRow(option.label, settings.unitSystem == option) { viewModel.setUnitSystem(option) }
            }

            SectionTitle("Tema")
            ThemeMode.entries.forEach { option ->
                RadioRow(option.label, settings.themeMode == option) { viewModel.setThemeMode(option) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Colori dinamici")
                    Text(
                        "Usa i colori dello sfondo (Android 12+)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) })
            }

            SectionTitle("Mappa")
            MapStyle.entries.forEach { option ->
                RadioRow(option.label, settings.mapStyle == option) { viewModel.setMapStyle(option) }
            }
            Text(
                "Le zone già visualizzate restano salvate e si vedono anche senza connessione.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Backup")
            Text(
                "Esporta tutto l'archivio (dati, tracce GPX e foto) in un file ZIP da salvare dove vuoi, " +
                    "ad esempio su Google Drive o su una chiavetta. Il ripristino sostituisce l'archivio attuale.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !backup.busy,
                    onClick = {
                        val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                        exportLauncher.launch("diario-uscite-$date.zip")
                    },
                ) {
                    Icon(Icons.Filled.Backup, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Esporta")
                }
                OutlinedButton(
                    enabled = !backup.busy,
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                ) {
                    Icon(Icons.Filled.Restore, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Ripristina")
                }
            }
            if (backup.busy) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text("Operazione in corso…")
                }
            }

            SectionTitle("Informazioni")
            Text("Diario Uscite ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Tutti i dati restano sul telefono. Mappe © OpenStreetMap contributors, OpenTopoMap (CC-BY-SA).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(24.dp))
        }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Ripristinare il backup?") },
            text = { Text("Tutte le uscite attuali verranno sostituite da quelle contenute nel backup. L'operazione non è reversibile.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importBackup(android.net.Uri.parse(uri))
                    confirmRestore = null
                }) { Text("Ripristina") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Annulla") } },
        )
    }

    backup.message?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMessage() },
            title = { Text("Backup") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.dismissMessage() }) { Text("OK") } },
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.size(8.dp))
        Text(label)
    }
}
