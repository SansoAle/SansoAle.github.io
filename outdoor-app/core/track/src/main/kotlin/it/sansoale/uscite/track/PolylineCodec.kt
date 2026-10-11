package it.sansoale.uscite.track

import kotlin.math.roundToLong

/**
 * Codifica compatta di una polilinea (algoritmo "Encoded Polyline", precisione 1e-5 ≈ 1 m).
 * Usata per salvare nel database la versione semplificata delle tracce per la mappa globale.
 */
object PolylineCodec {

    fun encode(points: List<LatLon>, precision: Int = 5): String {
        val factor = Math.pow(10.0, precision.toDouble())
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLon = 0L
        for (p in points) {
            val lat = (p.lat * factor).roundToLong()
            val lon = (p.lon * factor).roundToLong()
            encodeValue(lat - prevLat, sb)
            encodeValue(lon - prevLon, sb)
            prevLat = lat
            prevLon = lon
        }
        return sb.toString()
    }

    fun decode(encoded: String, precision: Int = 5): List<LatLon> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<LatLon>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val (dLat, i1) = decodeValue(encoded, index)
            if (i1 >= encoded.length) break
            val (dLon, i2) = decodeValue(encoded, i1)
            index = i2
            lat += dLat
            lon += dLon
            out.add(LatLon(lat / factor, lon / factor))
        }
        return out
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toInt().toChar())
    }

    private fun decodeValue(s: String, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var index = start
        var b: Int
        do {
            b = s[index++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
        } while (b >= 0x20 && index < s.length)
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to index
    }
}
