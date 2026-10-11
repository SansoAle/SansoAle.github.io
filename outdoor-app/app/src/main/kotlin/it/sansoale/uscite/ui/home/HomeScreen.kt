package it.sansoale.uscite.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import it.sansoale.uscite.data.db.TripSummary
import it.sansoale.uscite.data.settings.UnitSystem
import it.sansoale.uscite.model.TripType
import it.sansoale.uscite.ui.common.EmptyState
import it.sansoale.uscite.ui.common.RatingBar
import it.sansoale.uscite.ui.common.UnitFormatter
import it.sansoale.uscite.ui.common.color
import it.sansoale.uscite.ui.common.icon
import java.io.File

@Composable
fun HomeScreen(
    units: UnitSystem,
    onTripClick: (Long) -> Unit,
    onNewTrip: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formatter = remember(units) { UnitFormatter(units) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Le mie uscite") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTrip,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nuova uscita") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Cerca per titolo, luogo, note, tag…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.filters.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Filled.Clear, "Cancella") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            FiltersRow(state, viewModel)

            when {
                state.loading -> Unit
                state.totalCount == 0 -> EmptyState(
                    title = "Nessuna uscita",
                    message = "Aggiungi la tua prima gita, viaggio in van o giro in bici.\n" +
                        "Puoi importare un GPX o condividerlo qui da Strava, Komoot o Garmin.",
                    icon = Icons.Filled.Hiking,
                )
                state.trips.isEmpty() -> EmptyState(
                    title = "Nessun risultato",
                    message = "Nessuna uscita corrisponde ai filtri selezionati.",
                    icon = Icons.Filled.SearchOff,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            if (state.trips.size == 1) "1 uscita" else "${state.trips.size} uscite",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.trips, key = { it.trip.id }) { trip ->
                        TripCard(trip, formatter, onClick = { onTripClick(trip.trip.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltersRow(state: HomeUiState, viewModel: HomeViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TripType.entries.forEach { type ->
            val selected = type in state.filters.types
            FilterChip(
                selected = selected,
                onClick = { viewModel.toggleType(type) },
                label = { Text(type.label) },
                leadingIcon = { Icon(type.icon, null, Modifier.size(18.dp), tint = type.color) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = type.color.copy(alpha = 0.2f)),
            )
        }
        MenuChip(
            label = state.filters.year?.toString() ?: "Anno",
            selected = state.filters.year != null,
            options = state.years.map { it.toString() },
            onSelect = { viewModel.setYear(it?.toIntOrNull()) },
            allLabel = "Tutti gli anni",
        )
        if (state.tags.isNotEmpty()) {
            MenuChip(
                label = state.filters.tag?.let { "#$it" } ?: "Tag",
                selected = state.filters.tag != null,
                options = state.tags,
                onSelect = { viewModel.setTag(it) },
                allLabel = "Tutti i tag",
            )
        }
    }
}

@Composable
private fun MenuChip(
    label: String,
    selected: Boolean,
    options: List<String>,
    onSelect: (String?) -> Unit,
    allLabel: String,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelect(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun TripCard(summary: TripSummary, units: UnitFormatter, onClick: () -> Unit) {
    val context = LocalContext.current
    val trip = summary.trip
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 7f)
                .background(
                    Brush.linearGradient(listOf(trip.type.color.copy(alpha = 0.85f), trip.type.color.copy(alpha = 0.45f))),
                ),
        ) {
            val cover = summary.coverThumb?.let { File(context.filesDir, "thumbs/$it") }
                ?: summary.coverFile?.let { File(context.filesDir, "photos/$it") }
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    trip.type.icon,
                    null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(64.dp).align(Alignment.Center),
                )
            }
            Row(
                Modifier
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(trip.type.color)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(trip.type.icon, null, Modifier.size(16.dp), tint = Color.White)
                Spacer(Modifier.size(4.dp))
                Text(trip.type.label, color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            if (summary.photoCount > 0) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Photo, null, Modifier.size(14.dp), tint = Color.White)
                    Spacer(Modifier.size(4.dp))
                    Text("${summary.photoCount}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                trip.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val place = listOf(trip.location, trip.region).filter { it.isNotBlank() }.joinToString(" · ")
            Text(
                listOf(UnitFormatter.dateRange(trip.startDate, trip.endDate), place).filter { it.isNotBlank() }.joinToString(" — "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val stats = trip.stats
                val distance = if (trip.type == TripType.VAN) summary.roadKm?.times(1000) ?: stats.distanceM else stats.distanceM
                val parts = listOfNotNull(
                    distance?.let { units.distance(it) },
                    stats.elevationGainM?.let { "↑ ${units.elevation(it)}" },
                    stats.mainDurationS?.let { "⏱ ${units.duration(it)}" },
                )
                Text(
                    parts.joinToString("   "),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (trip.rating > 0) RatingBar(trip.rating, size = 14.dp)
            }
            if (summary.tags.isNotEmpty()) {
                Text(
                    summary.tags.joinToString("  ") { "#${it.name}" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
