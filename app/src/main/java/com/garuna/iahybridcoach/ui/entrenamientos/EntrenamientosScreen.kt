package com.garuna.iahybridcoach.ui.entrenamientos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garuna.iahybridcoach.data.workouts.Workout
import com.garuna.iahybridcoach.data.workouts.WorkoutType
import com.garuna.iahybridcoach.ui.theme.IAHybridCoachTheme
import com.google.firebase.Timestamp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/* CLAUDE CODE:
 * Pantalla de Entrenamientos.
 *
 * Modo normal: lista + FAB de anyadir.
 * Modo seleccion (entra al hacer long-press sobre un entreno):
 *   - Cada tap toggle de seleccion.
 *   - FAB oculto.
 *   - Aparece barra inferior con acciones (editar / borrar / cancelar).
 *   - Si seleccionas 1: puedes editar o borrar.
 *   - Si seleccionas varios: solo borrar.
 *
 * Edicion: reutiliza AddWorkoutSheet con el parametro `existing`.
 * Borrado: confirmacion previa con AlertDialog.
 */
@Composable
fun EntrenamientosScreen(
    modifier: Modifier = Modifier,
    onWorkoutClick: (String) -> Unit = {},
    viewModel: EntrenamientosViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var editingWorkout by remember { mutableStateOf<Workout?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val selectionMode = selectedIds.isNotEmpty()

    // Lista actual visible (para localizar el workout de un id seleccionado)
    val currentWorkouts: List<Workout> = (uiState as? EntrenamientosUiState.Success)
        ?.workouts.orEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        EntrenamientosScreenContent(
            uiState = uiState,
            selectedIds = selectedIds,
            selectionMode = selectionMode,
            onItemTap = { workout ->
                if (selectionMode) {
                    selectedIds = if (workout.id in selectedIds) {
                        selectedIds - workout.id
                    } else {
                        selectedIds + workout.id
                    }
                } else {
                    // CLAUDE CODE: tap normal -> abrir pantalla de detalle.
                    onWorkoutClick(workout.id)
                }
            },
            onItemLongPress = { workout ->
                selectedIds = selectedIds + workout.id
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!selectionMode) {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Anyadir entrenamiento"
                )
            }
        } else {
            SelectionActionBar(
                count = selectedIds.size,
                onEdit = {
                    val id = selectedIds.first()
                    editingWorkout = currentWorkouts.firstOrNull { it.id == id }
                },
                onDelete = { showDeleteDialog = true },
                onCancel = { selectedIds = emptySet() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showAddSheet) {
        AddWorkoutSheet(
            onDismiss = { showAddSheet = false },
            onSave = { workout ->
                viewModel.addWorkout(workout)
                showAddSheet = false
            }
        )
    }

    val editing = editingWorkout
    if (editing != null) {
        AddWorkoutSheet(
            existing = editing,
            onDismiss = {
                editingWorkout = null
                selectedIds = emptySet()
            },
            onSave = { updated ->
                viewModel.updateWorkout(updated)
                editingWorkout = null
                selectedIds = emptySet()
            }
        )
    }

    if (showDeleteDialog) {
        val n = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(if (n == 1) "Borrar entrenamiento" else "Borrar $n entrenamientos") },
            text = {
                Text(
                    text = if (n == 1) "Esta accion no se puede deshacer."
                    else "Se borraran $n entrenamientos. Esta accion no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMany(selectedIds)
                    selectedIds = emptySet()
                    showDeleteDialog = false
                }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancelar seleccion"
                )
            }
            Text(
                text = "$count seleccionado${if (count == 1) "" else "s"}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            if (count == 1) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Editar"
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Borrar",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EntrenamientosScreenContent(
    uiState: EntrenamientosUiState,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onItemTap: (Workout) -> Unit,
    onItemLongPress: (Workout) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        EntrenamientosUiState.Loading -> CenteredMessage(
            modifier = modifier,
            content = { CircularProgressIndicator() }
        )
        EntrenamientosUiState.Empty -> CenteredMessage(modifier = modifier) {
            Text(
                text = "Aun no tienes entrenos.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cuando anyadas tu primer entrenamiento aparecera aqui.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        is EntrenamientosUiState.Error -> CenteredMessage(modifier = modifier) {
            Text(
                text = uiState.message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        is EntrenamientosUiState.Success -> WorkoutsList(
            workouts = uiState.workouts,
            selectedIds = selectedIds,
            selectionMode = selectionMode,
            onItemTap = onItemTap,
            onItemLongPress = onItemLongPress,
            modifier = modifier
        )
    }
}

@Composable
private fun CenteredMessage(
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

@Composable
private fun WorkoutsList(
    workouts: List<Workout>,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onItemTap: (Workout) -> Unit,
    onItemLongPress: (Workout) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // CLAUDE CODE: padding extra abajo para que el ultimo entreno no
        // quede tapado por el FAB ni por la barra de seleccion.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = workouts, key = { it.id }) { workout ->
            WorkoutCard(
                workout = workout,
                isSelected = workout.id in selectedIds,
                selectionMode = selectionMode,
                onTap = { onItemTap(workout) },
                onLongPress = { onItemLongPress(workout) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutCard(
    workout: Workout,
    isSelected: Boolean,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else Color.Unspecified

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = workout.tipoEnum.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatFecha(workout.fecha),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (workout.descripcion.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = workout.descripcion,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val resumen = buildResumen(workout)
            if (resumen.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = resumen,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun buildResumen(workout: Workout): String {
    val partes = mutableListOf<String>()
    if (workout.duracionMinutos > 0) partes += "${workout.duracionMinutos} min"
    if (workout.distanciaMetros > 0) partes += formatDistancia(workout.distanciaMetros)
    if (workout.caloriasKcal > 0) partes += "${workout.caloriasKcal.toInt()} kcal"
    if (workout.fcMedia > 0 || workout.fcMaxima > 0) {
        val media = if (workout.fcMedia > 0) workout.fcMedia.toString() else "—"
        val max = if (workout.fcMaxima > 0) workout.fcMaxima.toString() else "—"
        partes += "FC $media/$max"
    }
    return partes.joinToString(" · ")
}

private fun formatDistancia(metros: Double): String {
    return if (metros >= 1000) {
        val km = metros / 1000.0
        "%.2f km".format(Locale("es", "ES"), km)
    } else {
        "${metros.toInt()} m"
    }
}

private val fechaFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale("es", "ES"))

private fun formatFecha(timestamp: Timestamp): String {
    val localDate = timestamp.toDate()
        .toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return fechaFormatter.format(localDate)
}

@Preview(showBackground = true)
@Composable
private fun EntrenamientosScreenEmptyPreview() {
    IAHybridCoachTheme {
        EntrenamientosScreenContent(
            uiState = EntrenamientosUiState.Empty,
            selectedIds = emptySet(),
            selectionMode = false,
            onItemTap = {},
            onItemLongPress = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EntrenamientosScreenSuccessPreview() {
    IAHybridCoachTheme {
        EntrenamientosScreenContent(
            uiState = EntrenamientosUiState.Success(
                workouts = listOf(
                    Workout(
                        id = "1",
                        tipo = WorkoutType.FUERZA.name,
                        fecha = Timestamp.now(),
                        descripcion = "Press banca 4x8 a 80kg, dominadas 4x6."
                    ),
                    Workout(
                        id = "2",
                        tipo = WorkoutType.CARDIO.name,
                        fecha = Timestamp.now(),
                        descripcion = "Carrera continua suave 45 minutos."
                    )
                )
            ),
            selectedIds = setOf("1"),
            selectionMode = true,
            onItemTap = {},
            onItemLongPress = {}
        )
    }
}
