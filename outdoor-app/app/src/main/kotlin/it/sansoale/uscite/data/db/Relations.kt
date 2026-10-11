package it.sansoale.uscite.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import androidx.room.TypeConverter
import it.sansoale.uscite.model.StatField
import it.sansoale.uscite.model.TripType

class Converters {
    @TypeConverter
    fun statFieldsToString(value: Set<StatField>): String = value.joinToString(",") { it.name }

    @TypeConverter
    fun stringToStatFields(value: String): Set<StatField> =
        value.split(',').filter { it.isNotBlank() }.mapNotNull { name -> StatField.entries.find { it.name == name } }.toSet()
}

/** Uscita con tutti i dati collegati. */
data class TripWithDetails(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "tripId") val mountain: MountainDetailEntity?,
    @Relation(parentColumn = "id", entityColumn = "tripId") val van: VanDetailEntity?,
    @Relation(parentColumn = "id", entityColumn = "tripId") val bike: BikeDetailEntity?,
    @Relation(parentColumn = "id", entityColumn = "tripId") val huts: List<HutEntity>,
    @Relation(parentColumn = "id", entityColumn = "tripId") val stages: List<VanStageEntity>,
    @Relation(parentColumn = "id", entityColumn = "tripId") val costs: List<VanCostEntity>,
    @Relation(parentColumn = "id", entityColumn = "tripId") val tracks: List<TrackEntity>,
    @Relation(parentColumn = "id", entityColumn = "tripId") val photos: List<PhotoEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(TripTagCrossRef::class, parentColumn = "tripId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(TripCompanionCrossRef::class, parentColumn = "tripId", entityColumn = "companionId"),
    )
    val companions: List<CompanionEntity>,
) {
    val sortedHuts get() = huts.sortedBy { it.sortOrder }
    val sortedStages get() = stages.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val sortedTracks get() = tracks.sortedWith(compareBy({ it.sortOrder }, { it.startTime ?: Long.MAX_VALUE }, { it.id }))
    val sortedPhotos get() = photos.sortedWith(compareBy({ it.sortOrder }, { it.takenAt ?: Long.MAX_VALUE }, { it.id }))
}

/** Dati per le card della home e per le statistiche. */
data class TripSummary(
    @Embedded val trip: TripEntity,
    val coverThumb: String?,
    val coverFile: String?,
    val photoCount: Int,
    val roadKm: Double?,
    val summit: String?,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(TripTagCrossRef::class, parentColumn = "tripId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(TripCompanionCrossRef::class, parentColumn = "tripId", entityColumn = "companionId"),
    )
    val companions: List<CompanionEntity>,
)

/** Traccia semplificata per la mappa globale. */
data class MapTrackRow(
    val trackId: Long,
    val tripId: Long,
    val type: TripType,
    val title: String,
    val startDate: Long,
    val simplified: String,
)
