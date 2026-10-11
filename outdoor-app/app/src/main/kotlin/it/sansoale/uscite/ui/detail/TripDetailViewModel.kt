package it.sansoale.uscite.ui.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.sansoale.uscite.data.db.TrackEntity
import it.sansoale.uscite.data.db.TripWithDetails
import it.sansoale.uscite.data.files.TrackImporter
import it.sansoale.uscite.data.repo.TripRepository
import it.sansoale.uscite.track.BoundingBox
import it.sansoale.uscite.track.ElevationProfile
import it.sansoale.uscite.track.LatLon
import it.sansoale.uscite.track.ProfilePoint
import it.sansoale.uscite.track.TrackPoint
import it.sansoale.uscite.track.TrackSimplifier
import it.sansoale.uscite.ui.common.color
import it.sansoale.uscite.ui.common.toHex
import it.sansoale.uscite.ui.map.MapLine
import it.sansoale.uscite.ui.navigation.DetailRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailState {
    data object Loading : DetailState
    data object NotFound : DetailState
    data class Loaded(val data: TripWithDetails) : DetailState
}

data class TrackDisplay(
    val lines: List<MapLine> = emptyList(),
    val profile: List<ProfilePoint> = emptyList(),
    val bounds: BoundingBox? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TripRepository,
    private val trackImporter: TrackImporter,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val tripId: Long = savedStateHandle.toRoute<DetailRoute>().tripId

    val state: StateFlow<DetailState> = repository.observeTrip(tripId)
        .map { if (it == null) DetailState.NotFound else DetailState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    /** Punti delle tracce (caricati dai file) pronti per mappa e profilo. */
    val tracks: StateFlow<TrackDisplay> = state
        .map { (it as? DetailState.Loaded)?.data }
        .filterNotNull()
        .map { it.sortedTracks to it.trip.type.color.toHex() }
        .distinctUntilChanged()
        .mapLatest { (tracks, color) -> loadTracks(tracks, color) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackDisplay())

    private val _selected = MutableStateFlow<Int?>(null)
    val selectedProfileIndex: StateFlow<Int?> = _selected.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun selectProfileIndex(index: Int?) {
        _selected.value = index
    }

    fun dismissMessage() {
        _message.value = null
    }

    private suspend fun loadTracks(tracks: List<TrackEntity>, colorHex: String): TrackDisplay {
        if (tracks.isEmpty()) return TrackDisplay()
        val allSegments = mutableListOf<List<TrackPoint>>()
        val lines = mutableListOf<MapLine>()
        var bounds: BoundingBox? = null
        for (track in tracks) {
            val segments = runCatching { trackImporter.loadSegments(track) }.getOrDefault(emptyList())
            allSegments += segments
            for (segment in segments) {
                val simplified = TrackSimplifier.simplifyToMax(segment, 3000, initialToleranceM = 2.0)
                lines += MapLine(track.id, simplified.map { LatLon(it.lat, it.lon) }, colorHex)
            }
            val box = BoundingBox(track.minLat, track.maxLat, track.minLon, track.maxLon)
            bounds = bounds?.union(box) ?: box
        }
        return TrackDisplay(lines, ElevationProfile.build(allSegments), bounds)
    }

    fun exportGpx(uri: Uri) {
        val data = (state.value as? DetailState.Loaded)?.data ?: return
        viewModelScope.launch {
            _message.value = runCatching {
                val out = context.contentResolver.openOutputStream(uri, "w") ?: error("Impossibile scrivere il file")
                out.use {
                    trackImporter.export(
                        tracks = data.sortedTracks,
                        title = data.trip.title,
                        description = data.trip.notes.ifBlank { null },
                        waypoints = emptyList(),
                        out = it,
                    )
                }
                "Traccia GPX esportata"
            }.getOrElse { "Esportazione non riuscita: ${it.message}" }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(tripId)
            onDeleted()
        }
    }
}
