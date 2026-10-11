package it.sansoale.uscite.domain

import it.sansoale.uscite.data.db.StatsColumns
import it.sansoale.uscite.data.db.TrackEntity
import it.sansoale.uscite.model.StatField
import it.sansoale.uscite.model.TripType
import it.sansoale.uscite.track.StatsConfig
import it.sansoale.uscite.track.TrackStats

/** Parametri di calcolo delle statistiche adatti al tipo di uscita. */
fun TripType.statsConfig(): StatsConfig = when (this) {
    TripType.MOUNTAIN -> StatsConfig.HIKING
    TripType.BIKE -> StatsConfig.CYCLING
    TripType.VAN -> StatsConfig.DRIVING
}

fun StatsColumns.toTrackStats(start: Long? = null, end: Long? = null) = TrackStats(
    distanceM = distanceM,
    elevationGainM = elevationGainM,
    elevationLossM = elevationLossM,
    minElevationM = minElevationM,
    maxElevationM = maxElevationM,
    totalDurationS = totalDurationS,
    movingDurationS = movingDurationS,
    avgSpeedMps = avgSpeedMps,
    maxSpeedMps = maxSpeedMps,
    startTime = start,
    endTime = end,
)

/** Statistiche automatiche di un'uscita = combinazione delle statistiche delle sue tracce. */
fun autoStats(tracks: List<TrackEntity>): StatsColumns =
    StatsColumns.from(TrackStats.combine(tracks.map { it.stats.toTrackStats(it.startTime, it.endTime) }))

/**
 * Unisce statistiche automatiche e valori inseriti a mano: i campi in [overridden] mantengono il valore
 * manuale. Se la distanza o i tempi sono manuali e la velocità media no, quest'ultima viene ricalcolata.
 */
fun mergeStats(auto: StatsColumns, manual: StatsColumns, overridden: Set<StatField>): StatsColumns {
    var result = auto
    for (field in overridden) result = result.with(field, manual.get(field))
    if (StatField.AVG_SPEED !in overridden &&
        (StatField.DISTANCE in overridden || StatField.MOVING_DURATION in overridden || StatField.TOTAL_DURATION in overridden)
    ) {
        result = result.copy(avgSpeedMps = TrackStats.averageSpeed(result.distanceM, result.movingDurationS, result.totalDurationS))
    }
    return result
}
