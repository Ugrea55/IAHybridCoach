package com.garuna.iahybridcoach.ui.detail

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garuna.iahybridcoach.data.workouts.Workout
import com.garuna.iahybridcoach.data.workouts.detail.LapInfo
import com.garuna.iahybridcoach.data.workouts.detail.SegmentInfo
import com.garuna.iahybridcoach.data.workouts.detail.WorkoutDetail
import com.google.firebase.Timestamp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES_LOCALE = Locale("es", "ES")

/* CLAUDE CODE:
 * Pantalla de detalle de un entrenamiento.
 *
 * Sub-paso 10.2: muestra el resumen y las listas de laps/segments. Los
 * graficos de FC/ritmo y el mapa GPS llegan en 10.3-10.5.
 *
 * El workoutId se pasa como parametro al Composable. El ViewModel se crea
 * con `key=workoutId` para que cada entreno tenga su propio VM (evita
 * fugas de datos entre pantallas).
 */
@Composable
fun WorkoutDetailScreen(
    workoutId: String,
    modifier: Modifier = Modifier
) {
    val viewModel: WorkoutDetailViewModel = viewModel(key = workoutId)
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(workoutId) {
        viewModel.load(workoutId)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            WorkoutDetailUiState.Loading -> Centered { CircularProgressIndicator() }
            is WorkoutDetailUiState.Error -> Centered {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            is WorkoutDetailUiState.Ready -> ReadyContent(state = state)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun ReadyContent(state: WorkoutDetailUiState.Ready) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SummaryCard(workout = state.workout) }

        when {
            state.loadingFromHc -> item {
                LoadingFromHcCard()
            }
            state.hcNoData -> item {
                NoDetailCard(
                    message = "Health Connect no devolvio datos detallados para este entreno."
                )
            }
            state.detail == null && state.workout.source != "HEALTH_CONNECT" -> item {
                NoDetailCard(
                    message = "Entreno manual: no hay laps, FC ni ruta GPS."
                )
            }
            state.detail != null -> {
                val d = state.detail
                if (d.laps.isNotEmpty()) {
                    item { SectionTitle("Vueltas (laps)") }
                    itemsIndexed(d.laps) { index, lap ->
                        LapRow(index = index + 1, lap = lap)
                    }
                }
                if (d.segments.isNotEmpty()) {
                    item { SectionTitle("Segmentos") }
                    items(d.segments) { seg ->
                        SegmentRow(segment = seg)
                    }
                }

                // Placeholder de futuras secciones (graficos FC, ritmo, mapa).
                item { FuturePlaceholdersCard(detail = d) }
            }
        }
    }
}

@Composable
private fun SummaryCard(workout: Workout) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = workout.tipoEnum.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatFechaLarga(workout.fecha),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (workout.descripcion.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = workout.descripcion,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            StatsGrid(workout = workout)
        }
    }
}

@Composable
private fun StatsGrid(workout: Workout) {
    val stats = buildList {
        if (workout.duracionMinutos > 0) {
            add("Duracion" to "${workout.duracionMinutos} min")
        }
        if (workout.distanciaMetros > 0) {
            add("Distancia" to formatDistancia(workout.distanciaMetros))
        }
        if (workout.caloriasKcal > 0) {
            add("Calorias" to "${workout.caloriasKcal.toInt()} kcal")
        }
        if (workout.fcMedia > 0) add("FC media" to "${workout.fcMedia} bpm")
        if (workout.fcMaxima > 0) add("FC max" to "${workout.fcMaxima} bpm")
    }
    if (stats.isEmpty()) {
        Text(
            text = "Sin datos de resumen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    // Pintamos en filas de 2 columnas
    stats.chunked(2).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth()) {
            row.forEach { (label, value) ->
                StatCell(
                    label = label,
                    value = value,
                    modifier = Modifier.weight(1f)
                )
            }
            if (row.size == 1) Box(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(ES_LOCALE),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun LapRow(index: Int, lap: LapInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lap $index",
                modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDuracion(lap.durationSeconds),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (lap.distanceMeters > 0) {
                    Text(
                        text = formatDistancia(lap.distanceMeters),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentRow(segment: SegmentInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = segmentTypeLabel(segment.segmentTypeCode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(formatDuracion(segment.durationSeconds))
                    if (segment.repetitions > 0) append(" · ${segment.repetitions} reps")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingFromHcCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp)
            )
            Spacer(modifier = Modifier.height(0.dp))
            Text(
                text = "  Cargando datos desde Health Connect...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun NoDetailCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FuturePlaceholdersCard(detail: WorkoutDetail) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Datos disponibles",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "FC: ${detail.hrSamples.size} muestras\n" +
                    "Velocidad: ${detail.speedSamples.size} muestras\n" +
                    "Ruta GPS: ${detail.routePoints.size} puntos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (detail.hrSamples.isNotEmpty() || detail.speedSamples.isNotEmpty() || detail.routePoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Los graficos y el mapa llegan en los siguientes sub-pasos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun segmentTypeLabel(code: Int): String {
    // CLAUDE CODE: estos codigos son ExerciseSegment.SEGMENT_TYPE_*. Mapeamos
    // los mas comunes; el resto se muestra como "Segmento".
    return when (code) {
        17 -> "Calentamiento"
        16 -> "Vuelta a la calma"
        66 -> "Descanso"
        64 -> "Pausa"
        else -> "Segmento"
    }
}

private fun formatDuracion(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "—"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatDistancia(metros: Double): String {
    return if (metros >= 1000) {
        val km = metros / 1000.0
        "%.2f km".format(ES_LOCALE, km)
    } else {
        "${metros.toInt()} m"
    }
}

private val fechaLargaFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", ES_LOCALE)

private fun formatFechaLarga(ts: Timestamp): String {
    val ldt = ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    return fechaLargaFormatter.format(ldt)
        .replaceFirstChar { it.uppercase(ES_LOCALE) }
}
