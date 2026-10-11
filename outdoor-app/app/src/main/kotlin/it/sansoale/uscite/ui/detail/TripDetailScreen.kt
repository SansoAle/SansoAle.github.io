package it.sansoale.uscite.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import it.sansoale.uscite.data.db.TripWithDetails
import it.sansoale.uscite.data.settings.AppSettings
import it.sansoale.uscite.model.CostCategory
import it.sansoale.uscite.model.TripType
import it.sansoale.uscite.track.BoundingBox
import it.sansoale.uscite.track.LatLon
import it.sansoale.uscite.ui.chart.ElevationChart
import it.sansoale.uscite.ui.common.RatingBar
import it.sansoale.uscite.ui.common.SectionTitle
import it.sansoale.uscite.ui.common.StatTile
import it.sansoale.uscite.ui.common.UnitFormatter
import it.sansoale.uscite.ui.common.color
import it.sansoale.uscite.ui.common.icon
import it.sansoale.uscite.ui.map.MapMarker
import it.sansoale.uscite.ui.map.TripMap
import java.io.File
import java.time.LocalDate

@Composable
fun TripDetailScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenPhoto: (tripId: Long, index: Int) -> Unit,
    viewModel: TripDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val selected by viewModel.selectedProfileIndex.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val units = remember(settings.unitSystem) { UnitFormatter(settings.unitSystem) }
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var mapTouched by remember { mutableStateOf(false) }

    val data = (state as? DetailState.Loaded)?.data

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) viewModel.exportGpx(uri)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(data?.trip?.title ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                },
                actions = {
                    if (data != null) {
                        IconButton(onClick = { onEdit(data.trip.id) }) { Icon(Icons.Filled.Edit, "Modifica") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Altro") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (data.tracks.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Esporta GPX") },
                                        leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                                        onClick = {
                                            menuOpen = false
                                            exportLauncher.launch(fileNameFor(data))
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Elimina") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            DetailState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            DetailState.NotFound -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Uscita non trovata")
            }
            is DetailState.Loaded -> {
                val d = (state as DetailState.Loaded).data
                val photos = d.sortedPhotos
                val markers = photos.mapNotNull { p ->
                    if (p.lat != null && p.lon != null) MapMarker(p.id, LatLon(p.lat, p.lon)) else null
                }
                val bounds = tracks.bounds ?: markers.takeIf { it.isNotEmpty() }?.let { m ->
                    BoundingBox(m.minOf { it.position.lat }, m.maxOf { it.position.lat }, m.minOf { it.position.lon }, m.maxOf { it.position.lon })
                }
                val highlight = selected?.let { tracks.profile.getOrNull(it) }?.let { LatLon(it.lat, it.lon) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    userScrollEnabled = !mapTouched,
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    item { Header(d) }
                    if (d.tracks.isNotEmpty() || markers.isNotEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .pointerInput(Unit) {
                                        // Mentre si tocca la mappa la lista non scorre, così pan e zoom funzionano.
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                mapTouched = event.changes.any { it.pressed }
                                            }
                                        }
                                    },
                            ) {
                                TripMap(
                                    lines = tracks.lines,
                                    markers = markers,
                                    highlight = highlight,
                                    fitBounds = bounds,
                                    mapStyle = settings.mapStyle,
                                    themeMode = settings.themeMode,
                                    onMarkerClick = { id ->
                                        val index = photos.indexOfFirst { it.id == id }
                                        if (index >= 0) onOpenPhoto(d.trip.id, index)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(300.dp),
                                )
                            }
                        }
                    }
                    if (tracks.profile.size >= 2) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                SectionTitle("Profilo altimetrico")
                                ElevationChart(
                                    profile = tracks.profile,
                                    units = units,
                                    selectedIndex = selected,
                                    onSelect = viewModel::selectProfileIndex,
                                )
                                Text(
                                    "Tocca o trascina sul grafico per vedere il punto sulla mappa",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item { StatsSection(d, units) }
                    item { TypeSection(d, units) }
                    if (photos.isNotEmpty()) {
                        item {
                            Column {
                                SectionTitle("Foto (${photos.size})", Modifier.padding(horizontal = 16.dp))
                                PhotoStrip(photos.map { it.thumbName?.let { t -> "thumbs/$t" } ?: "photos/${it.fileName}" }) { index ->
                                    onOpenPhoto(d.trip.id, index)
                                }
                            }
                        }
                    }
                    if (d.trip.notes.isNotBlank()) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                SectionTitle("Note")
                                Text(d.trip.notes, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    if (d.tracks.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                SectionTitle("Tracce GPX")
                                d.sortedTracks.forEach { t ->
                                    Text(
                                        "• ${t.name} — ${units.distance(t.stats.distanceM)}, ${t.pointCount} punti",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && data != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare l'uscita?") },
            text = { Text("\"${data.trip.title}\" verrà eliminata insieme a tracce e foto. L'operazione non è reversibile.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}

private fun fileNameFor(d: TripWithDetails): String {
    val date = LocalDate.ofEpochDay(d.trip.startDate)
    val safe = d.trip.title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().replace(' ', '_').take(60).ifEmpty { "uscita" }
    return "${date}_$safe.gpx"
}

@Composable
private fun Header(d: TripWithDetails) {
    val trip = d.trip
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(
                onClick = {},
                label = { Text(trip.type.label) },
                leadingIcon = { Icon(trip.type.icon, null, tint = trip.type.color) },
            )
            Spacer(Modifier.size(8.dp))
            if (trip.rating > 0) RatingBar(trip.rating, size = 18.dp)
        }
        Text(trip.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            if (trip.endDate > trip.startDate) UnitFormatter.dateRange(trip.startDate, trip.endDate)
            else UnitFormatter.fullDate(trip.startDate).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val place = listOf(trip.location, trip.region).filter { it.isNotBlank() }.joinToString(" · ")
        if (place.isNotBlank()) Text("📍 $place", style = MaterialTheme.typography.bodyLarge)
        val weather = listOfNotNull(
            trip.weather?.let { "${it.emoji} ${it.label}" },
            trip.temperatureC?.let { "${UnitFormatter.number(it, 0)} °C" },
        )
        if (weather.isNotEmpty()) Text(weather.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
        if (d.companions.isNotEmpty()) {
            Text("👥 " + d.companions.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodyLarge)
        }
        if (d.tags.isNotEmpty()) {
            Text(
                d.tags.joinToString("  ") { "#${it.name}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StatsSection(d: TripWithDetails, units: UnitFormatter) {
    val s = d.trip.stats
    val tiles = listOfNotNull(
        s.distanceM?.let { Triple("Distanza", units.distance(it, 2), Icons.Filled.Straighten) },
        s.elevationGainM?.let { Triple("Dislivello +", units.elevation(it), Icons.Filled.NorthEast) },
        s.elevationLossM?.let { Triple("Dislivello −", units.elevation(it), Icons.Filled.SouthEast) },
        s.maxElevationM?.let { Triple("Quota max", units.elevation(it), Icons.Filled.Height) },
        s.minElevationM?.let { Triple("Quota min", units.elevation(it), Icons.Filled.Height) },
        s.totalDurationS?.let { Triple("Durata totale", units.duration(it), Icons.Filled.AccessTime) },
        s.movingDurationS?.let { Triple("In movimento", units.duration(it), Icons.Filled.Timer) },
        s.avgSpeedMps?.let { Triple("Velocità media", units.speed(it), Icons.Filled.Speed) },
        s.maxSpeedMps?.let { Triple("Velocità max", units.speed(it), Icons.Filled.Speed) },
    )
    if (tiles.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionTitle("Statistiche")
        FlowRow(
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tiles.forEach { (label, value, icon) ->
                StatTile(label, value, Modifier.weight(1f), icon)
            }
            if (tiles.size % 2 == 1) Spacer(Modifier.weight(1f))
        }
        if (d.trip.overriddenStats.isNotEmpty()) {
            Text(
                "Alcuni valori sono stati inseriti a mano.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TypeSection(d: TripWithDetails, units: UnitFormatter) {
    when (d.trip.type) {
        TripType.MOUNTAIN -> {
            val m = d.mountain
            val huts = d.sortedHuts
            if (m == null && huts.isEmpty()) return
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("Montagna")
                InfoCard {
                    m?.activity?.let { InfoRow("Tipo", it.label) }
                    m?.difficulty?.let { InfoRow("Difficoltà", "${it.code} — ${it.label}") }
                    if (!m?.summit.isNullOrBlank()) {
                        InfoRow("Vetta / meta", m!!.summit + (m.summitElevationM?.let { " (${units.elevation(it)})" } ?: ""))
                    }
                    if (huts.isNotEmpty()) {
                        InfoRow("Rifugi", huts.joinToString("\n") { h -> h.name + (h.elevationM?.let { " (${units.elevation(it)})" } ?: "") })
                    }
                }
            }
        }
        TripType.BIKE -> {
            val b = d.bike ?: return
            if (b.bikeType == null && b.surface == null) return
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("Bici")
                InfoCard {
                    b.bikeType?.let { InfoRow("Bici", it.label) }
                    b.surface?.let { InfoRow("Fondo", it.label) }
                }
            }
        }
        TripType.VAN -> {
            val stages = d.sortedStages
            val costs = d.costs
            val roadKm = d.van?.roadKm
            if (stages.isEmpty() && costs.isEmpty() && roadKm == null) return
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("Viaggio in van")
                InfoCard {
                    roadKm?.let { InfoRow("Km su strada", units.distance(it * 1000, 0)) }
                    if (costs.isNotEmpty()) {
                        InfoRow("Spesa totale", UnitFormatter.euro(costs.sumOf { it.amountCents }))
                        CostCategory.entries.forEach { cat ->
                            val total = costs.filter { it.category == cat }.sumOf { it.amountCents }
                            if (total > 0) InfoRow("  ${cat.label}", UnitFormatter.euro(total))
                        }
                        roadKm?.takeIf { it > 0 }?.let { km ->
                            val fuel = costs.filter { it.category == CostCategory.FUEL }.sumOf { it.amountCents }
                            if (fuel > 0) InfoRow("Carburante al km", UnitFormatter.euro((fuel / km).toLong()))
                        }
                    }
                }
                if (stages.isNotEmpty()) {
                    SectionTitle("Tappe")
                    InfoCard {
                        stages.forEachIndexed { i, st ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Text("${i + 1}. ${st.name}", style = MaterialTheme.typography.titleSmall)
                            val sub = listOfNotNull(
                                st.date?.let { UnitFormatter.date(it) },
                                st.overnightType?.let { "Notte: ${it.label}" },
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium)
                            if (st.notes.isNotBlank()) Text(st.notes, style = MaterialTheme.typography.bodySmall)
                            val stageCosts = costs.filter { it.stageId == st.id }
                            if (stageCosts.isNotEmpty()) {
                                Text(
                                    "Spese: " + stageCosts.joinToString(", ") { "${it.category.label} ${UnitFormatter.euro(it.amountCents)}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
    }
}

@Composable
private fun PhotoStrip(paths: List<String>, onClick: (Int) -> Unit) {
    val context = LocalContext.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(paths) { index, path ->
            AsyncImage(
                model = File(context.filesDir, path),
                contentDescription = "Foto ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onClick(index) },
            )
        }
    }
}
