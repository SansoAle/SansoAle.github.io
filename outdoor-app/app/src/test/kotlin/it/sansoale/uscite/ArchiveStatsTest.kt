package it.sansoale.uscite

import it.sansoale.uscite.domain.ArchiveStats
import it.sansoale.uscite.domain.StatsTrip
import it.sansoale.uscite.model.TripType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ArchiveStatsTest {

    private fun trip(id: Long, type: TripType, date: String, km: Double, gain: Double, days: Long = 1) = StatsTrip(
        id = id,
        title = "Uscita $id",
        type = type,
        startDate = LocalDate.parse(date).toEpochDay(),
        endDate = LocalDate.parse(date).toEpochDay() + days - 1,
        distanceM = km * 1000,
        gainM = gain,
        maxElevationM = 1000 + gain,
        durationS = (km * 600).toLong(),
        maxSpeedMps = if (type == TripType.BIKE) km / 2 else null,
    )

    private val trips = listOf(
        trip(1, TripType.MOUNTAIN, "2023-07-10", 12.0, 900.0),
        trip(2, TripType.MOUNTAIN, "2024-07-14", 15.0, 1200.0),
        trip(3, TripType.BIKE, "2024-05-01", 80.0, 600.0),
        trip(4, TripType.VAN, "2024-08-05", 1500.0, 0.0, days = 10),
    )

    @Test
    fun `totali per anno e per tipo`() {
        val r = ArchiveStats.compute(trips, 2024, emptySet())
        assertEquals(3, r.totals.count)
        assertEquals(1_595_000.0, r.totals.distanceM, 1e-6)
        assertEquals(1, r.byType.getValue(TripType.MOUNTAIN).count)
        assertEquals(1800.0, r.totals.gainM, 1e-6)
        assertEquals(12, r.totals.days)
        assertEquals(listOf(2023, 2024), r.byYear.keys.toList())
    }

    @Test
    fun `istogramma mensile`() {
        val r = ArchiveStats.compute(trips, 2024, setOf(TripType.MOUNTAIN, TripType.BIKE))
        assertEquals(12, r.months.size)
        assertEquals(1, r.months[4].count) // maggio
        assertEquals(80_000.0, r.months[4].distanceM, 1e-6)
        assertEquals(1, r.months[6].count) // luglio
        assertEquals(0, r.months[7].count) // agosto: il van è escluso dal filtro
    }

    @Test
    fun `record personali`() {
        val records = ArchiveStats.compute(trips, null, emptySet()).records.associateBy { it.label }
        assertEquals(4L, records.getValue("Uscita più lunga").trip.id)
        assertEquals(2L, records.getValue("Maggior dislivello").trip.id)
        assertEquals(3L, records.getValue("Velocità massima in bici").trip.id)
        assertEquals(10.0, records.getValue("Viaggio più lungo").value, 0.0)
    }
}
