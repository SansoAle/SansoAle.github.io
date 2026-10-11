package it.sansoale.uscite.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import dagger.hilt.android.lifecycle.HiltViewModel
import it.sansoale.uscite.data.repo.TripRepository
import it.sansoale.uscite.data.settings.UnitSystem
import it.sansoale.uscite.domain.ArchiveStats
import it.sansoale.uscite.domain.ArchiveStatsResult
import it.sansoale.uscite.domain.PersonalRecord
import it.sansoale.uscite.domain.RecordKind
import it.sansoale.uscite.domain.StatsTrip
import it.sansoale.uscite.model.TripType
import it.sansoale.uscite.ui.common.EmptyState
import it.sansoale.uscite.ui.common.SectionTitle
import it.sansoale.uscite.ui.common.StatTile
import it.sansoale.uscite.ui.common.UnitFormatter
import it.sansoale.uscite.ui.common.color
import it.sansoale.uscite.ui.common.icon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(repository: TripRepository) : ViewModel() {
    val trips: StateFlow<List<StatsTrip>?> = repository.summaries
        .map { list -> list.map { StatsTrip.from(it.trip, it.roadKm) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private enum class ChartMetric(val label: String) { DISTANCE("Distanza"), GAIN("Dislivello"), COUNT("Uscite") }

private val MONTHS = listOf("G", "F", "M", "A", "M", "G", "L", "A", "S", "O", "N", "D")

@Composable
fun StatsScreen(
    units: UnitSystem,
    onTripClick: (Long) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val formatter = remember(units) { UnitFormatter(units) }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var types by rememberSaveable { mutableStateOf(emptySet<TripType>()) }
    var metric by rememberSaveable { mutableStateOf(ChartMetric.DISTANCE) }

    Scaffold(topBar = { TopAppBar(title = { Text("Statistiche") }) }) { padding ->
        val all = trips ?: return@Scaffold
        if (all.isEmpty()) {
            EmptyState(
                title = "Ancora nessun dato",
                message = "Le statistiche compariranno dopo aver aggiunto le prime uscite.",
                icon = Icons.Filled.QueryStats,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        val years = remember(all) { all.map { it.year }.distinct().sortedDescending() }
        val result = remember(all, year, types) { ArchiveStats.compute(all, year, types) }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = year == null, onClick = { year = null }, label = { Text("Tutti gli anni") })
                years.forEach { y -> FilterChip(selected = year == y, onClick = { year = y }, label = { Text("$y") }) }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TripType.entries.forEach { t ->
                    FilterChip(
                        selected = t in types,
                        onClick = { types = if (t in types) types - t else types + t },
                        label = { Text(t.label) },
                        leadingIcon = { Icon(t.icon, null, Modifier.size(18.dp), tint = t.color) },
                    )
                }
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(if (year == null) "Totali" else "Totali $year")
                TotalsGrid(result, formatter)

                SectionTitle("Per tipo")
                TripType.entries.filter { types.isEmpty() || it in types }.forEach { t ->
                    val tot = result.byType.getValue(t)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(t.icon, null, tint = t.color)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${tot.count} uscite · ${formatter.distance(tot.distanceM, 0)} · ↑ ${formatter.elevation(tot.gainM)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                SectionTitle(if (year == null) "Andamento per anno" else "Andamento mensile")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ChartMetric.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = metric == m,
                            onClick = { metric = m },
                            shape = SegmentedButtonDefaults.itemShape(i, ChartMetric.entries.size),
                        ) { Text(m.label) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val (labels, values) = remember(result, metric, year, formatter) { chartData(result, metric, year, formatter) }
                ColumnChart(labels, values)
                Text(
                    when (metric) {
                        ChartMetric.DISTANCE -> "Valori in ${formatter.distanceUnit}"
                        ChartMetric.GAIN -> "Valori in ${formatter.elevationUnit}"
                        ChartMetric.COUNT -> "Numero di uscite"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (result.records.isNotEmpty()) {
                    SectionTitle("Record personali")
                    result.records.forEach { r -> RecordRow(r, formatter) { onTripClick(r.trip.id) } }
                }
            }
        }
    }
}

private fun chartData(
    result: ArchiveStatsResult,
    metric: ChartMetric,
    year: Int?,
    units: UnitFormatter,
): Pair<List<String>, List<Double>> {
    fun value(count: Int, distanceM: Double, gainM: Double) = when (metric) {
        ChartMetric.DISTANCE -> units.distanceValue(distanceM)
        ChartMetric.GAIN -> units.elevationValue(gainM)
        ChartMetric.COUNT -> count.toDouble()
    }
    return if (year == null) {
        result.byYear.keys.map { it.toString() } to result.byYear.values.map { value(it.count, it.distanceM, it.gainM) }
    } else {
        MONTHS to result.months.map { value(it.count, it.distanceM, it.gainM) }
    }
}

@Composable
private fun TotalsGrid(result: ArchiveStatsResult, units: UnitFormatter) {
    val t = result.totals
    FlowRow(
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile("Uscite", "${t.count}", Modifier.weight(1f))
        StatTile("Giorni", "${t.days}", Modifier.weight(1f))
        StatTile("Distanza", units.distance(t.distanceM, 0), Modifier.weight(1f))
        StatTile("Dislivello +", units.elevation(t.gainM), Modifier.weight(1f))
        StatTile("Tempo", units.duration(t.durationS), Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ColumnChart(labels: List<String>, values: List<Double>) {
    if (values.isEmpty() || values.all { it <= 0.0 }) {
        Text(
            "Nessun dato per il periodo selezionato",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        return
    }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        producer.runTransaction { columnSeries { series(values) } }
    }
    val color = MaterialTheme.colorScheme.primary
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(fill = fill(color), thickness = 14.dp),
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = CartesianValueFormatter { _, x, _ -> labels.getOrNull(x.toInt()) ?: "" },
                ),
            ),
            modelProducer = producer,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
    }
}

@Composable
private fun RecordRow(record: PersonalRecord, units: UnitFormatter, onClick: () -> Unit) {
    val value = when (record.kind) {
        RecordKind.DISTANCE -> units.distance(record.value)
        RecordKind.ELEVATION -> units.elevation(record.value)
        RecordKind.DURATION -> units.duration(record.value.toLong())
        RecordKind.SPEED -> units.speed(record.value)
        RecordKind.DAYS -> "${record.value.toLong()} giorni"
    }
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(record.trip.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(UnitFormatter.date(record.trip.startDate), style = MaterialTheme.typography.bodySmall)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
