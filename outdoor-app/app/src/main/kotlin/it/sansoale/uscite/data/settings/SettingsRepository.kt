package it.sansoale.uscite.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class UnitSystem(val label: String) {
    METRIC("Metrico (km, m)"),
    IMPERIAL("Imperiale (mi, ft)"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("Come il sistema"),
    LIGHT("Chiaro"),
    DARK("Scuro"),
}

enum class MapStyle(val label: String) {
    TOPO("OpenTopoMap (topografica)"),
    OSM("OpenStreetMap (standard)"),
}

data class AppSettings(
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val mapStyle: MapStyle = MapStyle.TOPO,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "impostazioni")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val UNITS = stringPreferencesKey("unita")
        val THEME = stringPreferencesKey("tema")
        val DYNAMIC = booleanPreferencesKey("colori_dinamici")
        val MAP = stringPreferencesKey("stile_mappa")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            unitSystem = p[Keys.UNITS].toEnum(UnitSystem.METRIC),
            themeMode = p[Keys.THEME].toEnum(ThemeMode.SYSTEM),
            dynamicColor = p[Keys.DYNAMIC] ?: false,
            mapStyle = p[Keys.MAP].toEnum(MapStyle.TOPO),
        )
    }

    suspend fun setUnitSystem(value: UnitSystem) = context.dataStore.edit { it[Keys.UNITS] = value.name }
    suspend fun setThemeMode(value: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = value.name }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC] = value }
    suspend fun setMapStyle(value: MapStyle) = context.dataStore.edit { it[Keys.MAP] = value.name }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
