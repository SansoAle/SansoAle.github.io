package it.sansoale.uscite.track

/**
 * Posiziona una foto lungo la traccia confrontando l'orario di scatto con i timestamp del GPX
 * (interpolazione lineare tra i due punti più vicini).
 */
object PhotoLocator {

    /** Tolleranza oltre l'inizio/fine della traccia entro cui una foto viene comunque agganciata. */
    const val DEFAULT_TOLERANCE_MS = 15 * 60 * 1000L

    fun locate(
        timeMillis: Long,
        segments: List<List<TrackPoint>>,
        toleranceMs: Long = DEFAULT_TOLERANCE_MS,
    ): LatLon? {
        val timed = segments.flatten().filter { it.time != null }.sortedBy { it.time }
        return locateSorted(timeMillis, timed, toleranceMs)
    }

    /** Come [locate], ma richiede punti già filtrati (con orario) e ordinati per orario. */
    fun locateSorted(timeMillis: Long, timed: List<TrackPoint>, toleranceMs: Long = DEFAULT_TOLERANCE_MS): LatLon? {
        if (timed.isEmpty()) return null
        val first = timed.first()
        val last = timed.last()
        if (timeMillis < first.time!! - toleranceMs || timeMillis > last.time!! + toleranceMs) return null
        if (timeMillis <= first.time) return LatLon(first.lat, first.lon)
        if (timeMillis >= last.time) return LatLon(last.lat, last.lon)

        var lo = 0
        var hi = timed.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (timed[mid].time!! <= timeMillis) lo = mid else hi = mid
        }
        val a = timed[lo]
        val b = timed[hi]
        val span = (b.time!! - a.time!!).toDouble()
        val f = if (span <= 0) 0.0 else (timeMillis - a.time) / span
        return LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
    }
}
