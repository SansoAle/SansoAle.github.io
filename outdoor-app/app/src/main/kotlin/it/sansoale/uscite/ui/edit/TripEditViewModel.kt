package it.sansoale.uscite.ui.edit

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import it.sansoale.uscite.data.db.BikeDetailEntity
import it.sansoale.uscite.data.db.CostDraft
import it.sansoale.uscite.data.db.HutEntity
import it.sansoale.uscite.data.db.MountainDetailEntity
import it.sansoale.uscite.data.db.PhotoEntity
import it.sansoale.uscite.data.db.StatsColumns
import it.sansoale.uscite.data.db.TrackEntity
import it.sansoale.uscite.data.db.TripEntity
import it.sansoale.uscite.data.db.TripGraph
import it.sansoale.uscite.data.db.VanCostEntity
import it.sansoale.uscite.data.db.VanDetailEntity
import it.sansoale.uscite.data.db.VanStageEntity
import it.sansoale.uscite.data.files.PhotoImporter
import it.sansoale.uscite.data.files.TrackImporter
import it.sansoale.uscite.data.repo.TripRepository
import it.sansoale.uscite.data.settings.SettingsRepository
import it.sansoale.uscite.domain.autoStats
import it.sansoale.uscite.domain.mergeStats
import it.sansoale.uscite.model.BikeType
import it.sansoale.uscite.model.CostCategory
import it.sansoale.uscite.model.MountainActivity
import it.sansoale.uscite.model.MountainDifficulty
import it.sansoale.uscite.model.OvernightType
import it.sansoale.uscite.model.StatField
import it.sansoale.uscite.model.Surface
import it.sansoale.uscite.model.TripSource
import it.sansoale.uscite.model.TripType
import it.sansoale.uscite.model.Weather
import it.sansoale.uscite.track.BoundingBox
import it.sansoale.uscite.track.TrackPoint
import it.sansoale.uscite.ui.common.UnitFormatter
import it.sansoale.uscite.ui.navigation.EditRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HutForm(val key: Long, val name: String = "", val elevation: String = "")

data class StageForm(
    val key: Long,
    val name: String = "",
    val date: LocalDate? = null,
    val overnight: OvernightType? = null,
    val notes: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
)

data class CostForm(
    val key: Long,
    val category: CostCategory = CostCategory.FUEL,
    val amount: String = "",
    val note: String = "",
    val stageKey: Long? = null,
    val date: Long? = null,
)

data class EditState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val isNew: Boolean = true,
    val type: TripType = TripType.MOUNTAIN,
    val title: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now(),
    val location: String = "",
    val region: String = "",
    val notes: String = "",
    val weather: Weather? = null,
    val temperature: String = "",
    val rating: Int = 0,
    val tags: List<String> = emptyList(),
    val companions: List<String> = emptyList(),
    // Montagna
    val difficulty: MountainDifficulty? = null,
    val activity: MountainActivity? = null,
    val summit: String = "",
    val summitElevation: String = "",
    val huts: List<HutForm> = emptyList(),
    // Van
    val roadKm: String = "",
    val stages: List<StageForm> = emptyList(),
    val costs: List<CostForm> = emptyList(),
    // Bici
    val bikeType: BikeType? = null,
    val surface: Surface? = null,
    // Tracce, foto, statistiche
    val tracks: List<TrackEntity> = emptyList(),
    val photos: List<PhotoEntity> = emptyList(),
    val coverIndex: Int? = null,
    val autoStats: StatsColumns = StatsColumns(),
    val overridden: Set<StatField> = emptySet(),
    val statInputs: Map<StatField, String> = emptyMap(),
    val dirty: Boolean = false,
    val message: String? = null,
    val savedId: Long? = null,
)

