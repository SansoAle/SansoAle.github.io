package it.sansoale.uscite.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Un costo del van con il riferimento (per indice) alla tappa a cui è collegato. */
data class CostDraft(val cost: VanCostEntity, val stageIndex: Int?)

/** Tutto ciò che va salvato per un'uscita, in un'unica transazione. */
data class TripGraph(
    val trip: TripEntity,
    val mountain: MountainDetailEntity?,
    val van: VanDetailEntity?,
    val bike: BikeDetailEntity?,
    val huts: List<HutEntity>,
    val stages: List<VanStageEntity>,
    val costs: List<CostDraft>,
    val tracks: List<TrackEntity>,
    val photos: List<PhotoEntity>,
    /** Indice in [photos] della foto di copertina. */
    val coverPhotoIndex: Int?,
    val tags: List<String>,
    val companions: List<String>,
)

@Dao
abstract class TripDao {

    // ---- Letture ----

    @Transaction
    @Query(
        """
        SELECT trip.*,
          (SELECT p.thumbName FROM photo p WHERE p.tripId = trip.id
             ORDER BY CASE WHEN p.id = trip.coverPhotoId THEN 0 ELSE 1 END, p.sortOrder, p.id LIMIT 1) AS coverThumb,
          (SELECT p.fileName FROM photo p WHERE p.tripId = trip.id
             ORDER BY CASE WHEN p.id = trip.coverPhotoId THEN 0 ELSE 1 END, p.sortOrder, p.id LIMIT 1) AS coverFile,
          (SELECT COUNT(*) FROM photo p WHERE p.tripId = trip.id) AS photoCount,
          (SELECT v.roadKm FROM van_detail v WHERE v.tripId = trip.id) AS roadKm,
          (SELECT m.summit FROM mountain_detail m WHERE m.tripId = trip.id) AS summit
        FROM trip
        ORDER BY trip.startDate DESC, trip.id DESC
        """,
    )
    abstract fun observeSummaries(): Flow<List<TripSummary>>

    @Transaction
    @Query("SELECT * FROM trip WHERE id = :id")
    abstract fun observeTrip(id: Long): Flow<TripWithDetails?>

    @Transaction
    @Query("SELECT * FROM trip WHERE id = :id")
    abstract suspend fun getTrip(id: Long): TripWithDetails?

    @Query(
        """
        SELECT track.id AS trackId, track.tripId AS tripId, trip.type AS type, trip.title AS title,
               trip.startDate AS startDate, track.simplified AS simplified
        FROM track JOIN trip ON trip.id = track.tripId
        ORDER BY trip.startDate
        """,
    )
    abstract fun observeMapTracks(): Flow<List<MapTrackRow>>

    @Query("SELECT name FROM tag ORDER BY name COLLATE NOCASE")
    abstract fun observeTagNames(): Flow<List<String>>

    @Query("SELECT name FROM companion ORDER BY name COLLATE NOCASE")
    abstract fun observeCompanionNames(): Flow<List<String>>

    @Query("SELECT DISTINCT region FROM trip WHERE region != '' ORDER BY region COLLATE NOCASE")
    abstract fun observeRegions(): Flow<List<String>>

    @Query("SELECT * FROM track WHERE tripId = :tripId")
    abstract suspend fun getTracks(tripId: Long): List<TrackEntity>

    @Query("SELECT * FROM photo WHERE tripId = :tripId")
    abstract suspend fun getPhotos(tripId: Long): List<PhotoEntity>

    // ---- Scritture elementari ----

    @Insert
    abstract suspend fun insertTrip(trip: TripEntity): Long

    @Update
    abstract suspend fun updateTrip(trip: TripEntity)

    @Query("UPDATE trip SET coverPhotoId = :photoId WHERE id = :tripId")
    abstract suspend fun setCover(tripId: Long, photoId: Long?)

    @Query("DELETE FROM trip WHERE id = :id")
    abstract suspend fun deleteTrip(id: Long)

    @Query("DELETE FROM mountain_detail WHERE tripId = :tripId")
    abstract suspend fun deleteMountain(tripId: Long)

    @Query("DELETE FROM van_detail WHERE tripId = :tripId")
    abstract suspend fun deleteVan(tripId: Long)

    @Query("DELETE FROM bike_detail WHERE tripId = :tripId")
    abstract suspend fun deleteBike(tripId: Long)

    @Query("DELETE FROM hut WHERE tripId = :tripId")
    abstract suspend fun deleteHuts(tripId: Long)

    @Query("DELETE FROM van_cost WHERE tripId = :tripId")
    abstract suspend fun deleteCosts(tripId: Long)

    @Query("DELETE FROM van_stage WHERE tripId = :tripId")
    abstract suspend fun deleteStages(tripId: Long)

    @Query("DELETE FROM trip_tag WHERE tripId = :tripId")
    abstract suspend fun deleteTripTags(tripId: Long)

