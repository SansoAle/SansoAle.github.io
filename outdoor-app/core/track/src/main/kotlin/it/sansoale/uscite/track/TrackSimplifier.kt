package it.sansoale.uscite.track

import kotlin.math.cos

/** Semplificazione Douglas-Peucker (iterativa) con tolleranza in metri. */
object TrackSimplifier {

    fun simplify(points: List<TrackPoint>, toleranceM: Double): List<TrackPoint> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        // Proiezione equirettangolare locale: sufficiente per le distanze in gioco.
        val refLat = Math.toRadians(points.map { it.lat }.average())
        val kx = Geo.EARTH_RADIUS_M * cos(refLat) * Math.PI / 180
        val ky = Geo.EARTH_RADIUS_M * Math.PI / 180
        val xs = DoubleArray(points.size) { points[it].lon * kx }
        val ys = DoubleArray(points.size) { points[it].lat * ky }
        val tol2 = toleranceM * toleranceM

        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, points.size - 1))
        while (stack.isNotEmpty()) {
            val (first, last) = stack.removeLast().let { it[0] to it[1] }
            var maxD = -1.0
            var index = -1
            for (i in first + 1 until last) {
                val d = segmentDistance2(xs[i], ys[i], xs[first], ys[first], xs[last], ys[last])
                if (d > maxD) {
                    maxD = d; index = i
                }
            }
            if (index != -1 && maxD > tol2) {
                keep[index] = true
                stack.addLast(intArrayOf(first, index))
                stack.addLast(intArrayOf(index, last))
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /** Riduce la traccia a circa [maxPoints] punti aumentando la tolleranza se necessario. */
    fun simplifyToMax(points: List<TrackPoint>, maxPoints: Int, initialToleranceM: Double = 5.0): List<TrackPoint> {
        var tol = initialToleranceM
        var result = simplify(points, tol)
        while (result.size > maxPoints && tol < 10_000) {
            tol *= 2
            result = simplify(points, tol)
        }
        return result
    }

    private fun segmentDistance2(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return (px - ax).let { it * it } + (py - ay).let { it * it }
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return (px - cx).let { it * it } + (py - cy).let { it * it }
    }
}
