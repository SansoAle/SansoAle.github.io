package it.sansoale.uscite.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackStatsCalculatorTest {

    private fun load(name: String) =
        GpxParser.parse(requireNotNull(javaClass.classLoader.getResourceAsStream(name)) { name })

    /**
     * Il file di esempio simula: 3 km di salita (+600 m) a 0,8 m/s, 10 minuti di sosta in vetta,
     * 3 km di discesa (-600 m) a 1,2 m/s; quote con rumore gaussiano (σ = 2 m) e picchi di +15 m.
     */
    @Test
    fun `statistiche dell'escursione di esempio`() {
        val stats = TrackStatsCalculator.compute(load("escursione.gpx"), StatsConfig.HIKING)

        assertEquals(6000.0, stats.distanceM!!, 250.0)
        assertEquals(600.0, stats.elevationGainM!!, 40.0)
        assertEquals(600.0, stats.elevationLossM!!, 40.0)
        assertEquals(1500.0, stats.minElevationM!!, 6.0)
        assertEquals(2100.0, stats.maxElevationM!!, 6.0)
        assertEquals(6850L, stats.totalDurationS) // 06:30:00 → 08:24:10
        // Tempo in movimento ≈ 6250 s: la sosta di 10 minuti va esclusa.
        assertEquals(6250.0, stats.movingDurationS!!.toDouble(), 150.0)
        assertEquals(stats.distanceM!! / stats.movingDurationS!!, stats.avgSpeedMps!!, 1e-9)
        // La velocità massima reale è 1,2 m/s.
        assertEquals(1.2, stats.maxSpeedMps!!, 0.15)
        assertNotNull(stats.startTime)
        assertNotNull(stats.endTime)
    }

    @Test
    fun `lo smoothing evita di gonfiare il dislivello`() {
        val doc = load("escursione.gpx")
        val raw = TrackStatsCalculator.compute(
            doc,
            StatsConfig(elevationMedianWindow = 1, elevationSmoothingDistanceM = 0.0, elevationHysteresisM = 0.0),
        )
        val smoothed = TrackStatsCalculator.compute(doc, StatsConfig.HIKING)
        // Senza filtri il rumore gonfia il dislivello di molto.
        assertTrue("raw=${raw.elevationGainM}", raw.elevationGainM!! > 900)
        assertTrue(smoothed.elevationGainM!! < raw.elevationGainM!! * 0.75)
    }

    @Test
    fun `piu segmenti non contano il salto tra un segmento e l'altro`() {
        val stats = TrackStatsCalculator.compute(load("bici_garmin.gpx"), StatsConfig.CYCLING)
        // 3 tratti da ~100 m + 2 tratti da ~100 m.
        assertEquals(500.4, stats.distanceM!!, 1.0)
        assertEquals(6.0, stats.elevationGainM!!, 0.01)
        assertEquals(4.0, stats.elevationLossM!!, 0.01)
        assertEquals(120.0, stats.minElevationM!!, 0.01)
        assertEquals(130.0, stats.maxElevationM!!, 0.01)
        assertEquals(640L, stats.totalDurationS) // 08:00:00 → 08:10:40 UTC
        assertEquals(100L, stats.movingDurationS)
        assertEquals(5.0, stats.maxSpeedMps!!, 0.05)
    }

    @Test
    fun `traccia senza orari ha durate e velocita nulle`() {
        val stats = TrackStatsCalculator.compute(load("percorso_komoot.gpx"))
        assertEquals(333.6, stats.distanceM!!, 1.0)
        assertEquals(30.0, stats.elevationGainM!!, 0.01)
        assertNull(stats.totalDurationS)
        assertNull(stats.movingDurationS)
        assertNull(stats.avgSpeedMps)
        assertNull(stats.maxSpeedMps)
    }

    @Test
    fun `traccia vuota`() {
        assertEquals(TrackStats.EMPTY, TrackStatsCalculator.compute(emptyList()))
    }

    @Test
    fun `combina le statistiche di piu tracce`() {
        val a = TrackStats(1000.0, 100.0, 50.0, 500.0, 600.0, 600, 500, 2.0, 3.0, 0, 600_000)
        val b = TrackStats(3000.0, 200.0, 250.0, 450.0, 700.0, 1200, 1000, 3.0, 5.0, 1_000_000, 2_200_000)
        val c = TrackStats.combine(listOf(a, b))
        assertEquals(4000.0, c.distanceM!!, 1e-9)
        assertEquals(300.0, c.elevationGainM!!, 1e-9)
        assertEquals(300.0, c.elevationLossM!!, 1e-9)
        assertEquals(450.0, c.minElevationM!!, 1e-9)
        assertEquals(700.0, c.maxElevationM!!, 1e-9)
        assertEquals(1800L, c.totalDurationS)
        assertEquals(1500L, c.movingDurationS)
        assertEquals(4000.0 / 1500, c.avgSpeedMps!!, 1e-9)
        assertEquals(5.0, c.maxSpeedMps!!, 1e-9)
        assertEquals(0L, c.startTime)
        assertEquals(2_200_000L, c.endTime)
    }

    @Test
    fun `isteresi del dislivello`() {
        val values = doubleArrayOf(100.0, 102.0, 101.0, 103.0, 102.0, 110.0, 105.0, 104.0)
        val (gain, loss) = ElevationSmoother.gainLoss(values, 3.0)
        assertEquals(10.0, gain, 1e-9) // 100 → 103 → 110
        assertEquals(6.0, loss, 1e-9) // 110 → 105 → 104 (ultimo tratto sotto soglia incluso)
    }

    @Test
    fun `il filtro mediano rimuove i picchi isolati`() {
        val values = doubleArrayOf(100.0, 100.0, 100.0, 150.0, 100.0, 100.0, 100.0)
        val filtered = ElevationSmoother.medianFilter(values, 5)
        assertTrue(filtered.all { it == 100.0 })
    }
}
