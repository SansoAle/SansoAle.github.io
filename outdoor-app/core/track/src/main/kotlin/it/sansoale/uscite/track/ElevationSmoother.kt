package it.sansoale.uscite.track

import kotlin.math.min

/**
 * Smoothing delle quote GPS/barometriche per evitare di gonfiare il dislivello:
 * 1. filtro mediano (rimuove i picchi isolati);
 * 2. media mobile pesata sulla distanza percorsa (attenua il rumore);
 * 3. il conteggio di salita/discesa usa un'isteresi (vedi [gainLoss]).
 */
object ElevationSmoother {

    fun medianFilter(values: DoubleArray, window: Int): DoubleArray {
        if (window <= 1 || values.size < 3) return values.copyOf()
        val half = window / 2
        val out = DoubleArray(values.size)
        val buf = DoubleArray(window)
        for (i in values.indices) {
            // Finestra simmetrica che si restringe ai bordi: così una rampa regolare resta invariata.
            val h = min(half, min(i, values.size - 1 - i))
            val from = i - h
            val to = i + h
            val n = to - from + 1
            for (k in 0 until n) buf[k] = values[from + k]
            java.util.Arrays.sort(buf, 0, n)
            out[i] = if (n % 2 == 1) buf[n / 2] else (buf[n / 2 - 1] + buf[n / 2]) / 2
        }
        return out
    }

    /** Media mobile in cui ogni punto è la media dei punti entro ±`windowM/2` metri lungo la traccia. */
    fun distanceMovingAverage(values: DoubleArray, cumulativeDistance: DoubleArray, windowM: Double): DoubleArray {
        require(values.size == cumulativeDistance.size)
        if (windowM <= 0 || values.size < 3) return values.copyOf()
        val half = windowM / 2
        val out = DoubleArray(values.size)
        var lo = 0
        var hi = 0
        var sum = 0.0
        for (i in values.indices) {
            while (hi < values.size && cumulativeDistance[hi] <= cumulativeDistance[i] + half) {
                sum += values[hi]; hi++
            }
            while (cumulativeDistance[lo] < cumulativeDistance[i] - half) {
                sum -= values[lo]; lo++
            }
            out[i] = sum / (hi - lo)
        }
        return out
    }

    fun smooth(values: DoubleArray, cumulativeDistance: DoubleArray, config: StatsConfig): DoubleArray =
        distanceMovingAverage(
            medianFilter(values, config.elevationMedianWindow),
            cumulativeDistance,
            config.elevationSmoothingDistanceM,
        )

    /** Somma salita e discesa contando solo le variazioni che superano la soglia di isteresi. */
    fun gainLoss(values: DoubleArray, hysteresisM: Double): Pair<Double, Double> {
        if (values.isEmpty()) return 0.0 to 0.0
        var gain = 0.0
        var loss = 0.0
        var ref = values[0]
        for (i in 1 until values.size) {
            val diff = values[i] - ref
            if (diff >= hysteresisM) {
                gain += diff; ref = values[i]
            } else if (diff <= -hysteresisM) {
                loss -= diff; ref = values[i]
            }
        }
        // L'ultimo tratto sotto soglia viene comunque contato, per non perdere gli ultimi metri.
        val tail = values.last() - ref
        if (tail > 0) gain += tail else loss -= tail
        return gain to loss
    }
}
