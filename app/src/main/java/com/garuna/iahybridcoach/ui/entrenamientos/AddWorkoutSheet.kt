package com.garuna.iahybridcoach.ui.entrenamientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garuna.iahybridcoach.data.workouts.Workout
import com.garuna.iahybridcoach.data.workouts.WorkoutType
import com.garuna.iahybridcoach.ui.common.TapToOpenField
import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/* CLAUDE CODE:
 * Bottom sheet con formulario para CREAR o EDITAR un entrenamiento.
 *
 * Modo CREAR: `existing` es null. Boton "Guardar" crea uno nuevo.
 * Modo EDITAR: `existing` es el Workout actual. Campos pre-rellenados.
 *   Boton "Guardar cambios" hace UPDATE conservando id/source/externalId.
 *
 * Campos avanzados (duracion, distancia, calorias, FC) son siempre editables,
 * incluidos para entrenos importados de Health Connect (el usuario quiere
 * libertad total para corregir).
 *
 * Para mantener la firma del callback simple, expongo SOLO onSave(Workout)
 * y el sheet construye el objeto completo. En modo edicion, preservamos
 * id, source y externalId del existing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkoutSheet(
    onDismiss: () -> Unit,
    onSave: (Workout) -> Unit,
    modifier: Modifier = Modifier,
    initialFechaMillis: Long = System.currentTimeMillis(),
    existing: Workout? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEdit = existing != null

    // CLAUDE CODE: estado del formulario, sembrado desde `existing` o por defecto.
    var tipo by remember {
        mutableStateOf(existing?.tipoEnum ?: WorkoutType.FUERZA)
    }
    var fechaMillis by remember {
        mutableStateOf(existing?.fecha?.toDate()?.time ?: initialFechaMillis)
    }
    var descripcion by remember {
        mutableStateOf(existing?.descripcion.orEmpty())
    }
    var duracionText by remember {
        mutableStateOf(
            if ((existing?.duracionMinutos ?: 0) > 0) existing!!.duracionMinutos.toString() else ""
        )
    }
    var distanciaText by remember {
        mutableStateOf(
            if ((existing?.distanciaMetros ?: 0.0) > 0) {
                formatKmEdit((existing!!.distanciaMetros) / 1000.0)
            } else ""
        )
    }
    var caloriasText by remember {
        mutableStateOf(
            if ((existing?.caloriasKcal ?: 0.0) > 0) existing!!.caloriasKcal.toInt().toString() else ""
        )
    }
    var fcMediaText by remember {
        mutableStateOf(
            if ((existing?.fcMedia ?: 0) > 0) existing!!.fcMedia.toString() else ""
        )
    }
    var fcMaxText by remember {
        mutableStateOf(
            if ((existing?.fcMaxima ?: 0) > 0) existing!!.fcMaxima.toString() else ""
        )
    }

    var showDatePicker by remember { mutableStateOf(false) }

    val isValid = descripcion.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = if (isEdit) "Editar entrenamiento" else "Nuevo entrenamiento",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            TipoDropdown(selected = tipo, onSelected = { tipo = it })

            Spacer(modifier = Modifier.height(12.dp))

            TapToOpenField(
                value = formatFechaCompact(fechaMillis),
                label = "Fecha",
                onClick = { showDatePicker = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripcion") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Datos detallados (opcional)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = duracionText,
                onValueChange = { input ->
                    if (input.all { it.isDigit() } && input.length <= 4) {
                        duracionText = input
                    }
                },
                label = { Text("Duracion (min)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = distanciaText,
                onValueChange = { input ->
                    val normalized = input.replace(',', '.')
                    if (normalized.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) {
                        distanciaText = input
                    }
                },
                label = { Text("Distancia (km)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = caloriasText,
                onValueChange = { input ->
                    if (input.all { it.isDigit() } && input.length <= 5) {
                        caloriasText = input
                    }
                },
                label = { Text("Calorias (kcal)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = fcMediaText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 3) {
                            fcMediaText = input
                        }
                    },
                    label = { Text("FC media") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = fcMaxText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 3) {
                            fcMaxText = input
                        }
                    },
                    label = { Text("FC max") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val distanciaKm = distanciaText.replace(',', '.').toDoubleOrNull() ?: 0.0
                        val workout = Workout(
                            // CLAUDE CODE: en edicion preservamos id/source/externalId.
                            id = existing?.id.orEmpty(),
                            tipo = tipo.name,
                            fecha = Timestamp(Date(fechaMillis)),
                            descripcion = descripcion.trim(),
                            source = existing?.source ?: "MANUAL",
                            externalId = existing?.externalId.orEmpty(),
                            duracionMinutos = duracionText.toIntOrNull() ?: 0,
                            distanciaMetros = (distanciaKm * 1000.0),
                            caloriasKcal = caloriasText.toDoubleOrNull() ?: 0.0,
                            fcMedia = fcMediaText.toIntOrNull() ?: 0,
                            fcMaxima = fcMaxText.toIntOrNull() ?: 0
                        )
                        onSave(workout)
                    },
                    enabled = isValid
                ) {
                    Text(if (isEdit) "Guardar cambios" else "Guardar")
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = fechaMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selected ->
                        fechaMillis = millisFromUtcToLocalDate(selected)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipoDropdown(
    selected: WorkoutType,
    onSelected: (WorkoutType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo") },
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
            WorkoutType.values().forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

private val fechaCompactFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "ES"))

private fun formatFechaCompact(millis: Long): String {
    val localDate = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return fechaCompactFormatter.format(localDate)
}

private fun formatKmEdit(km: Double): String {
    val asInt = km.toInt()
    return if (km == asInt.toDouble()) asInt.toString()
    else "%.2f".format(Locale.US, km)
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
