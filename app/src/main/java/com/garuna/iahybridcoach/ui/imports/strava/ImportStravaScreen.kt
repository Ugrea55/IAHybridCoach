package com.garuna.iahybridcoach.ui.imports.strava

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garuna.iahybridcoach.data.strava.StravaActivitySummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES_LOCALE = Locale("es", "ES")

/* CLAUDE CODE:
 * Pantalla "Importar de Strava". Espejo de ImportWorkoutsScreen pero
 * tirando de actividades de Strava a traves de Cloud Functions.
 *
 * Anyade un detalle visual: los candidatos que reemplazaran a un Workout
 * de Health Connect (porque coinciden en tiempo) llevan un texto pequenyo
 * "Sustituye al de Health Connect".
 */
@Composable
fun ImportStravaScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportStravaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            ImportStravaUiState.Loading -> Centered { CircularProgressIndicator() }
            is ImportStravaUiState.Error -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = viewModel::retry) { Text("Reintentar") }
                }
            }
            is ImportStravaUiState.Ready -> ReadyContent(
                state = state,
                onToggle = viewModel::toggleSelection,
                onSelectAll = viewModel::selectAll,
                onSelectNone = viewModel::selectNone,
                onImport = viewModel::importSelected
            )
            is ImportStravaUiState.Done -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Importados ${state.imported} entrenos",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    if (state.replacedHc > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sustituidos ${state.replacedHc} de Health Connect",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Volver a Entrenos") }
                }
            }
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
private fun ReadyContent(
    state: ImportStravaUiState.Ready,
    onToggle: (StravaImportCandidate) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onImport: () -> Unit
) {
    val nuevos = state.candidates.count { !it.alreadyImported }
    val seleccionados = state.candidates.count { it.selected && !it.alreadyImported }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.candidates.isEmpty()) {
            Centered {
                Text(
                    text = "No hay actividades en Strava en los ultimos 30 dias.",
                    textAlign = TextAlign.Center
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$nuevos nuevas · $seleccionados seleccionadas",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onSelectNone) { Text("Ninguna") }
            TextButton(onClick = onSelectAll) { Text("Todas") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = state.candidates, key = { it.activity.id }) { candidate ->
                CandidateCard(candidate = candidate, onToggle = { onToggle(candidate) })
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = onImport,
                enabled = seleccionados > 0 && !state.isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text("Importar $seleccionados actividades")
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: StravaImportCandidate,
    onToggle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = candidate.selected,
                onCheckedChange = { onToggle() },
                enabled = !candidate.alreadyImported
            )
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    Text(
                        text = candidate.activity.sportType,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (candidate.alreadyImported) {
                        Text(
                            text = "  · ya importado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (candidate.activity.name.isNotBlank()) {
                    Text(
                        text = candidate.activity.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = formatFecha(candidate.activity.startDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val resumen = buildResumen(candidate.activity)
                if (resumen.isNotBlank()) {
                    Text(
                        text = resumen,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (candidate.replacesHealthConnect && !candidate.alreadyImported) {
                    Text(
                        text = "Sustituira al equivalente de Health Connect",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

private fun buildResumen(a: StravaActivitySummary): String {
    val partes = mutableListOf<String>()
    if (a.movingTimeSec > 0) {
        val minutes = (a.movingTimeSec / 60).toInt()
        partes += "$minutes min"
    }
    if (a.distanceMeters > 0) {
        val km = a.distanceMeters / 1000.0
        partes += "%.2f km".format(ES_LOCALE, km)
    }
    if (a.caloriesKcal > 0) partes += "${a.caloriesKcal.toInt()} kcal"
    if (a.avgHeartRate > 0 || a.maxHeartRate > 0) {
        val media = if (a.avgHeartRate > 0) a.avgHeartRate.toString() else "—"
        val max = if (a.maxHeartRate > 0) a.maxHeartRate.toString() else "—"
        partes += "FC $media/$max"
    }
    return partes.joinToString(" · ")
}

private val fechaFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", ES_LOCALE)

private fun formatFecha(iso: String): String {
    return runCatching {
        val ldt = Instant.parse(iso).atZone(ZoneId.systemDefault())
        fechaFormatter.format(ldt)
    }.getOrDefault(iso)
}
