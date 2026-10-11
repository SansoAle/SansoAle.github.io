package it.sansoale.uscite.track

/**
 * Statistiche di una traccia o di un'uscita. Tutte le grandezze sono in unità SI
 * (metri, secondi, metri al secondo); `null` quando non calcolabili (es. niente orari o quote).
 */
data class TrackStats(
    val distanceM: Double? = null,
    val elevationGainM: Double? = null,
    val elevationLossM: Double? = null,
    val minElevationM: Double? = null,
    val maxElevationM: Double? = null,
    val totalDurationS: Long? = null,
    val movingDurationS: Long? = null,
    val avgSpeedMps: Double? = null,
    val maxSpeedMps: Double? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
) {
    companion object {
        val EMPTY = TrackStats()

        /**
         * Combina le statistiche di più tracce della stessa uscita: somma distanze, dislivelli e tempi,
         * prende minimo/massimo delle quote e ricalcola la velocità media.
         */
        fun combine(stats: List<TrackStats>): TrackStats {
            if (stats.isEmpty()) return EMPTY
            if (stats.size == 1) return stats.first()
            fun sumD(sel: (TrackStats) -> Double?) = stats.mapNotNull(sel).takeIf { it.isNotEmpty() }?.sum()
            fun sumL(sel: (TrackStats) -> Long?) = stats.mapNotNull(sel).takeIf { it.isNotEmpty() }?.sum()
            val distance = sumD { it.distanceM }
            val moving = sumL { it.movingDurationS }
            val total = sumL { it.totalDurationS }
            return TrackStats(
                distanceM = distance,
                elevationGainM = sumD { it.elevationGainM },
                elevationLossM = sumD { it.elevationLossM },
                minElevationM = stats.mapNotNull { it.minElevationM }.minOrNull(),
                maxElevationM = stats.mapNotNull { it.maxElevationM }.maxOrNull(),
                totalDurationS = total,
                movingDurationS = moving,
                avgSpeedMps = averageSpeed(distance, moving, total),
                maxSpeedMps = stats.mapNotNull { it.maxSpeedMps }.maxOrNull(),
                startTime = stats.mapNotNull { it.startTime }.minOrNull(),
                endTime = stats.mapNotNull { it.endTime }.maxOrNull(),
            )
        }

        fun averageSpeed(distanceM: Double?, movingS: Long?, totalS: Long?): Double? {
            if (distanceM == null) return null
            val seconds = movingS?.takeIf { it > 0 } ?: totalS?.takeIf { it > 0 } ?: return null
            return distanceM / seconds
        }
    }
}

/** Parametri del calcolo delle statistiche, adattabili al tipo di attività. */
data class StatsConfig(
    /** Finestra del filtro mediano sulle quote (numero di punti, dispari). */
    val elevationMedianWindow: Int = 5,
    /** Ampiezza (in metri lungo la traccia) della media mobile sulle quote. */
    val elevationSmoothingDistanceM: Double = 50.0,
    /** Variazione minima di quota per conteggiare salita o discesa (isteresi). */
    val elevationHysteresisM: Double = 3.0,
    /** Velocità sotto la quale si considera fermo. */
    val movingSpeedThresholdMps: Double = 0.5,
    /** Intervallo minimo su cui valutare se ci si sta muovendo (riduce l'effetto del rumore GPS da fermi). */
    val movingWindowS: Long = 10,
    /** Finestra temporale su cui calcolare la velocità massima (scarta i picchi del GPS). */
    val maxSpeedWindowS: Long = 10,
    /** Velocità oltre la quale un intervallo è considerato un errore del GPS. */
    val maxPlausibleSpeedMps: Double = 70.0,
) {
    companion object {
        val HIKING = StatsConfig(movingSpeedThresholdMps = 0.4, maxPlausibleSpeedMps = 30.0)
        val CYCLING = StatsConfig(movingSpeedThresholdMps = 1.0, maxPlausibleSpeedMps = 40.0)
        val DRIVING = StatsConfig(movingSpeedThresholdMps = 2.0, maxSpeedWindowS = 20, maxPlausibleSpeedMps = 70.0)
    }
}
