package com.garuna.iahybridcoach.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garuna.iahybridcoach.data.profile.Genero
import com.garuna.iahybridcoach.data.profile.UserProfile
import com.garuna.iahybridcoach.ui.common.TapToOpenField
import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/* CLAUDE CODE:
 * Pantalla de onboarding. Se muestra una sola vez: cuando el usuario se
 * loguea por primera vez y todavia no tiene documento /users/{uid}.
 *
 * Recibe el displayName de FirebaseAuth como sugerencia para el campo
 * nombre (el usuario puede editarlo). Al guardar con exito, MainActivity
 * detecta el nuevo perfil via observeProfile() y navega solo a MainScaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    sugerenciaNombre: String?,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var nombre by remember { mutableStateOf(sugerenciaNombre.orEmpty()) }
    var fechaNacMillis by remember { mutableStateOf<Long?>(null) }
    var genero by remember { mutableStateOf(Genero.HOMBRE) }
    var objetivo by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }

    val isValid = nombre.isNotBlank() && fechaNacMillis != null

    val isSaving = uiState is OnboardingUiState.Saving

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text(
            text = "Bienvenido",
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Cuentanos un poco sobre ti para personalizar tu entrenamiento.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        TapToOpenField(
            value = fechaNacMillis?.let { formatFechaCompact(it) } ?: "",
            label = "Fecha de nacimiento",
            onClick = { showDatePicker = true }
        )

        Spacer(modifier = Modifier.height(12.dp))

        EnumDropdown(
            label = "Genero",
            options = Genero.values().toList(),
            selected = genero,
            labelOf = { it.label },
            onSelected = { genero = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = objetivo,
            onValueChange = { objetivo = it },
            label = { Text("Objetivo principal") },
            placeholder = { Text("Ej: correr 10k en 50 min, ganar musculo, salud...") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState is OnboardingUiState.Error) {
            Text(
                text = (uiState as OnboardingUiState.Error).message,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = {
                val fechaMillis = fechaNacMillis ?: return@Button
                viewModel.saveProfile(
                    UserProfile(
                        nombre = nombre.trim(),
                        fechaNacimiento = Timestamp(Date(fechaMillis)),
                        genero = genero.name,
                        objetivo = objetivo.trim()
                    )
                )
            },
            enabled = isValid && !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp)
                )
            } else {
                Text("Guardar y continuar")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = fechaNacMillis ?: defaultBirthdayMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selected ->
                        fechaNacMillis = millisFromUtcToLocalDate(selected)
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

/* CLAUDE CODE:
 * Dropdown reutilizable para cualquier enum: se le pasa la lista de opciones
 * y una funcion para obtener su etiqueta.
 */
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

private fun formatFechaCompact(millis: Long): String {
    val localDate = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return fechaCompactFormatter.format(localDate)
}

/* CLAUDE CODE: arrancamos el DatePicker en una fecha razonable para nacimiento
 * (ahora menos 25 anyos), no en hoy. Asi el usuario tiene que scrollear menos. */
private fun defaultBirthdayMillis(): Long {
    return LocalDate.now()
        .minusYears(25)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
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
