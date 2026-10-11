package it.sansoale.uscite.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.sansoale.uscite.data.backup.BackupManager
import it.sansoale.uscite.data.settings.AppSettings
import it.sansoale.uscite.data.settings.MapStyle
import it.sansoale.uscite.data.settings.SettingsRepository
import it.sansoale.uscite.data.settings.ThemeMode
import it.sansoale.uscite.data.settings.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(val busy: Boolean = false, val message: String? = null)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val backupManager: BackupManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _backup = MutableStateFlow(BackupUiState())
    val backup: StateFlow<BackupUiState> = _backup.asStateFlow()

    fun setUnitSystem(value: UnitSystem) = viewModelScope.launch { repository.setUnitSystem(value) }
    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { repository.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { repository.setDynamicColor(value) }
    fun setMapStyle(value: MapStyle) = viewModelScope.launch { repository.setMapStyle(value) }

    fun exportBackup(uri: Uri) = runBackup {
        val r = backupManager.export(uri)
        "Backup completato: ${r.trips} uscite, ${r.tracks} tracce, ${r.photos} foto."
    }

    fun importBackup(uri: Uri) = runBackup {
        val r = backupManager.import(uri)
        buildString {
            append("Ripristino completato: ${r.trips} uscite, ${r.tracks} tracce, ${r.photos} foto.")
            if (r.missingFiles > 0) append(" Attenzione: ${r.missingFiles} file mancanti nel backup.")
        }
    }

    fun dismissMessage() {
        _backup.value = _backup.value.copy(message = null)
    }

    private fun runBackup(block: suspend () -> String) {
        if (_backup.value.busy) return
        _backup.value = BackupUiState(busy = true)
        viewModelScope.launch {
            val message = runCatching { block() }.getOrElse { "Operazione non riuscita: ${it.message ?: it.javaClass.simpleName}" }
            _backup.value = BackupUiState(busy = false, message = message)
        }
    }
}
