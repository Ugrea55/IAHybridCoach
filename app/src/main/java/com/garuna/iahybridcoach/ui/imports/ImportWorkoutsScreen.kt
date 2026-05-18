package com.garuna.iahybridcoach.ui.imports

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES_LOCALE = Locale("es", "ES")

/* CLAUDE CODE:
 * Pantalla para importar entrenos desde Health Connect.
 *
 * Maneja todos los casos de disponibilidad/permisos del SDK y la lista
 * con checkboxes para que el usuario elija que importar. Los entrenos ya
 * importados antes (mismo externalId) aparecen como "ya importado" y no se
 * pueden re-seleccionar.
 */
@Composable
fun ImportWorkoutsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportWorkoutsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val missingPermissions by viewModel.missingPermissions.collectAsState()

    // CLAUDE CODE: contract proporcionado por la SDK de Health Connect.
    // Devuelve el set de permisos concedidos tras el dialog.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onPermissionsResult(granted)
    }

    // Cuando el estado pasa a NeedsPermission, lanzamos el dialog una vez.
    LaunchedEffect(uiState) {
        if (uiState is ImportWorkoutsUiState.NeedsPermission) {
            permissionLauncher.launch(viewModel.permissions)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            ImportWorkoutsUiState.Loading -> Centered { CircularProgressIndicator() }
            ImportWorkoutsUiState.NotAvailable -> NotAvailableContent()
            ImportWorkoutsUiState.NeedsUpdate -> NeedsUpdateContent()
            ImportWorkoutsUiState.NeedsPermission -> NeedsPermissionContent(
                onRequest = { permissionLauncher.launch(viewModel.permissions) }
            )
            is ImportWorkoutsUiState.Ready -> ReadyContent(
                state = state,
                missingPermissions = missingPermissions,
                onGrantMore = { permissionLauncher.launch(viewModel.permissions) },
                onToggle = viewModel::toggleSelection,
                onSelectAll = viewModel::selectAll,
                onSelectNone = viewModel::selectNone,
                onImport = viewModel::importSelected
            )
            is ImportWorkoutsUiState.Done -> DoneContent(
                imported = state.imported,
                onBack = onBack
            )
            is ImportWorkoutsUiState.Error -> ErrorContent(
                message = state.message,
                onRetry = viewModel::refreshAvailability
            )
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
private fun NotAvailableContent() {
    val context = LocalContext.current
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Health Connect no esta disponible",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Necesitas Android 9 o superior con la app Health Connect instalada. " +
                    "En Android 14+ viene integrada.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { openHealthConnectInPlayStore(context) }) {
                Text("Instalar Health Connect")
            }
        }
    }
}

@Composable
private fun NeedsUpdateContent() {
    val context = LocalContext.current
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Health Connect necesita una actualizacion",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { openHealthConnectInPlayStore(context) }) {
                Text("Actualizar")
            }
        }
    }
}

@Composable
private fun MissingPermissionsBanner(
    missing: Set<String>,
    onGrantMore: () -> Unit
) {
    val labels = missing.map { permissionLabel(it) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Faltan ${missing.size} permisos opcionales",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = labels.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(0.dp))
            Button(onClick = onGrantMore) {
                Text("Conceder")
            }
        }
    }
}

/* CLAUDE CODE: nombre legible para los permisos que pedimos a Health Connect.
 * Los strings de permisos vienen como "android.permission.health.READ_*".
 */
private fun permissionLabel(permission: String): String {
    return when {
        permission.endsWith("READ_EXERCISE") -> "Ejercicio"
        permission.endsWith("READ_DISTANCE") -> "Distancia"
        permission.endsWith("READ_TOTAL_CALORIES_BURNED") -> "Calorias"
        permission.endsWith("READ_HEART_RATE") -> "Frecuencia cardiaca"
        permission.endsWith("READ_SPEED") -> "Velocidad"
        permission.endsWith("READ_EXERCISE_ROUTE") -> "Ruta GPS"
        else -> permission.substringAfterLast(".")
    }
}

@Composable
private fun NeedsPermissionContent(onRequest: () -> Unit) {
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Necesitamos acceso a tus entrenos",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Solo leeremos las sesiones de ejercicio (no peso, ni FC, ni nada mas). " +
                    "Lo aprobaras en el dialog de Health Connect.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequest) {
                Text("Dar permisos")
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: ImportWorkoutsUiState.Ready,
    missingPermissions: Set<String>,
    onGrantMore: () -> Unit,
    onToggle: (ImportCandidate) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onImport: () -> Unit
) {
    val nuevos = state.candidates.count { !it.alreadyImported }
    val seleccionados = state.candidates.count { it.selected && !it.alreadyImported }

    Column(modifier = Modifier.fillMaxSize()) {
        if (missingPermissions.isNotEmpty()) {
            MissingPermissionsBanner(
                missing = missingPermissions,
                onGrantMore = onGrantMore
            )
        }

        if (state.candidates.isEmpty()) {
            Centered {
                Text(
                    text = "No hay entrenos nuevos en los ultimos 30 dias.",
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
                text = "$nuevos nuevos · $seleccionados seleccionados",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onSelectNone) { Text("Ninguno") }
            TextButton(onClick = onSelectAll) { Text("Todos") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = state.candidates, key = { it.workout.externalId }) { candidate ->
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
                    Text("Importar $seleccionados entrenos")
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: ImportCandidate,
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
            Spacer(modifier = Modifier.height(0.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    Text(
                        text = candidate.workout.tipoEnum.label,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (candidate.alreadyImported) {
                        Spacer(modifier = Modifier.height(0.dp))
                        Text(
                            text = "  · ya importado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = formatFecha(candidate.workout.fecha),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (candidate.workout.descripcion.isNotBlank()) {
                    Text(
                        text = candidate.workout.descripcion,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun DoneContent(imported: Int, onBack: () -> Unit) {
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (imported > 0) "Importados $imported entrenos"
                else "No se importo nada",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Volver a Entrenos") }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) { Text("Reintentar") }
        }
    }
}

private fun openHealthConnectInPlayStore(context: android.content.Context) {
    val uri = "market://details?id=com.google.android.apps.healthdata"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private val fechaFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", ES_LOCALE)

private fun formatFecha(ts: Timestamp): String {
    val ldt = ts.toDate().toInstant().atZone(ZoneId.systemDefault())
    return fechaFormatter.format(ldt)
}

