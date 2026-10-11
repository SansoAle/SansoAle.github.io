package it.sansoale.uscite.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.sansoale.uscite.data.db.TripSummary
import it.sansoale.uscite.data.repo.TripRepository
import it.sansoale.uscite.model.TripType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.text.Normalizer
import java.time.LocalDate
import javax.inject.Inject

data class HomeFilters(
    val query: String = "",
    val types: Set<TripType> = emptySet(),
    val year: Int? = null,
    val tag: String? = null,
)

data class HomeUiState(
    val loading: Boolean = true,
    val trips: List<TripSummary> = emptyList(),
    val totalCount: Int = 0,
    val years: List<Int> = emptyList(),
    val tags: List<String> = emptyList(),
    val filters: HomeFilters = HomeFilters(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(repository: TripRepository) : ViewModel() {

    private val filters = MutableStateFlow(HomeFilters())

    val state: StateFlow<HomeUiState> = combine(repository.summaries, filters) { all, f ->
        HomeUiState(
            loading = false,
            trips = filter(all, f),
            totalCount = all.size,
            years = all.flatMap { yearsOf(it) }.distinct().sortedDescending(),
            tags = all.flatMap { s -> s.tags.map { it.name } }.distinct().sortedBy { it.lowercase() },
            filters = f,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun toggleType(type: TripType) = filters.update { f ->
        f.copy(types = if (type in f.types) f.types - type else f.types + type)
    }
    fun setYear(year: Int?) = filters.update { it.copy(year = year) }
    fun setTag(tag: String?) = filters.update { it.copy(tag = tag) }
    fun clearFilters() = filters.update { HomeFilters(query = it.query) }

    companion object {
        fun yearsOf(s: TripSummary): List<Int> {
            val start = LocalDate.ofEpochDay(s.trip.startDate).year
            val end = LocalDate.ofEpochDay(maxOf(s.trip.endDate, s.trip.startDate)).year
            return (start..end).toList()
        }

        fun filter(all: List<TripSummary>, f: HomeFilters): List<TripSummary> {
            val terms = normalize(f.query).split(' ').filter { it.isNotBlank() }
            return all.filter { s ->
                (f.types.isEmpty() || s.trip.type in f.types) &&
                    (f.year == null || f.year in yearsOf(s)) &&
                    (f.tag == null || s.tags.any { it.name.equals(f.tag, ignoreCase = true) }) &&
                    (terms.isEmpty() || searchText(s).let { text -> terms.all { it in text } })
            }
        }

        private fun searchText(s: TripSummary): String = normalize(
            listOf(
                s.trip.title,
                s.trip.location,
                s.trip.region,
                s.trip.notes,
                s.summit.orEmpty(),
                s.trip.type.label,
                s.tags.joinToString(" ") { it.name },
                s.companions.joinToString(" ") { it.name },
            ).joinToString(" "),
        )

        /** Minuscole e senza accenti, così "Cortina d'Ampezzo" si trova anche scrivendo "ampezzo". */
        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    }
}
