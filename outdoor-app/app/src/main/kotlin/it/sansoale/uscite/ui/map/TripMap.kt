package it.sansoale.uscite.ui.map

import android.graphics.RectF
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.sansoale.uscite.data.settings.MapStyle
import it.sansoale.uscite.data.settings.ThemeMode
import it.sansoale.uscite.track.BoundingBox
import it.sansoale.uscite.track.LatLon
import it.sansoale.uscite.ui.common.toHex
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Una linea da disegnare (traccia). [id] viene restituito al tocco. */
data class MapLine(val id: Long, val points: List<LatLon>, val colorHex: String)

/** Un marker circolare (foto, tappe…). */
data class MapMarker(val id: Long, val position: LatLon)

private const val SRC_LINES = "lines"
private const val SRC_MARKERS = "markers"
private const val SRC_HIGHLIGHT = "highlight"
private const val LAYER_CASING = "lines-casing"
private const val LAYER_LINES = "lines-layer"
private const val LAYER_MARKERS = "markers-layer"
private const val LAYER_HIGHLIGHT = "highlight-layer"

/**
 * Mappa MapLibre con tracce, marker e punto evidenziato.
 *
 * @param fitBounds area da inquadrare; la camera viene adattata ogni volta che cambia.
 */
@Composable
fun TripMap(
    lines: List<MapLine>,
    modifier: Modifier = Modifier,
    markers: List<MapMarker> = emptyList(),
    highlight: LatLon? = null,
    fitBounds: BoundingBox? = null,
    mapStyle: MapStyle = MapStyle.TOPO,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onLineClick: ((Long) -> Unit)? = null,
    onMarkerClick: ((Long) -> Unit)? = null,
    onMapClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val highlightColor = MaterialTheme.colorScheme.primary.toHex()
    val markerColor = "#D81B60"

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val currentOnLineClick by rememberUpdatedState(onLineClick)
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Caricamento (o cambio) dello stile.
    LaunchedEffect(mapView, mapStyle, dark) {
        style = null
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isCompassEnabled = true
            m.setStyle(Style.Builder().fromJson(MapStyles.json(mapStyle, dark))) { s ->
                s.addSource(GeoJsonSource(SRC_LINES))
                s.addSource(GeoJsonSource(SRC_MARKERS))
                s.addSource(GeoJsonSource(SRC_HIGHLIGHT))
                s.addLayer(
                    LineLayer(LAYER_CASING, SRC_LINES).withProperties(
                        PropertyFactory.lineColor(if (dark) "#000000" else "#FFFFFF"),
                        PropertyFactory.lineWidth(7f),
                        PropertyFactory.lineOpacity(0.7f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                s.addLayer(
                    LineLayer(LAYER_LINES, SRC_LINES).withProperties(
                        PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                        PropertyFactory.lineWidth(4f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                s.addLayer(
                    CircleLayer(LAYER_MARKERS, SRC_MARKERS).withProperties(
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleColor("#FFFFFF"),
                        PropertyFactory.circleStrokeColor(markerColor),
                        PropertyFactory.circleStrokeWidth(3f),
                    ),
                )
                s.addLayer(
                    CircleLayer(LAYER_HIGHLIGHT, SRC_HIGHLIGHT).withProperties(
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleColor(highlightColor),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(3f),
                    ),
                )
                style = s
            }
        }
    }

    // Click su marker e linee.
    DisposableEffect(map) {
        val m = map ?: return@DisposableEffect onDispose { }
        val listener = MapLibreMap.OnMapClickListener { latLng ->
            val point = m.projection.toScreenLocation(latLng)
            val touch = with(density) { 16.dp.toPx() }
            val rect = RectF(point.x - touch, point.y - touch, point.x + touch, point.y + touch)
            val marker = m.queryRenderedFeatures(rect, LAYER_MARKERS).firstOrNull()
            val markerId = marker?.getNumberProperty("id")?.toLong()
            if (markerId != null && currentOnMarkerClick != null) {
                currentOnMarkerClick?.invoke(markerId)
                return@OnMapClickListener true
            }
            val line = m.queryRenderedFeatures(rect, LAYER_LINES).firstOrNull()
            val lineId = line?.getNumberProperty("id")?.toLong()
            if (lineId != null && currentOnLineClick != null) {
                currentOnLineClick?.invoke(lineId)
                return@OnMapClickListener true
            }
            currentOnMapClick?.invoke()
            false
        }
        m.addOnMapClickListener(listener)
        onDispose { m.removeOnMapClickListener(listener) }
    }

    LaunchedEffect(style, lines) {
        val s = style ?: return@LaunchedEffect
        val features = lines.filter { it.points.size >= 2 }.map { line ->
            Feature.fromGeometry(LineString.fromLngLats(line.points.map { Point.fromLngLat(it.lon, it.lat) })).apply {
                addStringProperty("color", line.colorHex)
                addNumberProperty("id", line.id)
            }
        }
        s.getSourceAs<GeoJsonSource>(SRC_LINES)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    LaunchedEffect(style, markers) {
        val s = style ?: return@LaunchedEffect
        val features = markers.map { marker ->
            Feature.fromGeometry(Point.fromLngLat(marker.position.lon, marker.position.lat)).apply {
                addNumberProperty("id", marker.id)
            }
        }
        s.getSourceAs<GeoJsonSource>(SRC_MARKERS)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    LaunchedEffect(style, highlight) {
        val s = style ?: return@LaunchedEffect
        val features = listOfNotNull(highlight?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })
        s.getSourceAs<GeoJsonSource>(SRC_HIGHLIGHT)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    LaunchedEffect(style, fitBounds) {
        val m = map ?: return@LaunchedEffect
        if (style == null) return@LaunchedEffect
        val box = fitBounds ?: return@LaunchedEffect
        mapView.post {
            val padding = with(density) { 40.dp.roundToPx() }
            val degenerate = box.maxLat - box.minLat < 1e-4 && box.maxLon - box.minLon < 1e-4
            val update = if (degenerate) {
                CameraUpdateFactory.newLatLngZoom(LatLng((box.minLat + box.maxLat) / 2, (box.minLon + box.maxLon) / 2), 13.0)
            } else {
                CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(box.maxLat, box.maxLon, box.minLat, box.minLon), padding)
            }
            runCatching { m.moveCamera(update) }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
