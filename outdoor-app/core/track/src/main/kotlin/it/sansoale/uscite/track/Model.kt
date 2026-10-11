package it.sansoale.uscite.track

/**
 * Un punto di una traccia.
 *
 * @property ele quota in metri, `null` se assente nel file.
 * @property time istante in millisecondi epoch UTC, `null` se assente.
 */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val time: Long? = null,
)

/** Una traccia composta da uno o più segmenti (le interruzioni del GPS separano i segmenti). */
data class GpxTrack(
    val name: String? = null,
    val segments: List<List<TrackPoint>>,
) {
    val points: List<TrackPoint> get() = segments.flatten()
    val isEmpty: Boolean get() = segments.all { it.isEmpty() }
}

data class GpxWaypoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val time: Long? = null,
    val name: String? = null,
    val description: String? = null,
)

data class GpxDocument(
    val name: String? = null,
    val description: String? = null,
    val creator: String? = null,
    val time: Long? = null,
    /** Tracce (`trk`) e percorsi (`rte`, convertiti in tracce con un solo segmento). */
    val tracks: List<GpxTrack> = emptyList(),
    val waypoints: List<GpxWaypoint> = emptyList(),
) {
    /** Tutti i segmenti di tutte le tracce, nell'ordine del file. */
    val segments: List<List<TrackPoint>> get() = tracks.flatMap { it.segments }.filter { it.isNotEmpty() }
    val pointCount: Int get() = tracks.sumOf { t -> t.segments.sumOf { it.size } }
}

data class LatLon(val lat: Double, val lon: Double)

data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    fun union(other: BoundingBox) = BoundingBox(
        minOf(minLat, other.minLat),
        maxOf(maxLat, other.maxLat),
        minOf(minLon, other.minLon),
        maxOf(maxLon, other.maxLon),
    )

    companion object {
        fun of(points: Iterable<TrackPoint>): BoundingBox? {
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            var any = false
            for (p in points) {
                any = true
                if (p.lat < minLat) minLat = p.lat
                if (p.lat > maxLat) maxLat = p.lat
                if (p.lon < minLon) minLon = p.lon
                if (p.lon > maxLon) maxLon = p.lon
            }
            return if (any) BoundingBox(minLat, maxLat, minLon, maxLon) else null
        }
    }
}
