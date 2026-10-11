package it.sansoale.uscite.track

object TrackStatsCalculator {

    fun compute(document: GpxDocument, config: StatsConfig = StatsConfig()): TrackStats =
        compute(document.segments, config)

    /**
     * Calcola le statistiche di un insieme di segmenti. La distanza non include i salti fra un segmento
     * e l'altro; la durata totale va dal primo all'ultimo orario disponibile.
     */
    fun compute(segments: List<List<TrackPoint>>, config: StatsConfig = StatsConfig()): TrackStats {
        val nonEmpty = segments.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return TrackStats.EMPTY

        var distance = 0.0
        var gain = 0.0
        var loss = 0.0
        var minEle: Double? = null
        var maxEle: Double? = null
        var hasElevation = false
        var moving = 0L
        var maxSpeed: Double? = null
        var start: Long? = null
        var end: Long? = null

        for (segment in nonEmpty) {
            val cum = Geo.cumulativeDistances(segment)
            distance += cum.last()

            // Quote
            val eleIdx = segment.indices.filter { segment[it].ele != null }
            if (eleIdx.size >= 2) {
                hasElevation = true
                val raw = DoubleArray(eleIdx.size) { segment[eleIdx[it]].ele!! }
                val dist = DoubleArray(eleIdx.size) { cum[eleIdx[it]] }
                val median = ElevationSmoother.medianFilter(raw, config.elevationMedianWindow)
                val smooth = ElevationSmoother.distanceMovingAverage(median, dist, config.elevationSmoothingDistanceM)
                val (g, l) = ElevationSmoother.gainLoss(smooth, config.elevationHysteresisM)
                gain += g
                loss += l
                // Min/max dal solo filtro mediano: la media mobile abbasserebbe troppo le vette.
                val mn = median.min()
                val mx = median.max()
                minEle = minEle?.let { minOf(it, mn) } ?: mn
                maxEle = maxEle?.let { maxOf(it, mx) } ?: mx
            } else if (eleIdx.size == 1) {
                val e = segment[eleIdx[0]].ele!!
                hasElevation = true
                minEle = minEle?.let { minOf(it, e) } ?: e
                maxEle = maxEle?.let { maxOf(it, e) } ?: e
            }

            // Tempi
            val timed = segment.indices.filter { segment[it].time != null }
            if (timed.isNotEmpty()) {
                val first = segment[timed.first()].time!!
                val last = segment[timed.last()].time!!
                start = start?.let { minOf(it, first) } ?: first
                end = end?.let { maxOf(it, last) } ?: last
                moving += movingTime(segment, cum, timed, config)
                maxSpeedOf(segment, cum, timed, config)?.let { s -> maxSpeed = maxSpeed?.let { maxOf(it, s) } ?: s }
            }
        }

        val total = if (start != null && end != null) (end - start) / 1000 else null
        val movingS = if (total != null) minOf(moving, total) else null
        return TrackStats(
            distanceM = distance,
            elevationGainM = if (hasElevation) gain else null,
            elevationLossM = if (hasElevation) loss else null,
            minElevationM = minEle,
            maxElevationM = maxEle,
            totalDurationS = total,
            movingDurationS = movingS,
            avgSpeedMps = TrackStats.averageSpeed(distance, movingS, total),
            maxSpeedMps = maxSpeed,
            startTime = start,
            endTime = end,
        )
    }

    /**
     * Tempo in movimento: la traccia viene percorsa a blocchi di almeno [StatsConfig.movingWindowS] secondi;
     * un blocco conta come movimento se la sua velocità media supera la soglia. Così il "tremolio" del GPS
     * da fermi non viene scambiato per movimento e le pause vengono escluse.
     */
    private fun movingTime(segment: List<TrackPoint>, cum: DoubleArray, timed: List<Int>, config: StatsConfig): Long {
        var movingMs = 0L
        var anchor = timed.first()
        for (k in 1 until timed.size) {
            val j = timed[k]
            val dt = segment[j].time!! - segment[anchor].time!!
            if (dt <= 0) {
                if (dt < 0) anchor = j // orari non monotoni: riparte da qui
                continue
            }
            if (dt >= config.movingWindowS * 1000 || k == timed.size - 1) {
                val speed = (cum[j] - cum[anchor]) / (dt / 1000.0)
                if (speed >= config.movingSpeedThresholdMps && speed <= config.maxPlausibleSpeedMps) movingMs += dt
                anchor = j
            }
        }
        return movingMs / 1000
    }

    private fun maxSpeedOf(segment: List<TrackPoint>, cum: DoubleArray, timed: List<Int>, config: StatsConfig): Double? {
        if (timed.size < 2) return null
        val windowMs = config.maxSpeedWindowS * 1000
        var best: Double? = null
        var i = 0
        for (k in 1 until timed.size) {
            val tj = segment[timed[k]].time!!
            while (i < k - 1 && tj - segment[timed[i + 1]].time!! >= windowMs) i++
            val dt = tj - segment[timed[i]].time!!
            if (dt < windowMs / 2 || dt <= 0) continue
            val speed = (cum[timed[k]] - cum[timed[i]]) / (dt / 1000.0)
            if (speed > config.maxPlausibleSpeedMps) continue
            if (best == null || speed > best) best = speed
        }
        return best
    }
}