@HiltViewModel
class TripEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TripRepository,
    private val trackImporter: TrackImporter,
    private val photoImporter: PhotoImporter,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EditRoute>()

    var state by mutableStateOf(EditState(isNew = route.tripId == 0L))
        private set

    val tagSuggestions: StateFlow<List<String>> = repository.tagNames.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val companionSuggestions: StateFlow<List<String>> = repository.companionNames.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val regionSuggestions: StateFlow<List<String>> = repository.regions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val units: StateFlow<UnitFormatter> = settingsRepository.settings
        .map { UnitFormatter(it.unitSystem) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitFormatter())

    private var original: TripEntity? = null
    private val importedTracks = mutableListOf<TrackEntity>()
    private val importedPhotos = mutableListOf<PhotoEntity>()
    private val removedTracks = mutableListOf<TrackEntity>()
    private val removedPhotos = mutableListOf<PhotoEntity>()
    private var saved = false
    private var nextKey = 1L
    private fun key() = nextKey++

    init {
        viewModelScope.launch {
            if (route.tripId != 0L) load(route.tripId)
            state = state.copy(loading = false)
            route.importUri?.let { importTrack(Uri.parse(it)) }
        }
    }

    private suspend fun load(id: Long) {
        val d = repository.getTrip(id) ?: return
        original = d.trip
        val fmt = units.value
        val stageKeys = d.sortedStages.associate { it.id to key() }
        state = state.copy(
            isNew = false,
            type = d.trip.type,
            title = d.trip.title,
            startDate = LocalDate.ofEpochDay(d.trip.startDate),
            endDate = LocalDate.ofEpochDay(d.trip.endDate),
            location = d.trip.location,
            region = d.trip.region,
            notes = d.trip.notes,
            weather = d.trip.weather,
            temperature = d.trip.temperatureC?.let { UnitFormatter.plain(it, 1) }.orEmpty(),
            rating = d.trip.rating,
            tags = d.tags.map { it.name },
            companions = d.companions.map { it.name },
            difficulty = d.mountain?.difficulty,
            activity = d.mountain?.activity,
            summit = d.mountain?.summit.orEmpty(),
            summitElevation = d.mountain?.summitElevationM?.let { fmt.toInput(StatField.MAX_ELEVATION, it) }.orEmpty(),
            huts = d.sortedHuts.map { HutForm(key(), it.name, it.elevationM?.let { e -> fmt.toInput(StatField.MAX_ELEVATION, e) }.orEmpty()) },
            roadKm = d.van?.roadKm?.let { fmt.toInput(StatField.DISTANCE, it * 1000) }.orEmpty(),
            stages = d.sortedStages.map {
                StageForm(stageKeys.getValue(it.id), it.name, it.date?.let(LocalDate::ofEpochDay), it.overnightType, it.notes, it.lat, it.lon)
            },
            costs = d.costs.map {
                CostForm(key(), it.category, UnitFormatter.plain(it.amountCents / 100.0, 2), it.note, it.stageId?.let(stageKeys::get), it.date)
            },
            bikeType = d.bike?.bikeType,
            surface = d.bike?.surface,
            tracks = d.sortedTracks,
            photos = d.sortedPhotos,
            coverIndex = d.sortedPhotos.indexOfFirst { it.id == d.trip.coverPhotoId }.takeIf { it >= 0 }
                ?: if (d.photos.isNotEmpty()) 0 else null,
            autoStats = autoStats(d.sortedTracks),
            overridden = d.trip.overriddenStats,
            statInputs = d.trip.overriddenStats.associateWith { fmt.toInput(it, d.trip.stats.get(it)) },
        )
    }

    // ---- Campi semplici ----

    fun update(transform: EditState.() -> EditState) {
        state = state.transform().copy(dirty = true)
    }

    fun setType(type: TripType) {
        if (type == state.type) return
        update { copy(type = type) }
        // Le soglie di calcolo (es. tempo in movimento) dipendono dal tipo: si ricalcolano le tracce.
        if (state.tracks.isNotEmpty()) viewModelScope.launch {
            val recomputed = state.tracks.map { runCatching { trackImporter.recompute(it, type) }.getOrDefault(it) }
            state = state.copy(tracks = recomputed, autoStats = autoStats(recomputed))
        }
    }

    fun setStartDate(date: LocalDate) = update {
        copy(startDate = date, endDate = if (endDate.isBefore(date)) date else endDate)
    }

    fun setEndDate(date: LocalDate) = update {
        copy(endDate = if (date.isBefore(startDate)) startDate else date)
    }

    // ---- Liste (rifugi, tappe, costi) ----

    fun addHut() = update { copy(huts = huts + HutForm(key())) }
    fun updateHut(hut: HutForm) = update { copy(huts = huts.map { if (it.key == hut.key) hut else it }) }
    fun removeHut(hut: HutForm) = update { copy(huts = huts.filter { it.key != hut.key }) }

    fun addStage() = update {
        val date = stages.lastOrNull()?.date?.plusDays(1) ?: startDate
        copy(stages = stages + StageForm(key(), date = date))
    }
    fun updateStage(stage: StageForm) = update { copy(stages = stages.map { if (it.key == stage.key) stage else it }) }
    fun removeStage(stage: StageForm) = update {
        copy(
            stages = stages.filter { it.key != stage.key },
            costs = costs.map { if (it.stageKey == stage.key) it.copy(stageKey = null) else it },
        )
    }

    fun addCost() = update { copy(costs = costs + CostForm(key())) }
    fun updateCost(cost: CostForm) = update { copy(costs = costs.map { if (it.key == cost.key) cost else it }) }
    fun removeCost(cost: CostForm) = update { copy(costs = costs.filter { it.key != cost.key }) }

    // ---- Statistiche ----

    fun displayedStat(field: StatField): String =
        if (field in state.overridden) state.statInputs[field].orEmpty()
        else units.value.toInput(field, state.autoStats.get(field))

    fun setStat(field: StatField, text: String) = update {
        copy(overridden = overridden + field, statInputs = statInputs + (field to text))
    }

    fun resetStat(field: StatField) = update {
        copy(overridden = overridden - field, statInputs = statInputs - field)
    }

    fun unitFor(field: StatField) = units.value.unitFor(field)
    fun isStatValid(field: StatField): Boolean {
        val text = state.statInputs[field].orEmpty()
        return field !in state.overridden || text.isBlank() || units.value.parseInput(field, text) != null
    }

    // ---- Tracce ----

    fun importTrack(uri: Uri) {
        viewModelScope.launch {
            state = state.copy(busy = true)
            runCatching { trackImporter.import(uri, state.type) }
                .onSuccess { imported ->
                    importedTracks += imported.track
                    val first = state.tracks.isEmpty()
                    var s = state.copy(tracks = state.tracks + imported.track, busy = false, dirty = true)
                    if (s.title.isBlank()) s = s.copy(title = imported.suggestedTitle.orEmpty())
                    if (first && s.isNew) {
                        val zone = ZoneId.systemDefault()
                        imported.track.startTime?.let { s = s.copy(startDate = Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }
                        imported.track.endTime?.let {
                            val end = Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                            s = s.copy(endDate = if (end.isBefore(s.startDate)) s.startDate else end)
                        }
                    }
                    state = s.copy(autoStats = autoStats(s.tracks), message = "Traccia importata: ${imported.track.name}")
                    relocatePhotos()
                }
                .onFailure { e ->
                    state = state.copy(busy = false, message = "Impossibile importare il GPX: ${e.message ?: e.javaClass.simpleName}")
                }
        }
    }

    fun removeTrack(track: TrackEntity) {
        if (track.id == 0L) {
            trackImporter.deleteFiles(track)
            importedTracks.remove(track)
        } else {
            removedTracks += track
        }
        update { copy(tracks = tracks - track) }
        state = state.copy(autoStats = autoStats(state.tracks))
        viewModelScope.launch { relocatePhotos() }
    }

    // ---- Foto ----

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            state = state.copy(busy = true)
            val added = mutableListOf<PhotoEntity>()
            var failed = 0
            for (uri in uris) {
                runCatching { photoImporter.import(uri) }.onSuccess { added += it }.onFailure { failed++ }
            }
            importedPhotos += added
            val photos = (state.photos + added).sortedWith(compareBy({ it.takenAt ?: Long.MAX_VALUE }, { it.id }))
            val coverPhoto = state.coverIndex?.let { state.photos.getOrNull(it) }
            state = state.copy(
                photos = photos,
                coverIndex = coverPhoto?.let { photos.indexOf(it) }?.takeIf { it >= 0 } ?: if (photos.isNotEmpty()) 0 else null,
                busy = false,
                dirty = true,
                message = buildString {
                    append(if (added.size == 1) "1 foto aggiunta" else "${added.size} foto aggiunte")
                    if (failed > 0) append(" ($failed non leggibili)")
                },
            )
            relocatePhotos()
        }
    }

    fun removePhoto(photo: PhotoEntity) {
        val cover = state.coverIndex?.let { state.photos.getOrNull(it) }
        if (photo.id == 0L) {
            photoImporter.deleteFiles(photo)
            importedPhotos.remove(photo)
        } else {
            removedPhotos += photo
        }
        val photos = state.photos - photo
        update {
            copy(
                photos = photos,
                coverIndex = cover?.takeIf { it != photo }?.let { photos.indexOf(it) }?.takeIf { it >= 0 }
                    ?: if (photos.isNotEmpty()) 0 else null,
            )
        }
    }

    fun setCover(photo: PhotoEntity) = update { copy(coverIndex = photos.indexOf(photo).takeIf { it >= 0 }) }

    fun setCaption(photo: PhotoEntity, caption: String) = update {
        val updated = photo.copy(caption = caption)
        if (photo.id == 0L) {
            val i = importedPhotos.indexOf(photo)
            if (i >= 0) importedPhotos[i] = updated
        }
        copy(photos = photos.map { if (it === photo || it == photo) updated else it })
    }

    /** Ricalcola la posizione delle foto senza GPS in base agli orari delle tracce. */
    private suspend fun relocatePhotos() {
        if (state.photos.isEmpty()) return
        val timed = state.tracks
            .flatMap { runCatching { trackImporter.loadSegments(it) }.getOrDefault(emptyList()).flatten() }
            .filter { it.time != null }
            .sortedBy { it.time }
        val located = photoImporter.locateByTime(state.photos, timed)
        val cover = state.coverIndex
        state = state.copy(photos = located, coverIndex = cover)
        // Le foto appena importate vanno aggiornate anche nell'elenco dei file da ripulire.
        importedPhotos.replaceAll { p -> located.find { it.fileName == p.fileName } ?: p }
    }

    // ---- Salvataggio ----

    fun dismissMessage() {
        state = state.copy(message = null)
    }

    fun save() {
        val s = state
        if (s.busy) return
        val fmt = units.value
        val invalid = s.overridden.filterNot { isStatValid(it) }
        if (invalid.isNotEmpty()) {
            state = s.copy(message = "Valore non valido: ${invalid.joinToString { it.label }}")
            return
        }
        state = s.copy(busy = true)
        viewModelScope.launch {
            runCatching {
                var manual = StatsColumns()
                for (f in s.overridden) manual = manual.with(f, fmt.parseInput(f, s.statInputs[f].orEmpty()))
                val stats = mergeStats(s.autoStats, manual, s.overridden)
                val box = s.tracks.map { BoundingBox(it.minLat, it.maxLat, it.minLon, it.maxLon) }.reduceOrNull { a, b -> a.union(b) }
                    ?: BoundingBox.of(s.photos.mapNotNull { p -> if (p.lat != null && p.lon != null) TrackPoint(p.lat, p.lon) else null })
                val now = System.currentTimeMillis()
                val trip = TripEntity(
                    id = original?.id ?: 0,
                    type = s.type,
                    title = s.title.trim().ifBlank { "${s.type.label} del ${UnitFormatter.date(s.startDate.toEpochDay())}" },
                    startDate = s.startDate.toEpochDay(),
                    endDate = maxOf(s.endDate, s.startDate).toEpochDay(),
                    location = s.location.trim(),
                    region = s.region.trim(),
                    notes = s.notes.trim(),
                    weather = s.weather,
                    temperatureC = UnitFormatter.parseNumber(s.temperature),
                    rating = s.rating,
                    source = original?.source ?: if (s.tracks.isNotEmpty()) TripSource.IMPORTED else TripSource.MANUAL,
                    stats = stats,
                    overriddenStats = s.overridden,
                    minLat = box?.minLat,
                    maxLat = box?.maxLat,
                    minLon = box?.minLon,
                    maxLon = box?.maxLon,
                    createdAt = original?.createdAt ?: now,
                    updatedAt = now,
                )
                fun elevation(text: String) = fmt.parseInput(StatField.MAX_ELEVATION, text)
                val stageIndex = s.stages.mapIndexed { i, st -> st.key to i }.toMap()
                val graph = TripGraph(
                    trip = trip,
                    mountain = if (s.type == TripType.MOUNTAIN) {
                        MountainDetailEntity(0, s.difficulty, s.activity, s.summit.trim(), elevation(s.summitElevation))
                    } else null,
                    van = if (s.type == TripType.VAN) {
                        VanDetailEntity(0, fmt.parseInput(StatField.DISTANCE, s.roadKm)?.div(1000))
                    } else null,
                    bike = if (s.type == TripType.BIKE) BikeDetailEntity(0, s.bikeType, s.surface) else null,
                    huts = if (s.type == TripType.MOUNTAIN) {
                        s.huts.filter { it.name.isNotBlank() }.map { HutEntity(tripId = 0, name = it.name.trim(), elevationM = elevation(it.elevation)) }
                    } else emptyList(),
                    stages = if (s.type == TripType.VAN) {
                        s.stages.map {
                            VanStageEntity(
                                tripId = 0,
                                date = it.date?.toEpochDay(),
                                name = it.name.trim().ifBlank { "Tappa" },
                                overnightType = it.overnight,
                                lat = it.lat,
                                lon = it.lon,
                                notes = it.notes.trim(),
                            )
                        }
                    } else emptyList(),
                    costs = if (s.type == TripType.VAN) {
                        s.costs.mapNotNull { c ->
                            val cents = UnitFormatter.parseEuroCents(c.amount) ?: return@mapNotNull null
                            CostDraft(
                                VanCostEntity(tripId = 0, category = c.category, amountCents = cents, date = c.date, note = c.note.trim()),
                                c.stageKey?.let(stageIndex::get),
                            )
                        }
                    } else emptyList(),
                    tracks = s.tracks,
                    photos = s.photos,
                    coverPhotoIndex = s.coverIndex,
                    tags = s.tags,
                    companions = s.companions,
                )
                repository.save(graph, removedTracks.toList(), removedPhotos.toList())
            }.onSuccess { id ->
                saved = true
                state = state.copy(busy = false, savedId = id, dirty = false)
            }.onFailure { e ->
                state = state.copy(busy = false, message = "Salvataggio non riuscito: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        // Uscita annullata: si eliminano i file copiati in questa sessione e mai salvati.
        if (!saved) {
            importedTracks.forEach { trackImporter.deleteFiles(it) }
            importedPhotos.forEach { photoImporter.deleteFiles(it) }
        }
    }
}