    @Query("DELETE FROM trip_companion WHERE tripId = :tripId")
    abstract suspend fun deleteTripCompanions(tripId: Long)

    @Query("DELETE FROM track WHERE tripId = :tripId AND id NOT IN (:keep)")
    abstract suspend fun deleteTracksExcept(tripId: Long, keep: List<Long>)

    @Query("DELETE FROM photo WHERE tripId = :tripId AND id NOT IN (:keep)")
    abstract suspend fun deletePhotosExcept(tripId: Long, keep: List<Long>)

    @Query("DELETE FROM tag WHERE id NOT IN (SELECT tagId FROM trip_tag)")
    abstract suspend fun deleteUnusedTags()

    @Query("DELETE FROM companion WHERE id NOT IN (SELECT companionId FROM trip_companion)")
    abstract suspend fun deleteUnusedCompanions()

    @Upsert
    abstract suspend fun upsertMountain(entity: MountainDetailEntity)

    @Upsert
    abstract suspend fun upsertVan(entity: VanDetailEntity)

    @Upsert
    abstract suspend fun upsertBike(entity: BikeDetailEntity)

    @Insert
    abstract suspend fun insertHuts(list: List<HutEntity>)

    @Insert
    abstract suspend fun insertStage(stage: VanStageEntity): Long

    @Insert
    abstract suspend fun insertCosts(list: List<VanCostEntity>)

    @Insert
    abstract suspend fun insertTrack(track: TrackEntity): Long

    @Update
    abstract suspend fun updateTrack(track: TrackEntity)

    @Insert
    abstract suspend fun insertPhoto(photo: PhotoEntity): Long

    @Update
    abstract suspend fun updatePhoto(photo: PhotoEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTagIgnore(tag: TagEntity): Long

    @Query("SELECT id FROM tag WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun findTagId(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTripTags(list: List<TripTagCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCompanionIgnore(companion: CompanionEntity): Long

    @Query("SELECT id FROM companion WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun findCompanionId(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTripCompanions(list: List<TripCompanionCrossRef>)

    // ---- Salvataggio completo ----

    /** Salva l'uscita con tutti i dati collegati e restituisce il suo id. */
    @Transaction
    open suspend fun saveTripGraph(graph: TripGraph): Long {
        val tripId = if (graph.trip.id == 0L) {
            insertTrip(graph.trip.copy(coverPhotoId = null))
        } else {
            updateTrip(graph.trip.copy(coverPhotoId = null))
            graph.trip.id
        }

        deleteMountain(tripId)
        deleteVan(tripId)
        deleteBike(tripId)
        graph.mountain?.let { upsertMountain(it.copy(tripId = tripId)) }
        graph.van?.let { upsertVan(it.copy(tripId = tripId)) }
        graph.bike?.let { upsertBike(it.copy(tripId = tripId)) }

        deleteHuts(tripId)
        insertHuts(graph.huts.mapIndexed { i, h -> h.copy(id = 0, tripId = tripId, sortOrder = i) })

        deleteCosts(tripId)
        deleteStages(tripId)
        val stageIds = graph.stages.mapIndexed { i, s -> insertStage(s.copy(id = 0, tripId = tripId, sortOrder = i)) }
        insertCosts(
            graph.costs.map { draft ->
                draft.cost.copy(id = 0, tripId = tripId, stageId = draft.stageIndex?.let { stageIds.getOrNull(it) })
            },
        )

        deleteTracksExcept(tripId, graph.tracks.map { it.id }.filter { it != 0L })
        graph.tracks.forEachIndexed { i, t ->
            val track = t.copy(tripId = tripId, sortOrder = i)
            if (t.id == 0L) insertTrack(track) else updateTrack(track)
        }

        deletePhotosExcept(tripId, graph.photos.map { it.id }.filter { it != 0L })
        val photoIds = graph.photos.mapIndexed { i, p ->
            val photo = p.copy(tripId = tripId, sortOrder = i)
            if (p.id == 0L) insertPhoto(photo) else {
                updatePhoto(photo); p.id
            }
        }
        setCover(tripId, graph.coverPhotoIndex?.let { photoIds.getOrNull(it) })

        deleteTripTags(tripId)
        val tagIds = graph.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.map { name ->
            findTagId(name) ?: insertTagIgnore(TagEntity(name = name))
        }
        insertTripTags(tagIds.map { TripTagCrossRef(tripId, it) })

        deleteTripCompanions(tripId)
        val companionIds = graph.companions.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.map { name ->
            findCompanionId(name) ?: insertCompanionIgnore(CompanionEntity(name = name))
        }
        insertTripCompanions(companionIds.map { TripCompanionCrossRef(tripId, it) })

        deleteUnusedTags()
        deleteUnusedCompanions()
        return tripId
    }

    @Transaction
    open suspend fun deleteTripAndCleanup(id: Long) {
        deleteTrip(id)
        deleteUnusedTags()
        deleteUnusedCompanions()
    }
}
