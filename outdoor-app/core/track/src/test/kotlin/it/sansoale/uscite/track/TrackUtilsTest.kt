package it.sansoale.uscite.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TrackUtilsTest {

    private val hike by lazy {
        GpxParser.parse(requireNotNull(javaClass.classLoader.getResourceAsStream("escursione.gpx")))
    }

    @Test
    fun `codifica polilinea - esempio di riferimento`() {
        // Esempio ufficiale dell'algoritmo Encoded Polyline.
        val points = listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453))
        val encoded = PolylineCodec.encode(points)
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", encoded)
        val decoded = PolylineCodec.decode(encoded)
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-5)
            assertEquals(a.lon, b.lon, 1e-5)
        }
    }

    @Test
    fun `semplificazione mantiene estremi e forma`() {
        val points = hike.segments.single()
        val simplified = TrackSimplifier.simplify(points, 10.0)
        assertTrue(simplified.size < points.size / 3)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
        val capped = TrackSimplifier.simplifyToMax(points, 50)
        assertTrue(capped.size <= 50)
    }

    @Test
    fun `semplificazione di una retta lascia solo gli estremi`() {
        val line = (0..10).map { TrackPoint(45.0 + it * 0.001, 9.0) }
        assertEquals(2, TrackSimplifier.simplify(line, 1.0).size)
    }

    @Test
    fun `posizionamento foto per orario`() {
        val segment = listOf(
            TrackPoint(45.0, 9.0, time = 1_000_000),
            TrackPoint(45.1, 9.2, time = 2_000_000),
        )
        val mid = PhotoLocator.locate(1_500_000, listOf(segment))!!
        assertEquals(45.05, mid.lat, 1e-9)
        assertEquals(9.1, mid.lon, 1e-9)
        // Entro la tolleranza si aggancia all'estremo.
        val before = PhotoLocator.locate(1_000_000 - 60_000, listOf(segment))!!
        assertEquals(45.0, before.lat, 1e-9)
        // Fuori tolleranza: nessuna posizione.
        assertNull(PhotoLocator.locate(1_000_000 - PhotoLocator.DEFAULT_TOLERANCE_MS - 1, listOf(segment)))
        assertNull(PhotoLocator.locate(1_500_000, listOf(listOf(TrackPoint(45.0, 9.0)))))
    }

    @Test
    fun `posizionamento foto sull'escursione`() {
        // La salita termina alle 07:32:30, poi 10 minuti di sosta in vetta.
        val photoTime = GpxParser.parseTime("2024-07-14T07:37:00Z")!!
        val pos = PhotoLocator.locate(photoTime, hike.segments)!!
        val summit = hike.segments.single().first { it.time == GpxParser.parseTime("2024-07-14T07:32:30Z") }
        assertTrue(Geo.distance(pos.lat, pos.lon, summit.lat, summit.lon) < 10)
    }

    @Test
    fun `profilo altimetrico ridotto e ordinato`() {
        val profile = ElevationProfile.build(hike.segments, maxPoints = 300)
        assertTrue(profile.size in 250..301)
        assertTrue(profile.zipWithNext().all { (a, b) -> b.distanceM >= a.distanceM })
        assertEquals(0.0, profile.first().distanceM, 1e-9)
        val idx = ElevationProfile.indexAtDistance(profile, 3000.0)
        assertEquals(3000.0, profile[idx].distanceM, 50.0)
        assertEquals(2100.0, profile.maxOf { it.elevationM }, 10.0)
    }

    @Test
    fun `cache binaria dei punti`() {
        val segments = hike.segments + listOf(listOf(TrackPoint(1.0, 2.0)))
        val bytes = ByteArrayOutputStream().also { TrackPointsCodec.write(segments, it) }.toByteArray()
        val read = TrackPointsCodec.read(ByteArrayInputStream(bytes))
        assertEquals(segments.size, read.size)
        segments.flatten().zip(read.flatten()).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 0.0)
            assertEquals(a.lon, b.lon, 0.0)
            if (a.ele == null) assertNull(b.ele) else assertEquals(a.ele!!, b.ele!!, 0.001)
            assertEquals(a.time, b.time)
        }
    }

    @Test
    fun `bounding box`() {
        val box = BoundingBox.of(hike.segments.single())!!
        assertTrue(box.minLat < box.maxLat)
        assertTrue(box.minLon < box.maxLon)
        assertNull(BoundingBox.of(emptyList()))
    }
}
