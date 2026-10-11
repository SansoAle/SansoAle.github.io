package it.sansoale.uscite.track

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Formato binario compatto dei punti di una traccia, usato come cache per caricare velocemente
 * mappa e profilo senza ri-analizzare il GPX originale.
 */
object TrackPointsCodec {
    private const val MAGIC = 0x55534354 // "USCT"
    private const val VERSION = 1

    fun write(segments: List<List<TrackPoint>>, output: OutputStream) {
        val out = DataOutputStream(output.buffered())
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeInt(segments.size)
        for (segment in segments) {
            out.writeInt(segment.size)
            for (p in segment) {
                out.writeDouble(p.lat)
                out.writeDouble(p.lon)
                out.writeFloat(p.ele?.toFloat() ?: Float.NaN)
                out.writeLong(p.time ?: Long.MIN_VALUE)
            }
        }
        out.flush()
    }

    fun read(input: InputStream): List<List<TrackPoint>> {
        val inp = DataInputStream(input.buffered())
        if (inp.readInt() != MAGIC) throw IOException("Formato cache traccia non valido")
        val version = inp.readInt()
        if (version != VERSION) throw IOException("Versione cache traccia non supportata: $version")
        val segCount = inp.readInt()
        return List(segCount) {
            val n = inp.readInt()
            List(n) {
                val lat = inp.readDouble()
                val lon = inp.readDouble()
                val ele = inp.readFloat()
                val time = inp.readLong()
                TrackPoint(
                    lat,
                    lon,
                    if (ele.isNaN()) null else ele.toDouble(),
                    if (time == Long.MIN_VALUE) null else time,
                )
            }
        }
    }
}
