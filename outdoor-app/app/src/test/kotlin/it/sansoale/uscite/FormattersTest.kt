package it.sansoale.uscite

import it.sansoale.uscite.data.db.StatsColumns
import it.sansoale.uscite.data.settings.UnitSystem
import it.sansoale.uscite.domain.mergeStats
import it.sansoale.uscite.model.StatField
import it.sansoale.uscite.ui.common.UnitFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {

    private val metric = UnitFormatter(UnitSystem.METRIC)
    private val imperial = UnitFormatter(UnitSystem.IMPERIAL)

    @Test
    fun durate() {
        assertEquals(9000L, UnitFormatter.parseDuration("2:30"))
        assertEquals(9015L, UnitFormatter.parseDuration("2:30:15"))
        assertEquals(9000L, UnitFormatter.parseDuration("2h 30m"))
        assertEquals(5400L, UnitFormatter.parseDuration("90m"))
        assertEquals(5400L, UnitFormatter.parseDuration("1,5"))
        assertNull(UnitFormatter.parseDuration("boh"))
        assertEquals("2h 30m", metric.duration(9000))
        assertEquals("45m", metric.duration(2700))
    }

    @Test
    fun `campi modificabili - andata e ritorno`() {
        assertEquals("12,35", metric.toInput(StatField.DISTANCE, 12_346.0))
        assertEquals(12_350.0, metric.parseInput(StatField.DISTANCE, "12,35")!!, 1e-6)
        assertEquals(1200.0, metric.parseInput(StatField.ELEVATION_GAIN, "1200")!!, 1e-6)
        assertEquals("2:05", metric.toInput(StatField.MOVING_DURATION, 7500.0))
        assertEquals(10.0, metric.parseInput(StatField.AVG_SPEED, "36")!!, 1e-9)
        assertNull(metric.parseInput(StatField.DISTANCE, ""))
        // Imperiale
        assertEquals(1609.344, imperial.parseInput(StatField.DISTANCE, "1")!!, 1e-6)
        assertEquals("3281", imperial.toInput(StatField.MAX_ELEVATION, 1000.0))
    }

    @Test
    fun euro() {
        assertEquals(1250L, UnitFormatter.parseEuroCents("12,50"))
        assertEquals(1250L, UnitFormatter.parseEuroCents("€ 12.5"))
        assertNull(UnitFormatter.parseEuroCents("-3"))
    }

    @Test
    fun `statistiche manuali prevalgono su quelle automatiche`() {
        val auto = StatsColumns(distanceM = 10_000.0, elevationGainM = 800.0, movingDurationS = 3600, avgSpeedMps = 10_000.0 / 3600)
        val manual = StatsColumns(distanceM = 12_000.0, elevationGainM = 999.0)
        val merged = mergeStats(auto, manual, setOf(StatField.DISTANCE))
        assertEquals(12_000.0, merged.distanceM!!, 1e-9)
        assertEquals(800.0, merged.elevationGainM!!, 1e-9)
        // La velocità media viene ricalcolata perché la distanza è manuale.
        assertEquals(12_000.0 / 3600, merged.avgSpeedMps!!, 1e-9)
    }

    @Test
    fun `intervalli di date`() {
        val d = java.time.LocalDate.of(2024, 7, 14).toEpochDay()
        assertEquals("14 lug 2024", UnitFormatter.date(d))
        assertEquals("14 – 18 lug 2024", UnitFormatter.dateRange(d, d + 4))
    }
}
