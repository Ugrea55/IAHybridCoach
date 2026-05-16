package com.garuna.iahybridcoach.ui.salud

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garuna.iahybridcoach.data.health.FlujoMenstrual
import com.garuna.iahybridcoach.data.health.HealthEntry
import com.garuna.iahybridcoach.data.health.SintomaMenstrual
import com.garuna.iahybridcoach.ui.common.SaveStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/* CLAUDE CODE:
 * Pantalla de Salud. Es un FORMULARIO FIJO ligado al dia de hoy: muestra
 * los datos ya introducidos (si los hay) y permite editarlos. Cada cambio
 * se autoguarda en Firestore con debounce.
 *
 * Sin lista, sin FAB, sin bottom sheet. Todo inline.
 */
@Composable
fun SaludScreen(
    modifier: Modifier = Modifier,
    viewModel: SaludViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    SaludScreenContent(
        uiState = uiState,
        onEntryChanged = viewModel::onEntryChanged,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

@Composable
private fun SaludScreenContent(
    uiState: SaludUiState,
    onEntryChanged: (HealthEntry) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        SaludUiState.Loading -> CenteredBox(modifier) { CircularProgressIndicator() }
        is SaludUiState.Error -> CenteredBox(modifier) {
            Text(
                text = uiState.message,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Reintentar") }
        }
        is SaludUiState.Editing -> EditingForm(
            state = uiState,
            onEntryChanged = onEntryChanged,
            modifier = modifier
        )
    }
}

@Composable
private fun CenteredBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditingForm(
    state: SaludUiState.Editing,
    onEntryChanged: (HealthEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = state.entry

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Cabecera con fecha + estado guardado
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatFechaCabecera(state.date),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            SaveStatusIndicator(status = state.saveStatus)
        }

        Spacer(modifier = Modifier.height(16.dp))

        ScaleSlider(
            label = "Calidad del sueno",
            value = entry.calidadSueno.toFloat(),
            onValueChange = { onEntryChanged(entry.copy(calidadSueno = it.toInt())) },
            max = 5
        )

        Spacer(modifier = Modifier.height(12.dp))

        ScaleSlider(
            label = "Motivacion",
            value = entry.motivacion.toFloat(),
            onValueChange = { onEntryChanged(entry.copy(motivacion = it.toInt())) },
            max = 5
        )

        Spacer(modifier = Modifier.height(12.dp))

        ScaleSlider(
            label = "Energia",
            value = entry.energia.toFloat(),
            onValueChange = { onEntryChanged(entry.copy(energia = it.toInt())) },
            max = 5
        )

        Spacer(modifier = Modifier.height(12.dp))

        ScaleSlider(
            label = "Nivel de dolor",
            value = entry.nivelDolor.toFloat(),
            onValueChange = { onEntryChanged(entry.copy(nivelDolor = it.toInt())) },
            max = 10
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = entry.lugarDolor,
            onValueChange = { onEntryChanged(entry.copy(lugarDolor = it)) },
            label = { Text("Lugar del dolor") },
            placeholder = { Text("Ej: rodilla derecha, lumbar...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.incluyeCicloMenstrual) {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Ciclo menstrual",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Hoy tengo la regla", modifier = Modifier.weight(1f))
                Switch(
                    checked = entry.menstruacion,
                    onCheckedChange = { checked ->
                        onEntryChanged(
                            entry.copy(
                                menstruacion = checked,
                                // si se apaga, limpiamos el flujo
                                flujoMenstrual = if (checked) entry.flujoMenstrual else ""
                            )
                        )
                    }
                )
            }

            if (entry.menstruacion) {
                Spacer(modifier = Modifier.height(12.dp))
                FlujoDropdown(
                    selected = entry.flujoEnum,
                    onSelected = { onEntryChanged(entry.copy(flujoMenstrual = it.name)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Sintomas (selecciona los que apliquen)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            val seleccionados = entry.sintomasEnum
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SintomaMenstrual.values().forEach { sintoma ->
                    FilterChip(
                        selected = sintoma in seleccionados,
                        onClick = {
                            val nuevos = if (sintoma in seleccionados) {
                                seleccionados - sintoma
                            } else {
                                seleccionados + sintoma
                            }
                            onEntryChanged(
                                entry.copy(sintomasMenstruales = nuevos.map { it.name })
                            )
                        },
                        label = { Text(sintoma.label) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SaveStatusIndicator(status: SaveStatus) {
    when (status) {
        SaveStatus.Idle -> {
            // CLAUDE CODE: nada visible cuando no hay actividad reciente.
        }
        SaveStatus.Saving -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "Guardando...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SaveStatus.Saved -> Text(
            text = "Guardado",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        is SaveStatus.SaveError -> Text(
            text = "Error al guardar",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ScaleSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    max: Int
) {
    val intValue = value.toInt()
    val displayValue = if (intValue == 0) "—" else "$intValue/$max"
    Text(
        text = "$label: $displayValue",
        style = MaterialTheme.typography.bodyMedium
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = 0f..max.toFloat(),
        steps = max - 1
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlujoDropdown(
    selected: FlujoMenstrual?,
    onSelected: (FlujoMenstrual) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Flujo") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            FlujoMenstrual.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private val fechaCabeceraFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES"))

private fun formatFechaCabecera(date: LocalDate): String {
    return fechaCabeceraFormatter.format(date)
        .replaceFirstChar { it.uppercase(Locale("es", "ES")) }
}

