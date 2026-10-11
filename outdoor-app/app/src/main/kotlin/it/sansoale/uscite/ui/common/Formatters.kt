package it.sansoale.uscite.ui.common

import it.sansoale.uscite.data.settings.UnitSystem
import it.sansoale.uscite.model.StatField
import it.sansoale.uscite.model.StatKind
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

val ITALIAN: Locale = Locale.ITALY

/** Formattazione e interpretazione dei valori secondo il sistema di unità scelto. */
class UnitFormatter(val system: UnitSystem = UnitSystem.METRIC) {

    private val imperial get() = system == UnitSystem.IMPERIAL

    val distanceUnit get() = if (imperial) "mi" else "km"
    val elevationUnit get() = if (imperial) "ft" else "m"
    val speedUnit get() = if (imperial) "mph" else "km/h"

    fun distanceValue(meters: Double): Double = if (imperial) meters / METERS_PER_MILE else meters / 1000.0
    fun elevationValue(meters: Double): Double = if (imperial) meters * FEET_PER_METER else meters
    fun speedValue(mps: Double): Double = if (imperial) mps * 3600 / METERS_PER_MILE else mps * 3.6

    fun distance(meters: Double?, decimals: Int = 1): String =
        meters?.let { "${number(distanceValue(it), decimals)} $distanceUnit" } ?: "—"

    /** Distanza compatta per assi e grafici: sotto 1 km in metri/piedi. */
    fun shortDistance(meters: Double): String = when {
        imperial && meters < METERS_PER_MILE / 4 -> "${number(meters * FEET_PER_METER, 0)} ft"
        !imperial && meters < 1000 -> "${number(meters, 0)} m"
        else -> distance(meters, if (distanceValue(meters) < 10) 1 else 0)
    }

    fun elevation(meters: Double?): String = meters?.let { "${number(elevationValue(it), 0)} $elevationUnit" } ?: "—"

    fun speed(mps: Double?): String = mps?.let { "${number(speedValue(it), 1)} $speedUnit" } ?: "—"

    fun duration(seconds: Long?): String {
        if (seconds == null) return "—"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m"
    }

    fun stat(field: StatField, value: Double?): String = when (field.kind) {
        StatKind.DISTANCE -> distance(value, 2)
        StatKind.ELEVATION -> elevation(value)
        StatKind.DURATION -> duration(value?.toLong())
        StatKind.SPEED -> speed(value)
    }

    fun unitFor(field: StatField): String = when (field.kind) {
        StatKind.DISTANCE -> distanceUnit
        StatKind.ELEVATION -> elevationUnit
        StatKind.DURATION -> "h:mm"
        StatKind.SPEED -> speedUnit
    }

    /** Testo per un campo modificabile (senza unità). */
    fun toInput(field: StatField, value: Double?): String {
        if (value == null) return ""
        return when (field.kind) {
            StatKind.DISTANCE -> plain(distanceValue(value), 2)
            StatKind.ELEVATION -> plain(elevationValue(value), 0)
            StatKind.SPEED -> plain(speedValue(value), 1)
            StatKind.DURATION -> {
                val s = value.toLong()
                val h = s / 3600
                val m = (s % 3600) / 60
                val sec = s % 60
                if (sec == 0L) "$h:${m.toString().padStart(2, '0')}"
                else "$h:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
            }
        }
    }

    /** Interpreta un campo modificabile e restituisce il valore in unità SI; `null` se non valido o vuoto. */
    fun parseInput(field: StatField, text: String): Double? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return when (field.kind) {
            StatKind.DISTANCE -> parseNumber(t)?.let { if (imperial) it * METERS_PER_MILE else it * 1000 }
            StatKind.ELEVATION -> parseNumber(t)?.let { if (imperial) it / FEET_PER_METER else it }
            StatKind.SPEED -> parseNumber(t)?.let { if (imperial) it * METERS_PER_MILE / 3600 else it / 3.6 }
            StatKind.DURATION -> parseDuration(t)?.toDouble()
        }
    }

    companion object {
        const val METERS_PER_MILE = 1609.344
        const val FEET_PER_METER = 3.28084

        fun number(value: Double, decimals: Int): String =
            NumberFormat.getNumberInstance(ITALIAN).apply {
                maximumFractionDigits = decimals
                minimumFractionDigits = decimals
            }.format(value)

        /** Numero senza separatore delle migliaia, con virgola decimale, senza zeri finali inutili. */
        fun plain(value: Double, decimals: Int): String {
            val factor = Math.pow(10.0, decimals.toDouble())
            val rounded = (value * factor).roundToLong() / factor
            val s = if (decimals == 0) rounded.roundToLong().toString() else String.format(Locale.ROOT, "%.${decimals}f", rounded)
                .trimEnd('0').trimEnd('.')
            return s.replace('.', ',')
        }

        /** Accetta sia la virgola sia il punto come separatore decimale. */
        fun parseNumber(text: String): Double? = text.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()

        /** Accetta "h:mm", "h:mm:ss", "2h 30m", "90m" o un numero di ore ("1,5"). */
        fun parseDuration(text: String): Long? {
            val t = text.trim().lowercase()
            if (t.isEmpty()) return null
            if (':' in t) {
                val parts = t.split(':').map { it.trim().toLongOrNull() ?: return null }
                return when (parts.size) {
                    2 -> parts[0] * 3600 + parts[1] * 60
                    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                    else -> null
                }.takeIf { it == null || it >= 0 }
            }
            val match = Regex("""^(?:(\d+)\s*h)?\s*(?:(\d+)\s*m(?:in)?)?$""").matchEntire(t)
            if (match != null && (match.groupValues[1].isNotEmpty() || match.groupValues[2].isNotEmpty())) {
                val h = match.groupValues[1].toLongOrNull() ?: 0
                val m = match.groupValues[2].toLongOrNull() ?: 0
                return h * 3600 + m * 60
            }
            return parseNumber(t)?.takeIf { it >= 0 }?.let { (it * 3600).roundToLong() }
        }

        private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", ITALIAN)
        private val DATE_SHORT = DateTimeFormatter.ofPattern("d MMM", ITALIAN)
        private val DATE_FULL = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", ITALIAN)

        fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DATE)
        fun fullDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DATE_FULL)

        /** "14 lug 2024" oppure "14 – 18 lug 2024" per uscite di più giorni. */
        fun dateRange(startEpochDay: Long, endEpochDay: Long): String {
            if (endEpochDay <= startEpochDay) return date(startEpochDay)
            val s = LocalDate.ofEpochDay(startEpochDay)
            val e = LocalDate.ofEpochDay(endEpochDay)
            return when {
                s.year == e.year && s.month == e.month -> "${s.dayOfMonth} – ${e.format(DATE)}"
                s.year == e.year -> "${s.format(DATE_SHORT)} – ${e.format(DATE)}"
                else -> "${s.format(DATE)} – ${e.format(DATE)}"
            }
        }

        fun euro(cents: Long): String =
            NumberFormat.getCurrencyInstance(ITALIAN).apply { currency = java.util.Currency.getInstance("EUR") }
                .format(cents / 100.0)

        /** Interpreta un importo in euro ("12,50", "12.5", "€ 12") e restituisce i centesimi. */
        fun parseEuroCents(text: String): Long? =
            parseNumber(text.replace("€", ""))?.takeIf { it >= 0 }?.let { (it * 100).roundToLong() }
    }
}
