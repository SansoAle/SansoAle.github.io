package it.sansoale.uscite.ui.map

import it.sansoale.uscite.data.settings.MapStyle

/**
 * Stili MapLibre basati su tile raster gratuite (nessuna API key). Per la Fase 2 (mappe offline) le
 * stesse sorgenti potranno essere scaricate per regione con l'OfflineManager di MapLibre.
 */
object MapStyles {

    fun json(style: MapStyle, dark: Boolean): String {
        val (tiles, maxZoom, attribution) = when (style) {
            MapStyle.TOPO -> Triple(
                listOf(
                    "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
                    "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
                    "https://c.tile.opentopomap.org/{z}/{x}/{y}.png",
                ),
                17,
                "Mappa: © <a href=\\\"https://opentopomap.org\\\">OpenTopoMap</a> (CC-BY-SA) · Dati: © <a href=\\\"https://www.openstreetmap.org/copyright\\\">OpenStreetMap</a>, SRTM",
            )
            MapStyle.OSM -> Triple(
                listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
                19,
                "© <a href=\\\"https://www.openstreetmap.org/copyright\\\">OpenStreetMap</a>",
            )
        }
        val background = if (dark) "#2B2F2A" else "#E9E6DC"
        val tilesJson = tiles.joinToString(",") { "\"$it\"" }
        // In tema scuro le tile vengono leggermente scurite per non abbagliare.
        val rasterPaint = if (dark) """{"raster-brightness-max":0.75,"raster-saturation":-0.2}""" else "{}"
        return """
            {
              "version": 8,
              "name": "${style.name}",
              "sources": {
                "base": {
                  "type": "raster",
                  "tiles": [$tilesJson],
                  "tileSize": 256,
                  "maxzoom": $maxZoom,
                  "attribution": "$attribution"
                }
              },
              "layers": [
                {"id": "background", "type": "background", "paint": {"background-color": "$background"}},
                {"id": "base", "type": "raster", "source": "base", "paint": $rasterPaint}
              ]
            }
        """.trimIndent()
    }
}
