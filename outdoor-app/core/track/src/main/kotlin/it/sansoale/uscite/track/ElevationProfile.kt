package it.sansoale.uscite.track

data class ProfilePoint(
    /** Distanza dall'inizio in metri. */
    val distanceM: Double,
    val elevationM: Double,
    val lat: Double,
    val lon: Double,
    val time: Long?,
)

object ElevationProfile {

    /**
     * Costruisce il profilo altimetrico concatenando i segmenti (la distanza prosegue da un segmento
     * all'altro senza contare il salto). Le quote sono leggermente filtrate e il risultato è ridotto a
     * circa [maxPoints] punti per il disegno.
     */
    fun build(segments: List<List<TrackPoint>>, maxPoints: Int = 600, config: StatsConfig = StatsConfig()): List<ProfilePoint> {
        val all = ArrayList<ProfilePoint>()
        var offset = 0.0
        for (segment in segments) {
            if (segment.isEmpty()) continue
            val cum = Geo.cumulativeDistances(segment)
            val idx = segment.indices.filter { segment[it].ele != null }
            if (idx.isNotEmpty()) {
                val raw = DoubleArray(idx.size) { segment[idx[it]].ele!! }
                val dist = DoubleArray(idx.size) { cum[idx[it]] }
                val smooth = ElevationSmoother.distanceMovingAverage(
                    ElevationSmoother.medianFilter(raw, config.elevationMedianWindow),
                    dist,
                    20.0,
                )
                for (k in idx.indices) {
                    val p = segment[idx[k]]
                    all.add(ProfilePoint(offset + dist[k], smooth[k], p.lat, p.lon, p.time))
                }
            }
            offset += cum.last()
        }
        return downsample(all, maxPoints)
    }

    fun downsample(points: List<ProfilePoint>, maxPoints: Int): List<ProfilePoint> {
        if (points.size <= maxPoints || maxPoints < 2) return points
        val total = points.last().distanceM - points.first().distanceM
        if (total <= 0) return listOf(points.first(), points.last())
        val step = total / (maxPoints - 1)
        val out = ArrayList<ProfilePoint>(maxPoints)
        var next = points.first().distanceM
        for (p in points) {
            if (p.distanceM >= next) {
                out.add(p)
                next = p.distanceM + step
            }
        }
        if (out.last() !== points.last()) out.add(points.last())
        return out
    }

    /** Indice del punto del profilo più vicino alla distanza indicata. */
    fun indexAtDistance(profile: List<ProfilePoint>, distanceM: Double): Int {
        if (profile.isEmpty()) return -1
        var lo = 0
        var hi = profile.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (profile[mid].distanceM < distanceM) lo = mid + 1 else hi = mid
        }
        if (lo > 0 && distanceM - profile[lo - 1].distanceM < profile[lo].distanceM - distanceM) return lo - 1
        return lo
    }
}
