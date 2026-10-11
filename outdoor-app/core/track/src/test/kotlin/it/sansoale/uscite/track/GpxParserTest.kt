package it.sansoale.uscite.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxParserTest {

    private fun resource(name: String) = requireNotNull(javaClass.classLoader.getResourceAsStream(name)) { name }

    @Test
    fun `analizza l'escursione di esempio`() {
        val doc = GpxParser.parse(resource("escursione.gpx"))
        assertEquals("Escursione di prova", doc.name)
        assertEquals("Generatore di test", doc.creator)
        assertEquals(1, doc.tracks.size)
        assertEquals("Giro al Rifugio", doc.tracks[0].name)
        assertEquals(1, doc.segments.size)
        assertEquals(1370, doc.pointCount)
        val first = doc.segments[0][0]
        assertEquals(46.5, first.lat, 1e-9)
        assertEquals(11.8, first.lon, 1e-9)
        assertEquals(1497.7, first.ele!!, 1e-9)
        assertEquals(GpxParser.parseTime("2024-07-14T06:30:00Z"), first.time)
    }

    @Test
    fun `gestisce file Garmin con estensioni, piu segmenti, CDATA ed entita`() {
        val doc = GpxParser.parse(resource("bici_garmin.gpx"))
        assertEquals("Garmin Connect", doc.creator)
        assertEquals("Giro gravel & colline", doc.tracks.single().name)
        assertEquals(2, doc.segments.size)
        assertEquals(4, doc.segments[0].size)
        // Il punto con latitudine non valida viene scartato.
        assertEquals(3, doc.segments[1].size)
        assertEquals("Bar & ristoro \"Da Gino\"", doc.waypoints.single().name)
        // L'orario con fuso +02:00 viene convertito in UTC.
        assertEquals(GpxParser.parseTime("2024-05-01T08:00:00Z"), doc.segments[0][0].time)
        // Le estensioni non devono sovrascrivere i dati del punto.
        assertEquals(120.0, doc.segments[0][0].ele!!, 1e-9)
    }

    @Test
    fun `converte i percorsi rte in tracce`() {
        val doc = GpxParser.parse(resource("percorso_komoot.gpx"))
        val track = doc.tracks.single()
        assertEquals("Anello del lago", track.name)
        assertEquals(4, track.points.size)
        assertNull(track.points.last().ele)
        assertNull(track.points.first().time)
    }

    @Test(expected = GpxParseException::class)
    fun `rifiuta file che non sono GPX`() {
        GpxParser.parse("<kml><Document/></kml>")
    }

    @Test(expected = GpxParseException::class)
    fun `rifiuta XML malformato`() {
        GpxParser.parse("<gpx><trk>")
    }

    @Test
    fun `interpreta i vari formati di orario`() {
        val expected = 1_714_550_400_000L // 2024-05-01T08:00:00Z
        assertEquals(expected, GpxParser.parseTime("2024-05-01T08:00:00Z"))
        assertEquals(expected, GpxParser.parseTime("2024-05-01T08:00:00.000Z"))
        assertEquals(expected, GpxParser.parseTime("2024-05-01T10:00:00+02:00"))
        assertEquals(expected, GpxParser.parseTime("2024-05-01T08:00:00"))
        assertNull(GpxParser.parseTime("ieri"))
    }

    @Test
    fun `scrittura e rilettura conservano i dati`() {
        val original = GpxParser.parse(resource("bici_garmin.gpx"))
        val text = GpxWriter.toString(original.copy(name = "Prova <&>"))
        val reread = GpxParser.parse(text)
        assertEquals("Prova <&>", reread.name)
        assertEquals(original.pointCount, reread.pointCount)
        assertEquals(original.segments.size, reread.segments.size)
        original.segments.flatten().zip(reread.segments.flatten()).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-7)
            assertEquals(a.lon, b.lon, 1e-7)
            assertEquals(a.ele!!, b.ele!!, 0.05)
            assertEquals(a.time, b.time)
        }
        assertEquals(original.waypoints.single().name, reread.waypoints.single().name)
        assertTrue(text.contains("<gpx version=\"1.1\""))
        assertNotNull(reread.tracks.single().name)
    }
}
