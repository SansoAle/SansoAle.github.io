package it.sansoale.uscite.ui.edit

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import it.sansoale.uscite.data.db.PhotoEntity
import it.sansoale.uscite.data.settings.UnitSystem
import it.sansoale.uscite.model.BikeType
import it.sansoale.uscite.model.CostCategory
import it.sansoale.uscite.model.MountainActivity
import it.sansoale.uscite.model.MountainDifficulty
import it.sansoale.uscite.model.OvernightType
import it.sansoale.uscite.model.PhotoPositionSource
import it.sansoale.uscite.model.StatField
import it.sansoale.uscite.model.Surface
import it.sansoale.uscite.model.TripType
import it.sansoale.uscite.model.Weather
import it.sansoale.uscite.ui.common.ChipsInput
import it.sansoale.uscite.ui.common.DateField
import it.sansoale.uscite.ui.common.DropdownField
import it.sansoale.uscite.ui.common.RatingBar
import it.sansoale.uscite.ui.common.SectionTitle
import it.sansoale.uscite.ui.common.UnitFormatter
import it.sansoale.uscite.ui.common.icon
import java.io.File

@Composable
fun TripEditScreen(
    units: UnitSystem,
    onClose: () -> Unit,
    onSaved: (id: Long, isNew: Boolean) -> Unit,
    viewModel: TripEditViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val tagSuggestions by viewModel.tagSuggestions.collectAsStateWithLifecycle()
    val companionSuggestions by viewModel.companionSuggestions.collectAsStateWithLifecycle()
    val regionSuggestions by viewModel.regionSuggestions.collectAsStateWithLifecycle()
    val formatter = remember(units) { UnitFormatter(units) }
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }

    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importTrack(uri)
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        viewModel.addPhotos(uris)
    }
    val pickPhotos = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // Senza ACCESS_MEDIA_LOCATION Android rimuove le coordinate GPS dall'EXIF delle foto.
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { pickPhotos() }

    LaunchedEffect(state.savedId) {
        state.savedId?.let { onSaved(it, state.isNew) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    val close: () -> Unit = {
        if (state.dirty) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    BackHandler(onBack = close)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Nuova uscita" else "Modifica uscita") },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.Filled.Close, "Chiudi") } },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.busy && !state.loading) { Text("Salva") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.loading) return@Column
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.size(4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TripType.entries.forEachIndexed { i, type ->
                        SegmentedButton(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            shape = SegmentedButtonDefaults.itemShape(i, TripType.entries.size),
                            icon = { Icon(type.icon, null, Modifier.size(18.dp)) },
                        ) { Text(type.label) }
                    }
                }

                GeneralSection(state, viewModel, tagSuggestions, companionSuggestions, regionSuggestions)

                when (state.type) {
                    TripType.MOUNTAIN -> MountainSection(state, viewModel, formatter)
                    TripType.VAN -> VanSection(state, viewModel, formatter)
                    TripType.BIKE -> BikeSection(state, viewModel)
                }

                SectionTitle("Tracce GPX") {
                    TextButton(onClick = {
                        gpxLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*"))
                    }) {
                        Icon(Icons.Filled.FileUpload, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Importa")
                    }
                }
                if (state.tracks.isEmpty()) {
                    Text(
                        "Nessuna traccia: puoi importare un file GPX (le statistiche verranno calcolate in automatico) " +
                            "oppure inserire i dati a mano qui sotto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.tracks.forEach { track ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Route, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(12.dp)) {
                                Text(track.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${formatter.distance(track.stats.distanceM)} · ↑ ${formatter.elevation(track.stats.elevationGainM)} · ${track.pointCount} punti",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { viewModel.removeTrack(track) }) { Icon(Icons.Filled.Delete, "Rimuovi traccia") }
                        }
                    }
                }

                StatsSection(viewModel, state)

                SectionTitle("Foto") {
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            locationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                        } else {
                            pickPhotos()
                        }
                    }) {
                        Icon(Icons.Filled.AddAPhoto, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Aggiungi")
                    }
                }
                PhotosEditor(state, viewModel)
                Spacer(Modifier.size(32.dp))
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Annullare le modifiche?") },
            text = { Text("Le modifiche non salvate andranno perse.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text("Esci senza salvare") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Continua") } },
        )
    }
}

