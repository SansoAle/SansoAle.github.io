package it.sansoale.uscite.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun RatingBar(
    rating: Int,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    onRatingChange: ((Int) -> Unit)? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val filled = i <= rating
            val icon = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder
            val tint = if (filled) Color(0xFFF2B01E) else MaterialTheme.colorScheme.outline
            if (onRatingChange != null) {
                IconButton(onClick = { onRatingChange(if (rating == i) 0 else i) }) {
                    Icon(icon, contentDescription = "$i stelle", tint = tint, modifier = Modifier.size(size))
                }
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(4.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Campo a scelta singola con menu a tendina (valore nullo = "nessuno"). */
@Composable
fun <T> DropdownField(
    label: String,
    value: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    onValueChange: (T?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = true,
    noneLabel: String = "—",
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value?.let(optionLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowNone) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onValueChange(null); expanded = false })
            }
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onValueChange(option); expanded = false })
            }
        }
    }
}

/** Campo data con selettore a calendario. */
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = date?.let { UnitFormatter.date(it.toEpochDay()) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date?.toEpochDay()?.times(DAY_MS))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS))) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (clearable) TextButton(onClick = { onDateChange(null); open = false }) { Text("Nessuna") }
                    TextButton(onClick = { open = false }) { Text("Annulla") }
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private const val DAY_MS = 86_400_000L

/**
 * Inserimento di più valori come chip (tag, compagni di viaggio) con suggerimenti dai valori già usati.
 */
@Composable
fun ChipsInput(
    label: String,
    values: List<String>,
    suggestions: List<String>,
    onValuesChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    fun add(value: String) {
        val v = value.trim()
        if (v.isNotEmpty() && values.none { it.equals(v, ignoreCase = true) }) onValuesChange(values + v)
        text = ""
    }
    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                if (new.endsWith(",")) add(new.dropLast(1)) else text = new
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(text) }),
            trailingIcon = {
                if (text.isNotBlank()) IconButton(onClick = { add(text) }) { Icon(Icons.Filled.Add, "Aggiungi") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        val filtered = suggestions
            .filter { s -> values.none { it.equals(s, ignoreCase = true) } }
            .filter { text.isBlank() || it.contains(text.trim(), ignoreCase = true) }
            .take(8)
        if (values.isNotEmpty() || filtered.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                values.forEach { value ->
                    InputChip(
                        selected = true,
                        onClick = { onValuesChange(values - value) },
                        label = { Text(value) },
                        trailingIcon = { Icon(Icons.Filled.Close, "Rimuovi", Modifier.size(InputChipDefaults.IconSize)) },
                    )
                }
                filtered.forEach { s ->
                    SuggestionChip(onClick = { add(s) }, label = { Text(s) })
                }
            }
        }
    }
}
