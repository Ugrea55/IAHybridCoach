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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
 * Bottom sheet con el formulario para crear un entrenamiento.
 *
 * Es stateless: la pantalla padre decide cuando mostrarlo y que hacer con
 * el Workout resultante (onSave) o al cancelar (onDismiss).
 *
 * Estado interno: cada campo del formulario es un remember { mutableStateOf }.
 * No usamos otro ViewModel para 3 campos efimeros; al cerrar la sheet se
 * descarta todo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkoutSheet(
    onDismiss: () -> Unit,
    onSave: (Workout) -> Unit,
    modifier: Modifier = Modifier,
    // CLAUDE CODE: fecha pre-rellenada. Por defecto = hoy. Desde el
    // calendario se pasa la fecha del dia seleccionado para que el formulario
    // arranque con esa fecha (mejor UX al planificar).
    initialFechaMillis: Long = System.currentTimeMillis()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // CLAUDE CODE: estado del formulario.
    var tipo by remember { mutableStateOf(WorkoutType.FUERZA) }
    var fechaMillis by remember { mutableStateOf(initialFechaMillis) }
    var descripcion by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }

    // CLAUDE CODE: pedimos descripcion no vacia para evitar entrenos
    // anonimos. Tipo y fecha siempre tienen valor por defecto.
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
                text = "Nuevo entrenamiento",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            TipoDropdown(
                selected = tipo,
                onSelected = { tipo = it }
            )

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
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

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
                        onSave(
                            Workout(
                                tipo = tipo.name,
                                fecha = Timestamp(Date(fechaMillis)),
                                descripcion = descripcion.trim()
                            )
                        )
                    },
                    enabled = isValid
                ) {
                    Text("Guardar")
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
                        // CLAUDE CODE: el DatePicker devuelve millis a medianoche UTC.
                        // Lo convertimos a medianoche LOCAL para que la fecha
                        // guardada coincida con la que vio en pantalla.
                        fechaMillis = millisFromUtcToLocalDate(selected)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
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

/* CLAUDE CODE:
 * DatePicker de Material 3 devuelve los millis correspondientes a la fecha
 * seleccionada a las 00:00 UTC. Si el usuario en Europa selecciona "16 may",
 * recibe el millis de "16 may 00:00 UTC", que en hora local podria ser
 * "15 may 22:00" o "16 may 02:00". Para evitar desfases, extraemos la
 * LocalDate (Y/M/D) y la convertimos a inicio de dia en la zona del sistema.
 */
private fun millisFromUtcToLocalDate(utcMillis: Long): Long {
    val localDate = Instant.ofEpochMilli(utcMillis)
        .atZone(ZoneId.of("UTC"))
        .toLocalDate()
    return LocalDate.of(localDate.year, localDate.monthValue, localDate.dayOfMonth)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
