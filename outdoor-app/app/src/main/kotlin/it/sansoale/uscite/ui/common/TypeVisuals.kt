package it.sansoale.uscite.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AirportShuttle
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import it.sansoale.uscite.model.TripType

/** Colore distintivo del tipo di uscita (card, filtri, tracce sulla mappa). */
val TripType.color: Color
    get() = when (this) {
        TripType.MOUNTAIN -> Color(0xFF3F7D3A)
        TripType.VAN -> Color(0xFFE07B24)
        TripType.BIKE -> Color(0xFF2F6FB5)
    }

val TripType.icon: ImageVector
    get() = when (this) {
        TripType.MOUNTAIN -> Icons.Filled.Terrain
        TripType.VAN -> Icons.Filled.AirportShuttle
        TripType.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
    }

/** Colore in formato "#RRGGBB" per MapLibre. */
fun Color.toHex(): String = String.format("#%06X", 0xFFFFFF and toArgb())
