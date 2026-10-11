package it.sansoale.uscite.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import it.sansoale.uscite.data.backup.BackupData

/** Lettura e scrittura in blocco di tutte le tabelle, per backup e ripristino. */
@Dao
abstract class BackupDao {
    @Query("SELECT * FROM trip") abstract suspend fun trips(): List<TripEntity>
    @Query("SELECT * FROM mountain_detail") abstract suspend fun mountain(): List<MountainDetailEntity>
    @Query("SELECT * FROM hut") abstract suspend fun huts(): List<HutEntity>
    @Query("SELECT * FROM van_detail") abstract suspend fun van(): List<VanDetailEntity>
    @Query("SELECT * FROM van_stage") abstract suspend fun stages(): List<VanStageEntity>
    @Query("SELECT * FROM van_cost") abstract suspend fun costs(): List<VanCostEntity>
    @Query("SELECT * FROM bike_detail") abstract suspend fun bike(): List<BikeDetailEntity>
    @Query("SELECT * FROM track") abstract suspend fun tracks(): List<TrackEntity>
    @Query("SELECT * FROM photo") abstract suspend fun photos(): List<PhotoEntity>
    @Query("SELECT * FROM tag") abstract suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM trip_tag") abstract suspend fun tripTags(): List<TripTagCrossRef>
    @Query("SELECT * FROM companion") abstract suspend fun companions(): List<CompanionEntity>
    @Query("SELECT * FROM trip_companion") abstract suspend fun tripCompanions(): List<TripCompanionCrossRef>

    @Query("DELETE FROM trip") abstract suspend fun clearTrips()
    @Query("DELETE FROM tag") abstract suspend fun clearTags()
    @Query("DELETE FROM companion") abstract suspend fun clearCompanions()

    @Insert abstract suspend fun insertTrips(list: List<TripEntity>)
    @Insert abstract suspend fun insertMountain(list: List<MountainDetailEntity>)
    @Insert abstract suspend fun insertHuts(list: List<HutEntity>)
    @Insert abstract suspend fun insertVan(list: List<VanDetailEntity>)
    @Insert abstract suspend fun insertStages(list: List<VanStageEntity>)
    @Insert abstract suspend fun insertCosts(list: List<VanCostEntity>)
    @Insert abstract suspend fun insertBike(list: List<BikeDetailEntity>)
    @Insert abstract suspend fun insertTracks(list: List<TrackEntity>)
    @Insert abstract suspend fun insertPhotos(list: List<PhotoEntity>)
    @Insert abstract suspend fun insertTags(list: List<TagEntity>)
    @Insert abstract suspend fun insertTripTags(list: List<TripTagCrossRef>)
    @Insert abstract suspend fun insertCompanions(list: List<CompanionEntity>)
    @Insert abstract suspend fun insertTripCompanions(list: List<TripCompanionCrossRef>)

    @Transaction
    open suspend fun exportAll(): BackupData = BackupData(
        trips = trips(),
        mountain = mountain(),
        huts = huts(),
        van = van(),
        stages = stages(),
        costs = costs(),
        bike = bike(),
        tracks = tracks(),
        photos = photos(),
        tags = tags(),
        tripTags = tripTags(),
        companions = companions(),
        tripCompanions = tripCompanions(),
    )

    /** Sostituisce l'intero archivio. In caso di errore la transazione viene annullata. */
    @Transaction
    open suspend fun replaceAll(data: BackupData) {
        clearTrips() // le tabelle collegate si svuotano a cascata
        clearTags()
        clearCompanions()
        insertTrips(data.trips)
        insertTags(data.tags)
        insertCompanions(data.companions)
        insertMountain(data.mountain)
        insertHuts(data.huts)
        insertVan(data.van)
        insertStages(data.stages)
        insertCosts(data.costs)
        insertBike(data.bike)
        insertTracks(data.tracks)
        insertPhotos(data.photos)
        insertTripTags(data.tripTags)
        insertTripCompanions(data.tripCompanions)
    }
}
