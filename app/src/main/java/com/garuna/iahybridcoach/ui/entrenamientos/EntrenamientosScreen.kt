package com.garuna.iahybridcoach.ui.entrenamientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Pantalla de Entrenamientos (stateful). Lee el StateFlow del ViewModel y
 * delega la parte visual en EntrenamientosScreenContent para mantener la
 * separacion stateful/stateless del resto del proyecto.
 */
@Composable
fun EntrenamientosScreen(
    modifier: Modifier = Modifier,
    viewModel: EntrenamientosViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        EntrenamientosScreenContent(
            uiState = uiState,
            modifier = Modifier.fillMaxSize()
        )
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
}

@Composable
private fun EntrenamientosScreenContent(
    uiState: EntrenamientosUiState,
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
            modifier = modifier
        )
    }
}

/* CLAUDE CODE:
 * Wrapper para los estados Loading / Empty / Error: centra su contenido en
 * la pantalla. Evita duplicar la misma Column tres veces.
 */
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
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = workouts, key = { it.id }) { workout ->
            WorkoutCard(workout = workout)
        }
    }
}

@Composable
private fun WorkoutCard(workout: Workout) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
        }
    }
}

/* CLAUDE CODE:
 * Formatea un Timestamp de Firestore como "vie 16 may 2026" en castellano.
 * minSdk 26 garantiza java.time disponible nativamente sin desugaring.
 */
private val fechaFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale("es", "ES"))

private fun formatFecha(timestamp: Timestamp): String {
    val localDate = timestamp.toDate()
        .toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return fechaFormatter.format(localDate)
}

// ---- Previews ----

@Preview(showBackground = true)
@Composable
private fun EntrenamientosScreenEmptyPreview() {
    IAHybridCoachTheme {
        EntrenamientosScreenContent(uiState = EntrenamientosUiState.Empty)
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
            )
        )
    }
}