@Composable
private fun GeneralSection(
    state: EditState,
    vm: TripEditViewModel,
    tagSuggestions: List<String>,
    companionSuggestions: List<String>,
    regionSuggestions: List<String>,
) {
    SectionTitle("Informazioni")
    OutlinedTextField(
        value = state.title,
        onValueChange = { vm.update { copy(title = it) } },
        label = { Text("Titolo") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField("Inizio", state.startDate, { it?.let(vm::setStartDate) }, Modifier.weight(1f))
        DateField("Fine", state.endDate, { it?.let(vm::setEndDate) }, Modifier.weight(1f))
    }
    OutlinedTextField(
        value = state.location,
        onValueChange = { vm.update { copy(location = it) } },
        label = { Text("Luogo") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.region,
        onValueChange = { vm.update { copy(region = it) } },
        label = { Text("Regione / zona") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val regionHints = regionSuggestions.filter { it != state.region && (state.region.isBlank() || it.contains(state.region, true)) }.take(5)
    if (regionHints.isNotEmpty() && state.region.length < 30) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(regionHints) { _, r ->
                SuggestionChip(onClick = { vm.update { copy(region = r) } }, label = { Text(r) })
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DropdownField(
            label = "Meteo",
            value = state.weather,
            options = Weather.entries,
            optionLabel = { "${it.emoji} ${it.label}" },
            onValueChange = { vm.update { copy(weather = it) } },
            modifier = Modifier.weight(1.4f),
        )
        OutlinedTextField(
            value = state.temperature,
            onValueChange = { vm.update { copy(temperature = it) } },
            label = { Text("°C") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.weight(1f),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Voto", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
        RatingBar(state.rating, size = 28.dp, onRatingChange = { vm.update { copy(rating = it) } })
    }
    ChipsInput(
        label = "Compagni di viaggio",
        values = state.companions,
        suggestions = companionSuggestions,
        onValuesChange = { vm.update { copy(companions = it) } },
    )
    ChipsInput(
        label = "Tag",
        values = state.tags,
        suggestions = tagSuggestions,
        onValuesChange = { vm.update { copy(tags = it) } },
    )
    OutlinedTextField(
        value = state.notes,
        onValueChange = { vm.update { copy(notes = it) } },
        label = { Text("Note") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MountainSection(state: EditState, vm: TripEditViewModel, units: UnitFormatter) {
    SectionTitle("Montagna")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DropdownField(
            label = "Tipo",
            value = state.activity,
            options = MountainActivity.entries,
            optionLabel = { it.label },
            onValueChange = { vm.update { copy(activity = it) } },
            modifier = Modifier.weight(1f),
        )
        DropdownField(
            label = "Difficoltà CAI",
            value = state.difficulty,
            options = MountainDifficulty.entries,
            optionLabel = { "${it.code} – ${it.label}" },
            onValueChange = { vm.update { copy(difficulty = it) } },
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.summit,
            onValueChange = { vm.update { copy(summit = it) } },
            label = { Text("Vetta / meta raggiunta") },
            singleLine = true,
            modifier = Modifier.weight(2f),
        )
        OutlinedTextField(
            value = state.summitElevation,
            onValueChange = { vm.update { copy(summitElevation = it) } },
            label = { Text("Quota (${units.elevationUnit})") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
    Text("Rifugi", style = MaterialTheme.typography.titleSmall)
    state.huts.forEach { hut ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hut.name,
                onValueChange = { vm.updateHut(hut.copy(name = it)) },
                label = { Text("Rifugio") },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = hut.elevation,
                onValueChange = { vm.updateHut(hut.copy(elevation = it)) },
                label = { Text(units.elevationUnit) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.removeHut(hut) }) { Icon(Icons.Filled.Delete, "Rimuovi rifugio") }
        }
    }
    AddButton("Aggiungi rifugio", vm::addHut)
}

@Composable
private fun BikeSection(state: EditState, vm: TripEditViewModel) {
    SectionTitle("Bici")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DropdownField(
            label = "Bici",
            value = state.bikeType,
            options = BikeType.entries,
            optionLabel = { it.label },
            onValueChange = { vm.update { copy(bikeType = it) } },
            modifier = Modifier.weight(1f),
        )
        DropdownField(
            label = "Fondo",
            value = state.surface,
            options = Surface.entries,
            optionLabel = { it.label },
            onValueChange = { vm.update { copy(surface = it) } },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VanSection(state: EditState, vm: TripEditViewModel, units: UnitFormatter) {
    SectionTitle("Viaggio in van")
    OutlinedTextField(
        value = state.roadKm,
        onValueChange = { vm.update { copy(roadKm = it) } },
        label = { Text("Percorsi su strada (${units.distanceUnit})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )

    Text("Tappe e pernottamenti", style = MaterialTheme.typography.titleSmall)
    state.stages.forEachIndexed { index, stage ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tappa ${index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.removeStage(stage) }) { Icon(Icons.Filled.Delete, "Rimuovi tappa") }
                }
                OutlinedTextField(
                    value = stage.name,
                    onValueChange = { vm.updateStage(stage.copy(name = it)) },
                    label = { Text("Luogo di pernottamento") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("Notte del", stage.date, { vm.updateStage(stage.copy(date = it)) }, Modifier.weight(1f), clearable = true)
                    DropdownField(
                        label = "Sosta",
                        value = stage.overnight,
                        options = OvernightType.entries,
                        optionLabel = { it.label },
                        onValueChange = { vm.updateStage(stage.copy(overnight = it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = stage.notes,
                    onValueChange = { vm.updateStage(stage.copy(notes = it)) },
                    label = { Text("Note sulla tappa") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    AddButton("Aggiungi tappa", vm::addStage)

    Text("Costi", style = MaterialTheme.typography.titleSmall)
    state.costs.forEach { cost ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DropdownField(
                        label = "Voce",
                        value = cost.category,
                        options = CostCategory.entries,
                        optionLabel = { it.label },
                        onValueChange = { vm.updateCost(cost.copy(category = it ?: CostCategory.OTHER)) },
                        allowNone = false,
                        modifier = Modifier.weight(1.2f),
                    )
                    OutlinedTextField(
                        value = cost.amount,
                        onValueChange = { vm.updateCost(cost.copy(amount = it)) },
                        label = { Text("€") },
                        singleLine = true,
                        isError = cost.amount.isNotBlank() && UnitFormatter.parseEuroCents(cost.amount) == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.removeCost(cost) }) { Icon(Icons.Filled.Delete, "Rimuovi costo") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.stages.isNotEmpty()) {
                        DropdownField(
                            label = "Tappa",
                            value = state.stages.find { it.key == cost.stageKey },
                            options = state.stages,
                            optionLabel = { st -> st.name.ifBlank { "Tappa ${state.stages.indexOf(st) + 1}" } },
                            onValueChange = { vm.updateCost(cost.copy(stageKey = it?.key)) },
                            noneLabel = "Intero viaggio",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = cost.note,
                        onValueChange = { vm.updateCost(cost.copy(note = it)) },
                        label = { Text("Nota") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    val total = state.costs.sumOf { UnitFormatter.parseEuroCents(it.amount) ?: 0L }
    if (total > 0) Text("Totale: ${UnitFormatter.euro(total)}", style = MaterialTheme.typography.titleSmall)
    AddButton("Aggiungi costo", vm::addCost)
}

@Composable
private fun StatsSection(vm: TripEditViewModel, state: EditState) {
    SectionTitle("Statistiche")
    Text(
        if (state.tracks.isNotEmpty()) {
            "Calcolate dalla traccia. Puoi correggere qualsiasi valore: i campi modificati a mano restano tali " +
                "anche se aggiungi altre tracce (usa ↺ per tornare al valore calcolato)."
        } else {
            "Inserisci i dati a mano (la durata nel formato ore:minuti, es. 3:45)."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    StatField.entries.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { field ->
                val manual = field in state.overridden
                OutlinedTextField(
                    value = vm.displayedStat(field),
                    onValueChange = { vm.setStat(field, it) },
                    label = { Text("${field.label} (${vm.unitFor(field)})", maxLines = 1) },
                    singleLine = true,
                    isError = !vm.isStatValid(field),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field == StatField.TOTAL_DURATION || field == StatField.MOVING_DURATION) KeyboardType.Text else KeyboardType.Decimal,
                    ),
                    trailingIcon = if (manual && state.tracks.isNotEmpty()) {
                        { IconButton(onClick = { vm.resetStat(field) }) { Icon(Icons.Filled.Restore, "Ripristina valore calcolato") } }
                    } else null,
                    supportingText = if (manual && state.tracks.isNotEmpty()) {
                        { Text("Modificato a mano") }
                    } else null,
                    modifier = Modifier.weight(1f),
                )
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PhotosEditor(state: EditState, vm: TripEditViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<PhotoEntity?>(null) }
    if (state.photos.isEmpty()) {
        Text(
            "Le foto vengono copiate nella memoria dell'app. Se hanno le coordinate GPS compaiono sulla mappa, " +
                "altrimenti vengono posizionate confrontando l'orario di scatto con la traccia.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        itemsIndexed(state.photos, key = { _, p -> p.fileName }) { index, photo ->
            val isCover = state.coverIndex == index
            Box(
                Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { editing = photo },
            ) {
                AsyncImage(
                    model = File(context.filesDir, photo.thumbName?.let { "thumbs/$it" } ?: "photos/${photo.fileName}"),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(4.dp),
                ) {
                    if (isCover) Icon(Icons.Filled.Star, "Copertina", Modifier.size(16.dp), tint = Color(0xFFF2B01E))
                    if (photo.positionSource != PhotoPositionSource.NONE) {
                        Icon(Icons.Filled.LocationOn, "Posizionata", Modifier.size(16.dp), tint = Color.White)
                    }
                }
            }
        }
    }
    editing?.let { photo ->
        var caption by rememberSaveable(photo.fileName) { mutableStateOf(photo.caption) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Foto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Didascalia") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { vm.setCover(photo); editing = null }) {
                        Icon(Icons.Filled.Star, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Usa come copertina")
                    }
                    OutlinedButton(
                        onClick = { vm.removePhoto(photo); editing = null },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(6.dp))
                        Text("Rimuovi foto", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setCaption(photo, caption.trim())
                    editing = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Filled.AddCircleOutline, null, Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(text)
    }
}
