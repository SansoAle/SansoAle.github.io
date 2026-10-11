package it.sansoale.uscite.track

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.xml.parsers.SAXParserFactory

class GpxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parser GPX 1.0/1.1 in streaming (SAX), disponibile sia sulla JVM sia su Android.
 *
 * Supporta tracce (`trk`), percorsi (`rte`, convertiti in tracce) e waypoint (`wpt`).
 * Le estensioni (Garmin, Strava, Komoot…) vengono ignorate senza errori.
 */
object GpxParser {

    fun parse(input: InputStream): GpxDocument {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
        }
        // Protezione da XXE dove supportato.
        listOf(
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
        ).forEach { feature -> runCatching { factory.setFeature(feature, false) } }

        val handler = Handler()
        try {
            factory.newSAXParser().parse(InputSource(input), handler)
        } catch (e: SAXException) {
            throw GpxParseException("File GPX non valido: ${e.message}", e)
        }
        if (!handler.sawGpxRoot) throw GpxParseException("Il file non è un GPX")
        return handler.build()
    }

    fun parse(text: String): GpxDocument = parse(text.byteInputStream())

    /** Interpreta un orario ISO-8601 (con o senza fuso; senza fuso si assume UTC). */
    fun parseTime(value: String): Long? {
        val s = value.trim()
        if (s.isEmpty()) return null
        runCatching { return Instant.parse(s).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }
        return null
    }

    private class Handler : DefaultHandler() {
        var sawGpxRoot = false
        private val stack = ArrayDeque<String>()
        private val text = StringBuilder()

        private var creator: String? = null
        private var docName: String? = null
        private var docDesc: String? = null
        private var docTime: Long? = null
        private val tracks = mutableListOf<GpxTrack>()
        private val waypoints = mutableListOf<GpxWaypoint>()

        // Stato della traccia corrente
        private var trackName: String? = null
        private var segments = mutableListOf<List<TrackPoint>>()
        private var currentSegment = mutableListOf<TrackPoint>()

        // Stato del punto corrente (trkpt, rtept o wpt)
        private var ptLat = Double.NaN
        private var ptLon = Double.NaN
        private var ptEle: Double? = null
        private var ptTime: Long? = null
        private var ptName: String? = null
        private var ptDesc: String? = null

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = elementName(localName, qName)
            val parent = stack.lastOrNull()
            stack.addLast(name)
            text.setLength(0)
            if (stack.contains("extensions")) return
            when (name) {
                "gpx" -> if (parent == null) {
                    sawGpxRoot = true
                    creator = attributes.getValue("creator")
                }
                "trk", "rte" -> {
                    trackName = null
                    segments = mutableListOf()
                    currentSegment = mutableListOf()
                }
                "trkseg" -> currentSegment = mutableListOf()
                "trkpt", "rtept", "wpt" -> {
                    ptLat = attributes.getValue("lat")?.trim()?.toDoubleOrNull() ?: Double.NaN
                    ptLon = attributes.getValue("lon")?.trim()?.toDoubleOrNull() ?: Double.NaN
                    ptEle = null
                    ptTime = null
                    ptName = null
                    ptDesc = null
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = elementName(localName, qName)
            stack.removeLastOrNull()
            if (stack.contains("extensions") || name == "extensions") {
                text.setLength(0)
                return
            }
            val parent = stack.lastOrNull()
            val value = text.toString().trim()
            when (name) {
                "ele" -> if (parent in POINT_TAGS) ptEle = value.toDoubleOrNull()
                "time" -> when (parent) {
                    in POINT_TAGS -> ptTime = parseTime(value)
                    "metadata", "gpx" -> docTime = parseTime(value)
                }
                "name" -> when (parent) {
                    in POINT_TAGS -> ptName = value.ifEmpty { null }
                    "trk", "rte" -> trackName = value.ifEmpty { null }
                    "metadata", "gpx" -> docName = value.ifEmpty { null }
                }
                "desc" -> when (parent) {
                    in POINT_TAGS -> ptDesc = value.ifEmpty { null }
                    "metadata", "gpx" -> docDesc = value.ifEmpty { null }
                }
                "trkpt", "rtept" -> if (validPoint()) {
                    currentSegment.add(TrackPoint(ptLat, ptLon, ptEle, ptTime))
                }
                "trkseg" -> {
                    if (currentSegment.isNotEmpty()) segments.add(currentSegment)
                    currentSegment = mutableListOf()
                }
                "rte" -> {
                    if (currentSegment.isNotEmpty()) segments.add(currentSegment)
                    if (segments.isNotEmpty()) tracks.add(GpxTrack(trackName, segments))
                }
                "trk" -> {
                    // Alcuni file mettono i trkpt direttamente nel trk, senza trkseg.
                    if (currentSegment.isNotEmpty()) segments.add(currentSegment)
                    if (segments.isNotEmpty()) tracks.add(GpxTrack(trackName, segments))
                }
                "wpt" -> if (validPoint()) {
                    waypoints.add(GpxWaypoint(ptLat, ptLon, ptEle, ptTime, ptName, ptDesc))
                }
            }
            text.setLength(0)
        }

        private fun validPoint() =
            !ptLat.isNaN() && !ptLon.isNaN() && ptLat in -90.0..90.0 && ptLon in -180.0..180.0

        fun build() = GpxDocument(
            name = docName,
            description = docDesc,
            creator = creator,
            time = docTime,
            tracks = tracks.toList(),
            waypoints = waypoints.toList(),
        )

        private fun elementName(localName: String?, qName: String?): String =
            localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')

        companion object {
            val POINT_TAGS = setOf("trkpt", "rtept", "wpt")
        }
    }
}
