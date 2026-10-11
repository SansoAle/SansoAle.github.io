package it.sansoale.uscite.data.repo

import it.sansoale.uscite.data.db.AppDatabase
import it.sansoale.uscite.data.db.MapTrackRow
import it.sansoale.uscite.data.db.PhotoEntity
import it.sansoale.uscite.data.db.TrackEntity
import it.sansoale.uscite.data.db.TripGraph
import it.sansoale.uscite.data.db.TripSummary
import it.sansoale.uscite.data.db.TripWithDetails
import it.sansoale.uscite.data.files.PhotoImporter
import it.sansoale.uscite.data.files.TrackImporter
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    db: AppDatabase,
    private val trackImporter: TrackImporter,
    private val photoImporter: PhotoImporter,
) {
    private val dao = db.tripDao()

    val summaries: Flow<List<TripSummary>> = dao.observeSummaries()
    val mapTracks: Flow<List<MapTrackRow>> = dao.observeMapTracks()
    val tagNames: Flow<List<String>> = dao.observeTagNames()
    val companionNames: Flow<List<String>> = dao.observeCompanionNames()
    val regions: Flow<List<String>> = dao.observeRegions()

    fun observeTrip(id: Long): Flow<TripWithDetails?> = dao.observeTrip(id)

    suspend fun getTrip(id: Long): TripWithDetails? = dao.getTrip(id)

    /**
     * Salva l'uscita e, solo a salvataggio riuscito, elimina i file di tracce e foto rimosse.
     */
    suspend fun save(graph: TripGraph, removedTracks: List<TrackEntity>, removedPhotos: List<PhotoEntity>): Long {
        val id = dao.saveTripGraph(graph)
        removedTracks.forEach { trackImporter.deleteFiles(it) }
        removedPhotos.forEach { photoImporter.deleteFiles(it) }
        return id
    }

    suspend fun delete(id: Long) {
        val trip = dao.getTrip(id) ?: return
        dao.deleteTripAndCleanup(id)
        trip.tracks.forEach { trackImporter.deleteFiles(it) }
        trip.photos.forEach { photoImporter.deleteFiles(it) }
    }
}
