package it.sansoale.uscite.ui.globalmap

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.sansoale.uscite.data.repo.TripRepository
import it.sansoale.uscite.data.settings.AppSettings
import it.sansoale.uscite.model.TripType
import it.sansoale.uscite.track.BoundingBox
import it.sansoale.uscite.track.PolylineCodec
import it.sansoale.uscite.track.TrackPoint
import it.sansoale.uscite.ui.common.EmptyState
import it.sansoale.uscite.ui.common.UnitFormatter
import it.sansoale.uscite.ui.common.color
import it.sansoale.uscite.ui.common.icon
import it.sansoale.uscite.ui.common.toHex
import it.sansoale.uscite.ui.map.MapLine
import it.sansoale.uscite.ui.map.TripMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GlobalTrack(
    val trackId: Long,
    val tripId: Long,
    val type: TripType,
    val title: String,
    val startDate: Long,
    val line: MapLine,
    val bounds: BoundingBox?,
)

@HiltViewModel
class GlobalMapViewModel @Inject constructor(repository: TripRepository) : ViewModel() {
    val tracks: StateFlow<List<GlobalTrack>?> = repository.mapTracks
        .map { rows ->
            rows.map { row ->
                val points = PolylineCodec.decode(row.simplified)
                GlobalTrack(
                    trackId = row.trackId,
                    tripId = row.tripId,
                    type = row.type,
                    title = row.title,
                    startDate = row.startDate,
                    line = MapLine(row.trackId, points, row.type.color.toHex()),
                    bounds = BoundingBox.of(points.map { TrackPoint(it.lat, it.lon) }),
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Mappa con tutte le tracce dell'archivio, colorate per tipo. */
@Composable
fun GlobalMapScreen(
    settings: AppSettings,
    onTripClick: (Long) -> Unit,
    viewModel: GlobalMapViewModel = hiltViewModel(),
) {
    val all by viewModel.tracks.collectAsStateWithLifecycle()
    var hiddenTypes by rememberSaveable { mutableStateOf(emptySet<TripType>()) }
    var selectedTrackId by rememberSaveable { mutableStateOf<Long?>(null) }

    val tracks = all ?: return
    if (tracks.isEmpty()) {
        EmptyState(
            title = "Nessuna traccia",
            message = "Le tracce GPX delle tue uscite compariranno qui, colorate per tipo.",
            icon = Icons.Filled.Map,
            modifier = Modifier.statusBarsPadding(),
        )
        return
    }
    val visible = remember(tracks, hiddenTypes) { tracks.filter { it.type !in hiddenTypes } }
    val bounds = remember(tracks) { tracks.mapNotNull { it.bounds }.reduceOrNull { a, b -> a.union(b) } }
    val selected = tracks.find { it.trackId == selectedTrackId }

    Box(Modifier.fillMaxSize()) {
        TripMap(
            lines = visible.map { it.line },
            fitBounds = bounds,
            mapStyle = settings.mapStyle,
            themeMode = settings.themeMode,
            onLineClick = { selectedTrackId = it },
            onMapClick = { selectedTrackId = null },
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TripType.entries.forEach { type ->
                val count = tracks.count { it.type == type }
                FilterChip(
                    selected = type !in hiddenTypes,
                    onClick = { hiddenTypes = if (type in hiddenTypes) hiddenTypes - type else hiddenTypes + type },
                    label = { Text("${type.label} ($count)") },
                    leadingIcon = { Icon(type.icon, null, Modifier.size(18.dp), tint = type.color) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        selectedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
        if (selected != null) {
            Card(
                onClick = { onTripClick(selected.tripId) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(selected.type.icon, null, tint = selected.type.color)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selected.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(UnitFormatter.date(selected.startDate), style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Apri")
                }
            }
        }
    }
}
