package com.garuna.iahybridcoach.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garuna.iahybridcoach.data.profile.Genero
import com.garuna.iahybridcoach.data.profile.UserProfile
import com.garuna.iahybridcoach.ui.common.SaveStatus
import com.garuna.iahybridcoach.ui.common.TapToOpenField
import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/* CLAUDE CODE:
 * Pantalla de Perfil. Formulario editable con autosave. Mismo patron que
 * Salud: la UI manda eventos `onProfileChanged` al ViewModel y este se
 * encarga del debounce y del save.
 */
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    ProfileScreenContent(
        uiState = uiState,
        onProfileChanged = viewModel::onProfileChanged,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

@Composable
private fun ProfileScreenContent(
    uiState: ProfileUiState,
    onProfileChanged: (UserProfile) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        ProfileUiState.Loading -> Centered(modifier) { CircularProgressIndicator() }
        is ProfileUiState.Error -> Centered(modifier) {
            Text(
                text = uiState.message,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Reintentar") }
        }
        is ProfileUiState.Editing -> EditingProfileForm(
            state = uiState,
            onProfileChanged = onProfileChanged,
            modifier = modifier
        )
    }
}

@Composable
private fun Centered(
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
private fun EditingProfileForm(
    state: ProfileUiState.Editing,
    onProfileChanged: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = state.profile

    // CLAUDE CODE: textos como state local para permitir teclear sin
    // pelearse con los tipos numericos. La SOT (source of truth) sigue
    // siendo profile, recalculamos textos cuando profile cambia desde fuera.
    var alturaText by remember(profile.alturaCm) {
        mutableStateOf(if (profile.alturaCm > 0) profile.alturaCm.toString() else "")
    }
    var pesoText by remember(profile.pesoKg) {
        mutableStateOf(if (profile.pesoKg > 0) formatPesoEdit(profile.pesoKg) else "")
    }

    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Indicador de guardado arriba a la derecha
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            SaveStatusIndicator(status = state.saveStatus)
        }

        OutlinedTextField(
            value = profile.nombre,
            onValueChange = { onProfileChanged(profile.copy(nombre = it)) },
            label = { Text("Nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        TapToOpenField(
            value = formatFechaCompact(profile.fechaNacimiento),
            label = "Fecha de nacimiento",
            onClick = { showDatePicker = true }
        )

        Spacer(modifier = Modifier.height(12.dp))

        EnumDropdown(
            label = "Sexo",
            options = Genero.values().toList(),
            selected = profile.generoEnum,
            labelOf = { it.label },
            onSelected = { onProfileChanged(profile.copy(genero = it.name)) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = alturaText,
            onValueChange = { input ->
                if (input.all { it.isDigit() } && input.length <= 3) {
                    alturaText = input
                    val altura = input.toIntOrNull() ?: 0
                    onProfileChanged(profile.copy(alturaCm = altura))
                }
            },
            label = { Text("Altura (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = pesoText,
            onValueChange = { input ->
                val normalized = input.replace(',', '.')
                if (normalized.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) {
                    pesoText = input
                    val peso = normalized.toDoubleOrNull() ?: 0.0
                    onProfileChanged(profile.copy(pesoKg = peso))
                }
            },
            label = { Text("Peso (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = profile.objetivo,
            onValueChange = { onProfileChanged(profile.copy(objetivo = it)) },
            label = { Text("Objetivo principal") },
            placeholder = { Text("Ej: correr 10k en 50 min, ganar musculo, salud...") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showDatePicker) {
        val initialMillis = profile.fechaNacimiento.toDate().time
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selected ->
                        val millis = millisFromUtcToLocalDate(selected)
                        onProfileChanged(
                            profile.copy(fechaNacimiento = Timestamp(Date(millis)))
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun SaveStatusIndicator(status: SaveStatus) {
    when (status) {
        SaveStatus.Idle -> {
            // nada visible
        }
        SaveStatus.Saving -> Row(verticalAlignment = Alignment.CenterVertically) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
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
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private val fechaCompactFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "ES"))

private fun formatFechaCompact(timestamp: Timestamp): String {
    val localDate = timestamp.toDate()
        .toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return fechaCompactFormatter.format(localDate)
}

private fun formatPesoEdit(kg: Double): String {
    val asInt = kg.toInt()
    return if (kg == asInt.toDouble()) asInt.toString()
    else "%.1f".format(Locale.US, kg)
}

private fun millisFromUtcToLocalDate(utcMillis: Long): Long {
    val localDate = Instant.ofEpochMilli(utcMillis)
        .atZone(ZoneId.of("UTC"))
        .toLocalDate()
    return LocalDate.of(localDate.year, localDate.monthValue, localDate.dayOfMonth)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
