package it.sansoale.uscite.domain

import it.sansoale.uscite.data.db.TripEntity
import it.sansoale.uscite.model.TripType
import java.time.LocalDate

/** Dati minimi di un'uscita per le statistiche d'archivio. */
data class StatsTrip(
    val id: Long,
    val title: String,
    val type: TripType,
    val startDate: Long,
    val endDate: Long,
    /** Distanza in metri (per il van: km su strada se indicati, altrimenti la traccia). */
    val distanceM: Double?,
    val gainM: Double?,
    val maxElevationM: Double?,
    val durationS: Long?,
    val maxSpeedMps: Double?,
) {
    val year: Int get() = LocalDate.ofEpochDay(startDate).year
    val month: Int get() = LocalDate.ofEpochDay(startDate).monthValue
    val days: Long get() = (endDate - startDate).coerceAtLeast(0) + 1

    companion object {
        fun from(trip: TripEntity, roadKm: Double?) = StatsTrip(
            id = trip.id,
            title = trip.title,
            type = trip.type,
            startDate = trip.startDate,
            endDate = trip.endDate,
            distanceM = if (trip.type == TripType.VAN) roadKm?.times(1000) ?: trip.stats.distanceM else trip.stats.distanceM,
            gainM = trip.stats.elevationGainM,
            maxElevationM = trip.stats.maxElevationM,
            durationS = trip.stats.mainDurationS,
            maxSpeedMps = trip.stats.maxSpeedMps,
        )
    }
}

data class Totals(
    val count: Int = 0,
    val distanceM: Double = 0.0,
    val gainM: Double = 0.0,
    val durationS: Long = 0,
    val days: Long = 0,
)

/** Un record personale: [value] in unità SI (metri, secondi, m/s, giorni). */
data class PersonalRecord(val label: String, val kind: RecordKind, val value: Double, val trip: StatsTrip)

enum class RecordKind { DISTANCE, ELEVATION, DURATION, SPEED, DAYS }

data class MonthBucket(val month: Int, val count: Int, val distanceM: Double, val gainM: Double)

data class ArchiveStatsResult(
    val totals: Totals,
    val byType: Map<TripType, Totals>,
    /** 12 mesi dell'anno selezionato (o di tutti gli anni sommati). */
    val months: List<MonthBucket>,
    /** Totali per anno (per il grafico "tutti gli anni"). */
    val byYear: Map<Int, Totals>,
    val records: List<PersonalRecord>,
)

object ArchiveStats {

    fun compute(trips: List<StatsTrip>, year: Int?, types: Set<TripType>): ArchiveStatsResult {
        val byTypeFilter = if (types.isEmpty()) trips else trips.filter { it.type in types }
        val selected = if (year == null) byTypeFilter else byTypeFilter.filter { it.year == year }
        return ArchiveStatsResult(
            totals = totals(selected),
            byType = TripType.entries.associateWith { t -> totals(selected.filter { it.type == t }) },
            months = (1..12).map { m ->
                val inMonth = selected.filter { it.month == m }
                MonthBucket(m, inMonth.size, inMonth.sumOf { it.distanceM ?: 0.0 }, inMonth.sumOf { it.gainM ?: 0.0 })
            },
            byYear = byTypeFilter.groupBy { it.year }.toSortedMap().mapValues { totals(it.value) },
            records = records(selected),
        )
    }

    fun totals(trips: List<StatsTrip>) = Totals(
        count = trips.size,
        distanceM = trips.sumOf { it.distanceM ?: 0.0 },
        gainM = trips.sumOf { it.gainM ?: 0.0 },
        durationS = trips.sumOf { it.durationS ?: 0L },
        days = trips.sumOf { it.days },
    )

    fun records(trips: List<StatsTrip>): List<PersonalRecord> = buildList {
        trips.filter { (it.distanceM ?: 0.0) > 0 }.maxByOrNull { it.distanceM!! }?.let {
            add(PersonalRecord("Uscita più lunga", RecordKind.DISTANCE, it.distanceM!!, it))
        }
        trips.filter { (it.gainM ?: 0.0) > 0 }.maxByOrNull { it.gainM!! }?.let {
            add(PersonalRecord("Maggior dislivello", RecordKind.ELEVATION, it.gainM!!, it))
        }
        trips.filter { it.maxElevationM != null }.maxByOrNull { it.maxElevationM!! }?.let {
            add(PersonalRecord("Quota più alta", RecordKind.ELEVATION, it.maxElevationM!!, it))
        }
        trips.filter { (it.durationS ?: 0) > 0 }.maxByOrNull { it.durationS!! }?.let {
            add(PersonalRecord("Più tempo in movimento", RecordKind.DURATION, it.durationS!!.toDouble(), it))
        }
        trips.filter { it.type == TripType.BIKE && (it.maxSpeedMps ?: 0.0) > 0 }.maxByOrNull { it.maxSpeedMps!! }?.let {
            add(PersonalRecord("Velocità massima in bici", RecordKind.SPEED, it.maxSpeedMps!!, it))
        }
        trips.filter { it.days > 1 }.maxByOrNull { it.days }?.let {
            add(PersonalRecord("Viaggio più lungo", RecordKind.DAYS, it.days.toDouble(), it))
        }
    }
}
