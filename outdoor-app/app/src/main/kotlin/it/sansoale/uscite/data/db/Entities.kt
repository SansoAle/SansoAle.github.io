package it.sansoale.uscite.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.sansoale.uscite.model.BikeType
import it.sansoale.uscite.model.CostCategory
import it.sansoale.uscite.model.MountainActivity
import it.sansoale.uscite.model.MountainDifficulty
import it.sansoale.uscite.model.OvernightType
import it.sansoale.uscite.model.PhotoPositionSource
import it.sansoale.uscite.model.StatField
import it.sansoale.uscite.model.Surface
import it.sansoale.uscite.model.TripSource
import it.sansoale.uscite.model.TripType
import it.sansoale.uscite.model.Weather
import it.sansoale.uscite.track.TrackStats
import kotlinx.serialization.Serializable

/** Statistiche salvate (unità SI). */
@Serializable
data class StatsColumns(
    val distanceM: Double? = null,
    val elevationGainM: Double? = null,
    val elevationLossM: Double? = null,
    val minElevationM: Double? = null,
    val maxElevationM: Double? = null,
    val totalDurationS: Long? = null,
    val movingDurationS: Long? = null,
    val avgSpeedMps: Double? = null,
    val maxSpeedMps: Double? = null,
) {
    fun get(field: StatField): Double? = when (field) {
        StatField.DISTANCE -> distanceM
        StatField.ELEVATION_GAIN -> elevationGainM
        StatField.ELEVATION_LOSS -> elevationLossM
        StatField.MIN_ELEVATION -> minElevationM
        StatField.MAX_ELEVATION -> maxElevationM
        StatField.TOTAL_DURATION -> totalDurationS?.toDouble()
        StatField.MOVING_DURATION -> movingDurationS?.toDouble()
        StatField.AVG_SPEED -> avgSpeedMps
        StatField.MAX_SPEED -> maxSpeedMps
    }

    fun with(field: StatField, value: Double?): StatsColumns = when (field) {
        StatField.DISTANCE -> copy(distanceM = value)
        StatField.ELEVATION_GAIN -> copy(elevationGainM = value)
        StatField.ELEVATION_LOSS -> copy(elevationLossM = value)
        StatField.MIN_ELEVATION -> copy(minElevationM = value)
        StatField.MAX_ELEVATION -> copy(maxElevationM = value)
        StatField.TOTAL_DURATION -> copy(totalDurationS = value?.toLong())
        StatField.MOVING_DURATION -> copy(movingDurationS = value?.toLong())
        StatField.AVG_SPEED -> copy(avgSpeedMps = value)
        StatField.MAX_SPEED -> copy(maxSpeedMps = value)
    }

    /** Durata di riferimento: tempo in movimento se disponibile, altrimenti durata totale. */
    val mainDurationS: Long? get() = movingDurationS ?: totalDurationS

    companion object {
        fun from(stats: TrackStats) = StatsColumns(
            distanceM = stats.distanceM,
            elevationGainM = stats.elevationGainM,
            elevationLossM = stats.elevationLossM,
            minElevationM = stats.minElevationM,
            maxElevationM = stats.maxElevationM,
            totalDurationS = stats.totalDurationS,
            movingDurationS = stats.movingDurationS,
            avgSpeedMps = stats.avgSpeedMps,
            maxSpeedMps = stats.maxSpeedMps,
        )
    }
}

@Serializable
@Entity(tableName = "trip", indices = [Index("startDate"), Index("type")])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TripType,
    val title: String,
    /** Giorno di inizio (epoch day, vedi [java.time.LocalDate.toEpochDay]). */
    val startDate: Long,
    val endDate: Long,
    val location: String = "",
    val region: String = "",
    val notes: String = "",
    val weather: Weather? = null,
    val temperatureC: Double? = null,
    /** Voto personale 1-5, 0 = nessun voto. */
    val rating: Int = 0,
    val coverPhotoId: Long? = null,
    val source: TripSource = TripSource.MANUAL,
    @Embedded val stats: StatsColumns = StatsColumns(),
    /** Statistiche inserite a mano: un nuovo calcolo dal GPX non le sovrascrive. */
    val overriddenStats: Set<StatField> = emptySet(),
    val minLat: Double? = null,
    val maxLat: Double? = null,
    val minLon: Double? = null,
    val maxLon: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "mountain_detail",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
)
data class MountainDetailEntity(
    @PrimaryKey val tripId: Long,
    val difficulty: MountainDifficulty? = null,
    val activity: MountainActivity? = null,
    /** Vetta o meta raggiunta. */
    val summit: String = "",
    val summitElevationM: Double? = null,
)

@Serializable
@Entity(
    tableName = "hut",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class HutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val elevationM: Double? = null,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(
    tableName = "van_detail",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
)
data class VanDetailEntity(
    @PrimaryKey val tripId: Long,
    /** Km percorsi su strada (dal contachilometri), indipendenti dalla traccia GPX. */
    val roadKm: Double? = null,
)

@Serializable
@Entity(
    tableName = "van_stage",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class VanStageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val sortOrder: Int = 0,
    /** Epoch day della notte, facoltativo. */
    val date: Long? = null,
    val name: String,
    val overnightType: OvernightType? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val notes: String = "",
)

@Serializable
@Entity(
    tableName = "van_cost",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = VanStageEntity::class, parentColumns = ["id"], childColumns = ["stageId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("tripId"), Index("stageId")],
)
data class VanCostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val stageId: Long? = null,
    val category: CostCategory,
    /** Importo in centesimi di euro. */
    val amountCents: Long,
    val date: Long? = null,
    val note: String = "",
)

@Serializable
@Entity(
    tableName = "bike_detail",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
)
data class BikeDetailEntity(
    @PrimaryKey val tripId: Long,
    val bikeType: BikeType? = null,
    val surface: Surface? = null,
)

@Serializable
@Entity(
    tableName = "track",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    /** File GPX originale (in files/tracks), conservato per l'esportazione. */
    val gpxFile: String,
    /** Cache binaria dei punti (in files/tracks). */
    val pointsFile: String,
    /** Polilinea semplificata (Encoded Polyline) per la mappa globale. */
    val simplified: String,
    val pointCount: Int,
    @Embedded val stats: StatsColumns = StatsColumns(),
    val startTime: Long? = null,
    val endTime: Long? = null,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(
    tableName = "photo",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    /** File originale (in files/photos). */
    val fileName: String,
    /** Miniatura (in files/thumbs); `null` se non è stato possibile generarla. */
    val thumbName: String? = null,
    val takenAt: Long? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val positionSource: PhotoPositionSource = PhotoPositionSource.NONE,
    val caption: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(tableName = "tag", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Serializable
@Entity(
    tableName = "trip_tag",
    primaryKeys = ["tripId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class TripTagCrossRef(val tripId: Long, val tagId: Long)

@Serializable
@Entity(tableName = "companion", indices = [Index(value = ["name"], unique = true)])
data class CompanionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Serializable
@Entity(
    tableName = "trip_companion",
    primaryKeys = ["tripId", "companionId"],
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CompanionEntity::class, parentColumns = ["id"], childColumns = ["companionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("companionId")],
)
data class TripCompanionCrossRef(val tripId: Long, val companionId: Long)
