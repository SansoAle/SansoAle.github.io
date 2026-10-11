package it.sansoale.uscite.track

import java.io.OutputStream
import java.io.Writer
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Scrittura di file GPX 1.1. */
object GpxWriter {

    fun write(document: GpxDocument, out: OutputStream) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        write(document, writer)
        writer.flush()
    }

    fun toString(document: GpxDocument): String {
        val sw = java.io.StringWriter()
        write(document, sw)
        return sw.toString()
    }

    fun write(document: GpxDocument, w: Writer) {
        w.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        w.append("<gpx version=\"1.1\" creator=\"")
            .append(escape(document.creator ?: "Diario Uscite"))
            .append("\" xmlns=\"http://www.topografix.com/GPX/1/1\"")
            .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
            .append(" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        if (document.name != null || document.description != null || document.time != null) {
            w.append("  <metadata>\n")
            document.name?.let { w.append("    <name>").append(escape(it)).append("</name>\n") }
            document.description?.let { w.append("    <desc>").append(escape(it)).append("</desc>\n") }
            document.time?.let { w.append("    <time>").append(formatTime(it)).append("</time>\n") }
            w.append("  </metadata>\n")
        }
        for (wpt in document.waypoints) {
            w.append("  <wpt lat=\"").append(coord(wpt.lat)).append("\" lon=\"").append(coord(wpt.lon)).append("\">\n")
            wpt.ele?.let { w.append("    <ele>").append(ele(it)).append("</ele>\n") }
            wpt.time?.let { w.append("    <time>").append(formatTime(it)).append("</time>\n") }
            wpt.name?.let { w.append("    <name>").append(escape(it)).append("</name>\n") }
            wpt.description?.let { w.append("    <desc>").append(escape(it)).append("</desc>\n") }
            w.append("  </wpt>\n")
        }
        for (track in document.tracks) {
            w.append("  <trk>\n")
            track.name?.let { w.append("    <name>").append(escape(it)).append("</name>\n") }
            for (segment in track.segments) {
                w.append("    <trkseg>\n")
                for (p in segment) {
                    w.append("      <trkpt lat=\"").append(coord(p.lat)).append("\" lon=\"").append(coord(p.lon)).append("\">")
                    p.ele?.let { w.append("<ele>").append(ele(it)).append("</ele>") }
                    p.time?.let { w.append("<time>").append(formatTime(it)).append("</time>") }
                    w.append("</trkpt>\n")
                }
                w.append("    </trkseg>\n")
            }
            w.append("  </trk>\n")
        }
        w.append("</gpx>\n")
    }

    fun formatTime(epochMillis: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))

    private fun coord(v: Double) = String.format(Locale.ROOT, "%.7f", v)
    private fun ele(v: Double) = String.format(Locale.ROOT, "%.1f", v)

    fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> if (c.code >= 0x20 || c == '\n' || c == '\t' || c == '\r') sb.append(c)
            }
        }
        return sb.toString()
    }
}
